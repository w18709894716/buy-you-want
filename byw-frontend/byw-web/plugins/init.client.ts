/**
 * 初始化插件 - 客户端初始化用户和购物车状态
 */
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'

export default defineNuxtPlugin(() => {
  // 仅在客户端执行
  const userStore = useUserStore()
  const cartStore = useCartStore()

  // 初始化用户状态（从 localStorage 恢复 token）
  userStore.init()
  // 用户信息与购物车都必须等 hydration 结束再取：两者的初值只存在于客户端
  // （token / localStorage），服务端渲染时是空的，提前写入会让首帧与服务端 HTML 对不上。
  onNuxtReady(() => {
    if (userStore.isLoggedIn) {
      userStore.getUserInfo()
      cartStore.getCartList()
    } else {
      cartStore.restoreLocal()
    }
  })
})
