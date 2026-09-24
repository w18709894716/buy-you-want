/**
 * Agent 接入插件（仅客户端）。
 *
 * 登录后自动连接 AI Agent 中间件，登出时断开并销毁悬浮窗。
 * 密钥配置见 agent/client.ts 的 resolveAgentConfig。
 */
import { useUserStore } from '~/stores/user'
import {
  connectAgent,
  disconnectAgent,
  getAgentClient,
  getAgentWidget,
  resolveAgentConfig,
} from '~/agent/client'
import type { AgentConfig } from '~/agent/client'

declare global {
  interface Window {
    /** 开发期调试入口：__bywAgent.connect() / __bywAgent.getClient()?.ask('帮我查一下待发货订单') */
    __bywAgent?: {
      getClient: typeof getAgentClient
      config: AgentConfig | null
      connect: () => void
      disconnect: () => void
    }
  }
}

export default defineNuxtPlugin(() => {
  const config = resolveAgentConfig()

  if (!config) {
    console.info(
      '[agent] 未配置接入凭证，跳过 Agent 接入。请在 byw-web/.env 设置 NUXT_PUBLIC_AGENT_SYSTEM_ID / NUXT_PUBLIC_AGENT_API_KEY 后重启 dev server'
    )
    return
  }

  const userStore = useUserStore()
  // 消息历史按用户隔离；userId 由 /user/me 异步返回，未拿到时先不持久化
  const persistKey = () => (userStore.userId ? `byw-web:user-${userStore.userId}` : undefined)

  // 只等登录态，不等 /user/me：接口慢时图标照样立刻出现
  watch(
    () => userStore.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) {
        connectAgent(config, { persistKey: persistKey() })
      } else {
        disconnectAgent()
      }
    },
    { immediate: true }
  )

  // userId 晚于登录态到达（刷新场景）：此时再补设，历史键才会真正按用户隔离
  watch(
    () => userStore.userId,
    (userId) => {
      if (userId && userStore.isLoggedIn) getAgentWidget()?.setPersistKey(persistKey())
    }
  )

  if (import.meta.dev) {
    window.__bywAgent = {
      getClient: getAgentClient,
      config,
      connect: () => connectAgent(config, { persistKey: persistKey() }),
      disconnect: disconnectAgent,
    }
  }
})
