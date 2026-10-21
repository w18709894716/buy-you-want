/**
 * HTTP 请求封装 - 基于 $fetch 的 Axios 风格包装器
 * - 自动附加 Authorization Bearer Token
 * - 401 自动跳转登录
 * - 响应解包（从 R<T> 中提取 data）
 */

interface R<T = any> {
  code: number
  message: string
  data: T
}

interface RequestOptions extends Parameters<typeof $fetch>[1] {
  raw?: boolean // 是否返回原始响应（不解包）
}

const TOKEN_KEY = 'byw_token'

/** 无需登录即可浏览的公开页面（与 middleware/auth.ts 共用） */
const PUBLIC_PATHS = ['/', '/login', '/register', '/search', '/seckill', '/coupons']

/** 当前路径是否为公开页面 */
export function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.includes(path) || path.startsWith('/product/') || path.startsWith('/shop/')
}

/**
 * 获取 token。
 *
 * 只从 localStorage 读取。此前还额外写入了一个**非 HttpOnly 的 byw_token Cookie**，
 * 但后端并不读取它（Sa-Token 配置 is-read-cookie=false），
 * 等于让凭据随每个请求自动外发，XSS / 中间代理 / 访问日志都能拿到，因此已移除。
 */
export function getToken(): string | null {
  if (import.meta.server) return null
  return localStorage.getItem(TOKEN_KEY)
}

/** 设置 token */
export function setToken(token: string) {
  if (import.meta.server) return
  localStorage.setItem(TOKEN_KEY, token)
  clearLegacyCookie()
}

/** 清除 token */
export function clearToken() {
  if (import.meta.server) return
  localStorage.removeItem(TOKEN_KEY)
  clearLegacyCookie()
}

/** 清理历史版本遗留的 byw_token Cookie（后端不读，仅停止继续外发） */
function clearLegacyCookie() {
  if (typeof document === 'undefined') return
  document.cookie = 'byw_token=; path=/; max-age=0'
}

// ==================== Token 自动续期 ====================
// 背景（真机验证结论）：Sa-Token 的 timeout 是 86400 秒，而本项目的调用路径不会触发
// Sa-Token 的自动续期（网关只调 getLoginIdByToken，服务侧只信任网关注入的身份头），
// 所以 token 是"登录后固定 24 小时过期"，与用户是否活跃无关 —— 用户正在操作时也会被登出。
// 这里在剩余时间不足阈值时主动调 /auth/refresh 换新 token，避免这种"突然登出"。

/** 剩余不足该秒数就续期 */
const REFRESH_THRESHOLD_SECONDS = 2 * 60 * 60
/** 两次探测的最小间隔，避免每个请求都打一次 */
const TTL_CHECK_INTERVAL_MS = 10 * 60 * 1000

let lastTtlCheckAt = 0
let refreshing: Promise<string | null> | null = null

/** 取当前 token 剩余秒数；探测失败返回 null（不影响正常请求） */
async function probeTokenTtl(token: string): Promise<number | null> {
  try {
    const config = useRuntimeConfig()
    const resp = await $fetch<R<number>>('/auth/token-ttl', {
      baseURL: config.public.apiBase as string,
      headers: { Authorization: `Bearer ${token}` },
    })
    return typeof resp?.data === 'number' ? resp.data : null
  } catch {
    return null
  }
}

/** 单飞刷新：并发请求共享同一次刷新，避免把同一个旧 token 反复注销 */
async function refreshTokenOnce(token: string): Promise<string | null> {
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const config = useRuntimeConfig()
        const resp = await $fetch<R<{ token: string }>>('/auth/refresh', {
          baseURL: config.public.apiBase as string,
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        })
        const fresh = resp?.data?.token
        if (fresh) {
          setToken(fresh)
          return fresh
        }
        return null
      } catch {
        return null
      } finally {
        refreshing = null
      }
    })()
  }
  return refreshing
}

/**
 * 请求前确保 token 可用：临近过期（默认 2 小时）就续期一次。
 * - 无 token / SSR：原样返回
 * - token 已失效（TTL=-2）：清理本地 token，交给后续 401 流程跳登录
 * - 探测失败：不阻塞请求
 */
export async function ensureFreshToken(): Promise<string | null> {
  const token = getToken()
  if (!token || import.meta.server) return token
  const now = Date.now()
  if (now - lastTtlCheckAt < TTL_CHECK_INTERVAL_MS) return token
  lastTtlCheckAt = now
  const ttl = await probeTokenTtl(token)
  if (ttl === null || ttl === -1 || ttl > REFRESH_THRESHOLD_SECONDS) return token
  if (ttl <= 0) {
    clearToken()
    return null
  }
  return (await refreshTokenOnce(token)) ?? token
}

/** 核心请求函数 */
export async function request<T = any>(
  url: string,
  options: RequestOptions = {}
): Promise<T> {
  const { raw = false, ...fetchOptions } = options
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase as string

  // 临近过期先续期，避免用户在操作过程中被登出
  const token = await ensureFreshToken()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(fetchOptions.headers as Record<string, string> || {}),
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  try {
    const response = await $fetch<R<T>>(url, {
      baseURL,
      ...fetchOptions,
      headers,
    })

    // 原始响应模式
    if (raw) {
      return response as unknown as T
    }

    // 解包 R<T>
    if (response.code === 200 || response.code === 0) {
      return response.data
    }

    // 业务错误
    throw new Error(response.message || '请求失败')
  } catch (error: any) {
    // 401 处理：清除失效 token；仅受保护页面跳登录，公开页面（如首页/商品页）原地弹出登录框不强跳
    if (error?.response?.status === 401 || error?.statusCode === 401) {
      clearToken()
      if (import.meta.client) {
        if (!isPublicPath(window.location.pathname)) {
          navigateTo('/login')
        } else {
          // 弹出全局登录框；抛出 silent 错误供页面 catch 后跳过 Toast 提示
          useLoginModal().openLoginModal()
          const authError: any = new Error('请先登录')
          authError.silent = true
          throw authError
        }
      }
    }
    // 尝试从响应体中提取业务错误信息
    const data = error?.data || error?.response?._data
    if (data && data.message) {
      throw new Error(data.message)
    }
    throw error
  }
}

/** GET 请求 */
export function get<T = any>(url: string, params?: Record<string, any>, options?: RequestOptions): Promise<T> {
  return request<T>(url, { method: 'GET', params, ...options })
}

/** POST 请求 */
export function post<T = any>(url: string, body?: any, options?: RequestOptions): Promise<T> {
  return request<T>(url, { method: 'POST', body, ...options })
}

/** PUT 请求 */
export function put<T = any>(url: string, body?: any, options?: RequestOptions): Promise<T> {
  return request<T>(url, { method: 'PUT', body, ...options })
}

/** DELETE 请求 */
export function del<T = any>(url: string, options?: RequestOptions): Promise<T> {
  return request<T>(url, { method: 'DELETE', ...options })
}

/** 文件上传（multipart/form-data） */
export async function upload<T = any>(url: string, formData: FormData): Promise<T> {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase as string
  const token = await ensureFreshToken()
  const headers: Record<string, string> = {}
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  try {
    const response = await $fetch<R<T>>(url, {
      baseURL,
      method: 'POST',
      body: formData,
      headers,
    })
    if (response.code === 200 || response.code === 0) {
      return response.data
    }
    throw new Error(response.message || '上传失败')
  } catch (error: any) {
    const data = error?.data || error?.response?._data
    if (data && data.message) {
      throw new Error(data.message)
    }
    throw error
  }
}
