import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '../utils/request'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('merchant_token') || '')
  const username = ref<string>(localStorage.getItem('merchant_username') || '')
  const shopId = ref<string>(localStorage.getItem('merchant_shop_id') || '')

  const login = async (loginForm: { username: string; password: string }) => {
    const data: any = await request.post('/auth/merchant/login', loginForm)
    token.value = data.token
    username.value = data.username || loginForm.username
    shopId.value = data.shopId != null ? String(data.shopId) : ''
    localStorage.setItem('merchant_token', data.token)
    localStorage.setItem('merchant_username', data.username || loginForm.username)
    if (data.shopId != null) localStorage.setItem('merchant_shop_id', String(data.shopId))
    return data
  }

  const logout = () => {
    token.value = ''
    username.value = ''
    shopId.value = ''
    localStorage.removeItem('merchant_token')
    localStorage.removeItem('merchant_username')
    localStorage.removeItem('merchant_shop_id')
  }

  return {
    token,
    username,
    shopId,
    login,
    logout
  }
})
