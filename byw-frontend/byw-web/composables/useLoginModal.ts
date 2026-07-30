import { ref } from 'vue'

/**
 * 全局登录弹框 - 跨组件共享显隐状态（模式同 useFavorites 的模块级共享 ref）
 * 未登录用户触发需要登录的操作（加购/收藏/领券/秒杀等）时打开弹框，
 * 原地登录不打断浏览；仅顶部导航"登录"链接与路由中间件才跳转登录页。
 */
const visible = ref(false)

export function useLoginModal() {
  function openLoginModal() {
    visible.value = true
  }

  function closeLoginModal() {
    visible.value = false
  }

  return { loginModalVisible: visible, openLoginModal, closeLoginModal }
}
