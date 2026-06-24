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

/** 获取 token */
export function getToken(): string | null {
  if (import.meta.server) return null
  return localStorage.getItem(TOKEN_KEY) || getCookieToken()
}

/** 从 cookie 中读取 token（兼容 SSR） */
function getCookieToken(): string | null {
  if (typeof document === 'undefined') return null
  const match = document.cookie.match(/(?:^|;\s*)byw_token=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

/** 设置 token */
export function setToken(token: string) {
  if (import.meta.server) return
  localStorage.setItem(TOKEN_KEY, token)
  document.cookie = `byw_token=${encodeURIComponent(token)}; path=/; max-age=${60 * 60 * 24 * 7}`
}

/** 清除 token */
export function clearToken() {
  if (import.meta.server) return
  localStorage.removeItem(TOKEN_KEY)
  document.cookie = 'byw_token=; path=/; max-age=0'
}

/** 核心请求函数 */
export async function request<T = any>(
  url: string,
  options: RequestOptions = {}
): Promise<T> {
  const { raw = false, ...fetchOptions } = options
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBase as string

  const token = getToken()

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
    // 401 处理：清除 token 并跳转登录
    if (error?.response?.status === 401 || error?.statusCode === 401) {
      clearToken()
      if (import.meta.client) {
        navigateTo('/login')
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
