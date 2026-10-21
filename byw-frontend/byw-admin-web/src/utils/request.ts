import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { createHttpClient } from '../../../shared/http-client'
import { LOGIN_FLAG_KEY, clearToken } from './token'

/**
 * 管理端的 HTTP 客户端：逻辑在 byw-frontend/shared/http-client.ts（admin/merchant 共用）。
 *
 * 认证已切到 **HttpOnly Cookie**（cookieMode: true）：前端不再读写 token、不注入 Authorization 头，
 * 浏览器自动携带 Cookie；后端仍同时接受 Authorization 头（双模式），回退时把 cookieMode 改回 false 即可。
 */
export default createHttpClient({
  axios,
  toast: ElMessage,
  cookieMode: true,
  // 告诉后端"我是哪一端"，这样三端各自登录时互不覆盖 Cookie
  clientApp: 'admin',
  onUnauthorized: () => {
    clearToken()
    router.push('/login')
  },
  tokenKey: LOGIN_FLAG_KEY
})
