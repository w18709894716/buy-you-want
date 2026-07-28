import { defineStore } from 'pinia'
import { useCartStore } from '~/stores/cart'

interface UserState {
  token: string | null
  userId: number | null
  username: string
  nickname: string
  avatar: string
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: null,
    userId: null,
    username: '',
    nickname: '',
    avatar: '',
  }),

  getters: {
    isLoggedIn: (state): boolean => !!state.token,
  },

  actions: {
    /** 初始化 - 从 cookie/localStorage 恢复 token */
    init() {
      const token = getToken()
      if (token) {
        this.token = token
        this.getUserInfo()
      }
    },

    /** 登录 */
    async login(username: string, password: string) {
      const data = await post<{ token: string; userId: number; username: string; nickname: string; avatar: string }>(
        '/auth/login',
        { username, password }
      )
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.nickname = data.nickname
      this.avatar = data.avatar || ''
      setToken(data.token)
    },

    /** 注册 */
    async register(params: { username: string; password: string; phone: string; nickname: string }) {
      await post('/auth/register', params)
    },

    /** 获取用户信息 */
    async getUserInfo() {
      try {
        const data = await get<{ userId: number; username: string; nickname: string; avatar: string }>(
          '/user/me'
        )
        this.userId = data.userId
        this.username = data.username
        this.nickname = data.nickname
        this.avatar = data.avatar || ''
      } catch {
        // token 失效：静默清理登录态，不强跳登录页（游客可继续浏览）
        this.reset()
      }
    },

    /** 清理登录态（不跳转） */
    reset() {
      this.token = null
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      clearToken()
      // 同步清空购物车，避免残留上一个用户的数据
      useCartStore().clear()
    },

    /** 退出登录：回首页继续游客浏览（受保护页面由中间件引导至登录） */
    logout() {
      this.reset()
      navigateTo('/')
    },
  },
})
