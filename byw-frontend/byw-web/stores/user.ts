import { defineStore } from 'pinia'

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
        '/user/auth/login',
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
      await post('/user/auth/register', params)
    },

    /** 获取用户信息 */
    async getUserInfo() {
      try {
        const data = await get<{ userId: number; username: string; nickname: string; avatar: string }>(
          '/user/info'
        )
        this.userId = data.userId
        this.username = data.username
        this.nickname = data.nickname
        this.avatar = data.avatar || ''
      } catch {
        this.logout()
      }
    },

    /** 退出登录 */
    logout() {
      this.token = null
      this.userId = null
      this.username = ''
      this.nickname = ''
      this.avatar = ''
      clearToken()
      navigateTo('/login')
    },
  },
})
