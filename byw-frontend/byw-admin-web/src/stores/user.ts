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
  const username = ref<string>(localStorage.getItem('admin_username') || '')
  const menus = ref<MenuNode[]>(readJson<MenuNode[]>('admin_menus', []))
  const perms = ref<string[]>(readJson<string[]>('admin_perms', []))
  /** 权限集落盘时间戳：缓存必须有时效，否则后台改了授权，前端还会一直按旧权限放行 */
  const permsCachedAt = ref<number>(Number(localStorage.getItem('admin_perms_at') || 0))

  const login = async (loginForm: { username: string; password: string }) => {
    // 专用管理员登录接口，服务端校验 role 必须为 platform_admin
    const data: any = await request.post('/auth/admin/login', loginForm)
    // Cookie 模式：token 在 HttpOnly Cookie 里，本地只记"已登录"
    token.value = '1'
    username.value = data.username || loginForm.username
    setToken()
    localStorage.setItem('admin_username', data.username || loginForm.username)
    // 清掉上一个账号留下的菜单/权限缓存：否则换人登录后路由守卫会认为"已加载"而跳过重新拉取，
    // 低权限账号会照着高权限账号的菜单渲染（接口层仍会 403，但界面先骗了人）
    menus.value = []
    perms.value = []
    permsCachedAt.value = 0
    localStorage.removeItem('admin_menus')
    localStorage.removeItem('admin_perms')
    localStorage.removeItem('admin_perms_at')
    return data
  }

  // 登录后拉取当前员工的菜单树与权限集（后端按权限过滤下发）
  const fetchMenus = async () => {
    const data: any = await request.get('/admin/me/menus')
    menus.value = data.menus || []
    perms.value = data.perms || []
    permsCachedAt.value = Date.now()
    localStorage.setItem('admin_menus', JSON.stringify(menus.value))
    localStorage.setItem('admin_perms', JSON.stringify(perms.value))
    localStorage.setItem('admin_perms_at', String(permsCachedAt.value))
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
    menus.value = []
    perms.value = []
    permsCachedAt.value = 0
    clearToken()
    localStorage.removeItem('admin_username')
    localStorage.removeItem('admin_menus')
    localStorage.removeItem('admin_perms')
    localStorage.removeItem('admin_perms_at')
  }

  return {
    token,
    username,
    menus,
    perms,
    permsCachedAt,
    login,
    fetchMenus,
    logout
  }
})
