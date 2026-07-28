/**
 * 路由鉴权中间件
 * 如果没有 token，重定向到登录页面
 */
export default defineNuxtRouteMiddleware((to, _from) => {
  // 服务端 SSR 时跳过鉴权（无法访问 localStorage/cookie）
  if (import.meta.server) return

  // 公开页面无需鉴权（清单统一维护在 utils/request.ts 的 isPublicPath）
  if (isPublicPath(to.path)) {
    return
  }

  const token = getToken()

  if (!token) {
    return navigateTo({
      path: '/login',
      query: { redirect: to.fullPath },
    })
  }
})
