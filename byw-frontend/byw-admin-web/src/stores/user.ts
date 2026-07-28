import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '../utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('admin_token') || '')
  const username = ref<string>(localStorage.getItem('admin_username') || '')

  const login = async (loginForm: { username: string; password: string }) => {
    // 专用管理员登录接口，服务端校验 role 必须为 platform_admin
    const data: any = await request.post('/auth/admin/login', loginForm)
    token.value = data.token
    username.value = data.username || loginForm.username
    localStorage.setItem('admin_token', data.token)
    localStorage.setItem('admin_username', data.username || loginForm.username)
    return data
  }

  const logout = () => {
    token.value = ''
    username.value = ''
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_username')
  }

  return {
    token,
    username,
    login,
    logout
  }
})
