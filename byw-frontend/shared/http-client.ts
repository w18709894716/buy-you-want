/**
 * 三端共用的 HTTP 客户端工厂（admin / merchant 两个 SPA 共用；Nuxt 端栈不同，仍是独立实现）。
 *
 * <p><b>为什么是"零依赖 + 依赖注入"</b>：本文件位于 byw-frontend/shared/，不在任何 app 目录下。
 * 一旦在这里写 import axios / import { ElMessage } from 'element-plus'，模块解析会从
 * byw-frontend/shared/ 逐级向上找 node_modules，而依赖实际装在各自 app 的 node_modules 里
 * → 构建期直接 "Failed to resolve import"。所以把外部能力（axios、提示组件、路由跳转）
 * 全部做成参数，各 app 的 utils/request.ts 只做几行装配。
 *
 * <p>职责：
 * 1. 统一 baseURL / 超时 / JSON 头；
 * 2. 请求前按需续期 token（Sa-Token 不会自动续期，见 ensureFreshToken 注释）；
 * 3. 统一解包后端 R<T> 结构，401 统一跳登录，业务错误统一 toast。
 */

export interface ToastLike {
  error(message: string): void
}

export interface HttpClientOptions {
  /** axios 库本身（用来创建实例 + 发裸请求，避免拦截器递归） */
  axios: any
  /** 错误提示（ElementPlus 的 ElMessage 或任何带 error(msg) 的对象） */
  toast: ToastLike
  /** 401 时的跳转动作（各端 router.push('/login')） */
  onUnauthorized: () => void
  /**
   * localStorage 里的键名。两种模式语义不同：
   * - 头模式（默认）：存的是 token 本体；
   * - Cookie 模式：存的是"已登录"标记，token 在 HttpOnly Cookie 里，JS 读不到。
   */
  tokenKey: string
  /**
   * Cookie 模式（默认 false）。开启后：
   * - 请求带 withCredentials，浏览器自动携带 HttpOnly Cookie；
   * - 不再注入 Authorization 头；
   * - 续期（/auth/refresh）与 TTL 探测也靠 Cookie，服务端会把新 token 写回 Cookie。
   */
  cookieMode?: boolean
  /**
   * 端的标识（admin / merchant / web）。会以 X-Client-App 头发给后端，
   * 让网关与 auth 在"同一个浏览器里同时登录多端"时选对各自的 Cookie（三端 Cookie 名不同）。
   */
  clientApp?: string
  baseURL?: string
  timeoutMs?: number
  /** 剩余多少秒内触发续期，默认 2 小时 */
  refreshThresholdSeconds?: number
  /** /auth/token-ttl 探测的最小间隔，默认 10 分钟 */
  ttlCheckIntervalMs?: number
}

export function createHttpClient(options: HttpClientOptions) {
  const {
    axios,
    toast,
    onUnauthorized,
    tokenKey,
    cookieMode = false,
    clientApp,
    baseURL = '/api',
    timeoutMs = 15000,
    refreshThresholdSeconds = 2 * 60 * 60,
    ttlCheckIntervalMs = 10 * 60 * 1000
  } = options

  // 每端一个 Cookie 名，靠这个头让后端知道"这次请求属于哪一端"
  const appHeaders: Record<string, string> = clientApp ? { 'X-Client-App': clientApp } : {}

  const request = axios.create({
    baseURL,
    timeout: timeoutMs,
    // Cookie 模式必须带凭证，否则浏览器不会发送 HttpOnly Cookie
    withCredentials: cookieMode,
    headers: { 'Content-Type': 'application/json', ...appHeaders }
  })

  // ==================== Token 自动续期 ====================
  // 背景（真机验证结论）：Sa-Token 的 timeout 是 86400 秒，本项目的调用路径不会触发自动续期
  // （网关只调 getLoginIdByToken），所以 token 是"登录后固定 24 小时过期"，与是否活跃无关。
  // 这里在剩余不足阈值时主动 /auth/refresh，避免用户操作中被突然登出。
  let lastTtlCheckAt = 0
  let refreshing: Promise<string | null> | null = null

  /** 单飞刷新：并发请求共享同一次刷新 */
  async function refreshTokenOnce(): Promise<string | null> {
    const stored = localStorage.getItem(tokenKey)
    if (!stored) return null
    if (!refreshing) {
      // 用裸 axios（不带本实例拦截器），避免与 401 处理相互递归
      const config: any = { withCredentials: cookieMode, headers: { ...appHeaders } }
      if (!cookieMode) {
        config.headers.Authorization = `Bearer ${stored}`
      }
      refreshing = axios
        .post(`${baseURL}/auth/refresh`, null, config)
        .then((res: any) => {
          const fresh = res?.data?.data?.token
          if (fresh && !cookieMode) {
            // 头模式：新 token 覆盖旧值；Cookie 模式：服务端已用 Set-Cookie 轮换，本地无需保存
            localStorage.setItem(tokenKey, fresh)
          }
          return fresh ?? null
        })
        .catch(() => null)
        .then((v: string | null) => {
          refreshing = null
          return v
        })
    }
    return refreshing
  }

  /**
   * 请求前确保登录态可用；探测失败时静默放行，不阻塞请求。
   * 返回：头模式下是可直接用于 Authorization 的 token；Cookie 模式下恒为 null（由浏览器带 Cookie）。
   */
  async function ensureFreshToken(): Promise<string | null> {
    const stored = localStorage.getItem(tokenKey)
    if (!stored) return null
    const passthrough = cookieMode ? null : stored
    const now = Date.now()
    if (now - lastTtlCheckAt < ttlCheckIntervalMs) return passthrough
    lastTtlCheckAt = now
    try {
      const config: any = { withCredentials: cookieMode, headers: { ...appHeaders } }
      if (!cookieMode) {
        config.headers.Authorization = `Bearer ${stored}`
      }
      const res = await axios.get(`${baseURL}/auth/token-ttl`, config)
      const ttl = res?.data?.data
      if (typeof ttl !== 'number') return passthrough
      if (ttl === -1 || ttl > refreshThresholdSeconds) return passthrough
      if (ttl <= 0) {
        // 服务端会话已失效：Cookie 模式交给 401 分支统一处理跳登录
        if (!cookieMode) localStorage.removeItem(tokenKey)
        return passthrough
      }
      const fresh = await refreshTokenOnce()
      return cookieMode ? null : (fresh ?? stored)
    } catch {
      return passthrough
    }
  }

  function handleUnauthorized() {
    if (!cookieMode) {
      localStorage.removeItem(tokenKey)
    }
    // 登录标记的清理交给各端 onUnauthorized（它会调 clearToken 并跳登录页）
    toast.error('登录已过期，请重新登录')
    onUnauthorized()
  }

  // 请求拦截器：头模式注入 Token；两种模式都先尝试续期（Cookie 模式由浏览器自动带 Cookie）
  request.interceptors.request.use(
    async (config: any) => {
      const token = await ensureFreshToken()
      if (!cookieMode && token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      return config
    },
    (error: any) => Promise.reject(error)
  )

  // 响应拦截器：解包 R<T> 结构，处理 401
  request.interceptors.response.use(
    (response: any) => {
      const res = response.data
      if (res.code !== undefined) {
        if (res.code === 200 || res.code === 0) {
          return res.data
        }
        if (res.code === 401) {
          handleUnauthorized()
          return Promise.reject(new Error(res.message || '未授权'))
        }
        toast.error(res.message || '请求失败')
        return Promise.reject(Object.assign(new Error(res.message || '请求失败'), { _handled: true }))
      }
      return res
    },
    (error: any) => {
      if (error.response?.status === 401) {
        handleUnauthorized()
      } else if (error.response?.status === 429) {
        // 网关限流：RequestRateLimiter 直接返回 429 且没有响应体，这里给固定中文提示
        toast.error('操作过于频繁，请稍后再试')
      } else {
        toast.error(error.response?.data?.message || error.message || '网络异常')
      }
      return Promise.reject(Object.assign(error, { _handled: true }))
    }
  )

  return request
}
