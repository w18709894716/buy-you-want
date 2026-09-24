/**
 * Agent 连接生命周期管理（浏览器端）。
 *
 * 配置来源优先级：localStorage（byw_agent_config，便于不改构建切换）> 运行时配置（.env）。
 * 工具注册与连接分离：工具注册是本地行为，未配置密钥时也不影响页面运行。
 */
import { AgentClient, AgentWidget } from '~/agent/sdk/index.js'
import { registerBusinessTools } from '~/agent/registry'

export interface AgentConfig {
  endpoint: string
  systemId: string
  apiKey: string
}

const CONFIG_KEY = 'byw_agent_config'

/** 读取接入配置；缺少 systemId 或 apiKey 时返回 null（视为未配置） */
export function resolveAgentConfig(): AgentConfig | null {
  const fromRuntime = useRuntimeConfig().public.agent as Partial<AgentConfig> | undefined

  let fromStorage: Partial<AgentConfig> = {}
  try {
    fromStorage = JSON.parse(localStorage.getItem(CONFIG_KEY) || '{}')
  } catch {
    /* 忽略损坏的本地配置 */
  }

  const config: AgentConfig = {
    endpoint: fromStorage.endpoint || fromRuntime?.endpoint || 'ws://localhost:3100/ws',
    systemId: fromStorage.systemId || fromRuntime?.systemId || '',
    apiKey: fromStorage.apiKey || fromRuntime?.apiKey || '',
  }

  return config.systemId && config.apiKey ? config : null
}

/** 保存接入配置到 localStorage（下次刷新生效） */
export function saveAgentConfig(config: Partial<AgentConfig>): void {
  const current = JSON.parse(localStorage.getItem(CONFIG_KEY) || '{}')
  localStorage.setItem(CONFIG_KEY, JSON.stringify({ ...current, ...config }))
}

let client: AgentClient | null = null
let widget: AgentWidget | null = null
let toolsRegistered = false

export interface ConnectAgentOptions {
  /** 消息历史的 localStorage 键名后缀；不传则不持久化（见 AgentWidgetOptions.persistKey） */
  persistKey?: string
}

/** 建立连接：注册业务工具 → 创建 AgentClient → 渲染悬浮聊天窗 */
export function connectAgent(config: AgentConfig, options: ConnectAgentOptions = {}): AgentClient {
  if (client) return client

  const toolNames = toolsRegistered ? [] : registerBusinessTools()
  toolsRegistered = true

  client = new AgentClient({
    endpoint: config.endpoint,
    apiKey: config.apiKey,
    systemId: config.systemId,
    autoDiscoverDom: true,
    widget: true,
  })

  client.on('session.ready', () => {
    console.info(`[agent] 会话就绪：${client?.currentSessionId}`)
  })
  client.on('run.error', (payload) => {
    console.warn('[agent] 运行错误：', payload)
  })

  client.connect()
  // 上移一档，避开平台自身的右下角客服气泡（同为 56px 圆形、距边 24px）
  widget = new AgentWidget(client, { bottom: 96, persistKey: options.persistKey })

  if (import.meta.dev) {
    console.info(`[agent] 已连接 ${config.endpoint}，注册工具 ${toolNames.length} 个：`, toolNames)
  }

  return client
}

/** 断开连接并销毁悬浮窗（登出时调用） */
export function disconnectAgent(): void {
  if (!client) return
  widget?.destroy()
  client.destroy()
  widget = null
  client = null
}

/** 当前客户端实例（未连接时为 null） */
export function getAgentClient(): AgentClient | null {
  return client
}

/** 当前悬浮窗实例（未连接时为 null） */
export function getAgentWidget(): AgentWidget | null {
  return widget
}
