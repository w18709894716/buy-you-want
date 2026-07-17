/**
 * 路由鉴权中间件
 * 如果没有 token，重定向到登录页面
 */
export default defineNuxtRouteMiddleware((to, _from) => {
  // 服务端 SSR 时跳过鉴权（无法访问 localStorage/cookie）
  if (import.meta.server) return

  // 不需要鉴权的页面
  const publicPages = ['/', '/login', '/register', '/search']
  const isPublicPage = publicPages.some(page => to.path === page || to.path.startsWith('/product/'))

  if (isPublicPage) {
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
