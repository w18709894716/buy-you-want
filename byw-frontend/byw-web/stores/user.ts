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

    /**
     * 退出登录：回首页继续游客浏览（受保护页面由中间件引导至登录）。
     *
     * <p>必须调后端注销：只清本地的话，那个 token 在服务端仍然有效到 24 小时到期，
     * 从 localStorage / 浏览器历史 / 已泄露的日志里拿到它的人可以接着用。
     *
     * <p>顺序是"先记下 token → 立刻清本地并跳转 → 再拿记下的 token 发注销请求"：
     * 本地状态先清，用户点完立刻看到已退出，不必等一个可能很慢甚至失败的网络请求；
     * 而注销请求必须显式带上记下的 token（此时 getToken() 已经是 null 了）。
     * skipAuthRedirect 保证 token 早已失效时后端返回的 401 不会又弹一次登录框。
     */
    async logout() {
      const token = getToken()
      this.reset()
      navigateTo('/')
      if (!token) return
      try {
        await post('/auth/logout', null, {
          skipAuthRedirect: true,
          headers: { Authorization: `Bearer ${token}` },
        })
      } catch (error) {
        // 后端不可达也要保证本地已退出；服务端会话最迟 24h 后自行过期
        console.warn('[user] 服务端注销失败（本地登录态已清）', error)
      }
    },
  },
})
