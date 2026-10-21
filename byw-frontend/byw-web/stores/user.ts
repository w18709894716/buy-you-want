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
    /**
     * 初始化 - 从 localStorage 恢复 token（不拉用户信息）：中间件依赖 token 在 hydration 之前就位，
     * 而 getUserInfo 会改顶栏渲染结果，须由调用方推迟到 hydration 之后（见 plugins/init.client.ts）。
     */
    init() {
      const token = getToken()
      if (token) {
        this.token = token
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
        // /user/me 返回的是后端 User 实体，主键字段名是 id（没有 userId）
        const data = await get<{ id: number; username: string; nickname: string; avatar: string }>(
          '/user/me'
        )
        if (!data) {
          // 后端对查不到的用户返回 200 + null，这不是登录失效，别把人踢下线
          console.warn('[user] /user/me 返回空数据，保留登录态')
          return
        }
        this.userId = data.id
        this.username = data.username
        this.nickname = data.nickname
        this.avatar = data.avatar || ''
      } catch (error) {
        // 只有 token 真的失效才清登录态：request 层在 401 分支里已经 clearToken()，
        // 用"token 还在不在"来区分认证失效与接口自身出错（网络抖动、500、限流）。
        // 之前这里裸 catch 一律 reset()，任何后端故障都会表现成"莫名其妙自动退出"。
        if (getToken()) {
          console.error('[user] 获取用户信息失败，保留登录态', error)
          return
        }
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
