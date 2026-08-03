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
  const token = ref<string>(localStorage.getItem('admin_token') || '')
  const username = ref<string>(localStorage.getItem('admin_username') || '')
  const menus = ref<MenuNode[]>(readJson<MenuNode[]>('admin_menus', []))
  const perms = ref<string[]>(readJson<string[]>('admin_perms', []))

  const login = async (loginForm: { username: string; password: string }) => {
    // 专用管理员登录接口，服务端校验 role 必须为 platform_admin
    const data: any = await request.post('/auth/admin/login', loginForm)
    token.value = data.token
    username.value = data.username || loginForm.username
    localStorage.setItem('admin_token', data.token)
    localStorage.setItem('admin_username', data.username || loginForm.username)
    return data
  }

  // 登录后拉取当前员工的菜单树与权限集（后端按权限过滤下发）
  const fetchMenus = async () => {
    const data: any = await request.get('/admin/me/menus')
    menus.value = data.menus || []
    perms.value = data.perms || []
    localStorage.setItem('admin_menus', JSON.stringify(menus.value))
    localStorage.setItem('admin_perms', JSON.stringify(perms.value))
    return data
  }

  const logout = () => {
    token.value = ''
    username.value = ''
    menus.value = []
    perms.value = []
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
    localStorage.removeItem('admin_menus')
    localStorage.removeItem('admin_perms')
  }

  return {
    token,
    username,
    menus,
    perms,
    login,
    fetchMenus,
    logout
  }
})
