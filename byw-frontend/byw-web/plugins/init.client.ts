/**
 * 初始化插件 - 客户端初始化用户和购物车状态
 */
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'

export default defineNuxtPlugin(() => {
  // 仅在客户端执行
  const userStore = useUserStore()
  const cartStore = useCartStore()

  // 初始化用户状态（从 token 恢复）
  userStore.init()
  // 初始化购物车
  cartStore.getCartList()
})
