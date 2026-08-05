import { defineStore } from 'pinia'
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
  const token = ref<string>(localStorage.getItem('merchant_token') || '')
  const username = ref<string>(localStorage.getItem('merchant_username') || '')
  const shopId = ref<string>(localStorage.getItem('merchant_shop_id') || '')
  const userId = ref<string>(localStorage.getItem('merchant_user_id') || '')
  const menus = ref<MenuNode[]>(readJson<MenuNode[]>('merchant_menus', []))
  const perms = ref<string[]>(readJson<string[]>('merchant_perms', []))

  const login = async (loginForm: { username: string; password: string }) => {
    const data: any = await request.post('/auth/merchant/login', loginForm)
    token.value = data.token
    username.value = data.username || loginForm.username
    shopId.value = data.shopId != null ? String(data.shopId) : ''
    userId.value = data.userId != null ? String(data.userId) : ''
    localStorage.setItem('merchant_token', data.token)
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

  const logout = () => {
    token.value = ''
    username.value = ''
    shopId.value = ''
    userId.value = ''
    menus.value = []
    perms.value = []
    localStorage.removeItem('merchant_token')
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
