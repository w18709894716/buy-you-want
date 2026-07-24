import { ref } from 'vue'
import { useUserStore } from '~/stores/user'

/**
 * 商品收藏 - 跨组件共享收藏态
 * 收藏 ID 集合仅在客户端登录后加载，商品卡片/详情页/收藏页共用同一份状态。
 */
const favoriteIds = ref<Set<number>>(new Set())
const loaded = ref(false)

export function useFavorites() {
  const userStore = useUserStore()

  /** 加载当前用户的收藏商品 ID（仅首次或强制刷新时请求） */
  async function loadFavoriteIds(force = false) {
    if (!userStore.isLoggedIn) {
      favoriteIds.value = new Set()
      loaded.value = true
      return
    }
    if (loaded.value && !force) return
    try {
      const ids = await get<number[]>('/user/favorite/ids')
      favoriteIds.value = new Set(ids || [])
    } catch {
      // 忽略：未登录或接口异常时保持空集合
    } finally {
      loaded.value = true
    }
  }

  /** 是否已收藏 */
  function isFavorited(productId: number): boolean {
    return favoriteIds.value.has(productId)
  }

  /**
   * 切换收藏状态
   * @returns 切换后的收藏状态（true 已收藏 / false 未收藏）
   */
  async function toggleFavorite(productId: number): Promise<boolean> {
    if (!userStore.isLoggedIn) {
      navigateTo('/login')
      return false
    }
    const wasFavorited = favoriteIds.value.has(productId)
    try {
      if (wasFavorited) {
        await del(`/user/favorite/${productId}`)
        favoriteIds.value.delete(productId)
      } else {
        await post(`/user/favorite/${productId}`)
        favoriteIds.value.add(productId)
      }
      // 重新赋值触发响应式更新
      favoriteIds.value = new Set(favoriteIds.value)
      return !wasFavorited
    } catch {
      return wasFavorited
    }
  }

  return { favoriteIds, loadFavoriteIds, isFavorited, toggleFavorite }
}
