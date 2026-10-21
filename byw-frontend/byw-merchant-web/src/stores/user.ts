import { defineStore } from 'pinia'
import { getToken, setToken, clearToken } from '../utils/token'
import { ref } from 'vue'
import request from '../utils/request'

export interface MenuNode {
  id: number
  parentId: number
  menuName: string
  menuType: number
  path?: string
  permCode?: string
  icon?: string
  children?: MenuNode[]
}

const readJson = <T>(key: string, fallback: T): T => {
  try {
    const raw = localStorage.getItem(key)
    return raw ? (JSON.parse(raw) as T) : fallback
  } catch {
    return fallback
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const username = ref<string>(localStorage.getItem('merchant_username') || '')
  const shopId = ref<string>(localStorage.getItem('merchant_shop_id') || '')
  const userId = ref<string>(localStorage.getItem('merchant_user_id') || '')
  const menus = ref<MenuNode[]>(readJson<MenuNode[]>('merchant_menus', []))
  const perms = ref<string[]>(readJson<string[]>('merchant_perms', []))

  const login = async (loginForm: { username: string; password: string }) => {
    const data: any = await request.post('/auth/merchant/login', loginForm)
    // Cookie 模式：token 在 HttpOnly Cookie 里，本地只记"已登录"
    token.value = '1'
    username.value = data.username || loginForm.username
    shopId.value = data.shopId != null ? String(data.shopId) : ''
    userId.value = data.userId != null ? String(data.userId) : ''
    setToken()
    localStorage.setItem('merchant_username', data.username || loginForm.username)
    if (data.shopId != null) localStorage.setItem('merchant_shop_id', String(data.shopId))
    if (data.userId != null) localStorage.setItem('merchant_user_id', String(data.userId))
    return data
  }

  // 登录后拉取当前账号的菜单树与权限集（主账号=全菜单+*，子账号按角色过滤）
  const fetchMenus = async () => {
    const data: any = await request.get('/merchant/me/menus')
    menus.value = data.menus || []
    perms.value = data.perms || []
    localStorage.setItem('merchant_menus', JSON.stringify(menus.value))
    localStorage.setItem('merchant_perms', JSON.stringify(perms.value))
    return data
  }

  // 必须通知服务端清 Cookie：否则本地跳走了、Cookie 还在，刷新就又登上了
  const logout = async () => {
    try {
      await request.post('/auth/logout')
    } catch {
      // 服务端不可用也要把本地状态清掉
    }
    token.value = ''
    username.value = ''
    shopId.value = ''
    userId.value = ''
    menus.value = []
    perms.value = []
    clearToken()
    localStorage.removeItem('merchant_username')
    localStorage.removeItem('merchant_shop_id')
    localStorage.removeItem('merchant_user_id')
    localStorage.removeItem('merchant_menus')
    localStorage.removeItem('merchant_perms')
  }

  return {
    token,
    username,
    shopId,
    userId,
    menus,
    perms,
    login,
    fetchMenus,
    logout
  }
})
