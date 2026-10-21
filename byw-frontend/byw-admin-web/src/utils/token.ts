/**
 * 管理端登录态的唯一存放点。
 *
 * token 本体已经放进 **HttpOnly Cookie**（JS 读不到，XSS 偷不走），
 * 这里只维护一个"已登录"标记，供路由守卫 / 上传组件 / IM 连接判断要不要发起请求；
 * 真正的鉴权由浏览器自动携带 Cookie 完成。
 */
const LOGIN_FLAG_KEY = 'admin_logged_in'
// 历史遗留：切换前 token 存在这里，登录/登出时顺手清掉，避免旧值残留
const LEGACY_TOKEN_KEY = 'admin_token'

export function getToken(): string {
  return localStorage.getItem(LOGIN_FLAG_KEY) === '1' ? '1' : ''
}

export function setToken(_token?: string): void {
  localStorage.setItem(LOGIN_FLAG_KEY, '1')
  localStorage.removeItem(LEGACY_TOKEN_KEY)
}

export function clearToken(): void {
  localStorage.removeItem(LOGIN_FLAG_KEY)
  localStorage.removeItem(LEGACY_TOKEN_KEY)
}

export { LOGIN_FLAG_KEY, LEGACY_TOKEN_KEY }
