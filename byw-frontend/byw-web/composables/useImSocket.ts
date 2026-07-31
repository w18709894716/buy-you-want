/**
 * 客服 IM WebSocket 连接封装（模块级单例）。
 * - 直连网关 ws://<gateway>/ws/im?token=<JWT>（nitro devProxy 不支持 ws，须绕过）
 * - 心跳 ping/pong + 指数退避重连
 * - 收帧经 frameHandler 交给 stores/im 处理
 * 仅在 client 端运行。
 */
import { ref } from 'vue'
import { getToken } from '~/utils/request'

type Frame = Record<string, any>
type FrameHandler = (frame: Frame) => void

// 模块级共享状态：跨组件单例
const connected = ref(false)
// 因空闲超时而主动断开的标志（供聊天框展示“会话已超时结束”）
const idleClosed = ref(false)
let ws: WebSocket | null = null
let frameHandler: FrameHandler | null = null
let heartbeatTimer: any = null
let reconnectTimer: any = null
let idleTimer: any = null
let reconnectAttempts = 0
let manualClosed = false
// 断线期间用户主动发送的帧：排队暂存，重连成功后自动补发
let pendingFrames: Frame[] = []

const HEARTBEAT_INTERVAL = 25000
const MAX_RECONNECT_DELAY = 30000
// 用户端空闲超时：无收发消息/操作达 5 分钟则断开（心跳不算活动）
const IDLE_TIMEOUT = 5 * 60 * 1000

function buildUrl(): string | null {
  const token = getToken()
  if (!token) return null
  const config = useRuntimeConfig()
  const base = (config.public.wsBase as string) || 'ws://localhost:8080'
  return `${base}/ws/im?token=${encodeURIComponent(token)}`
}

function clearTimers() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  if (idleTimer) { clearTimeout(idleTimer); idleTimer = null }
}

// 重置空闲计时：仅在连接存活时调用，收发消息/主动操作时调用
function resetIdleTimer() {
  if (import.meta.server) return
  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = setTimeout(idleDisconnect, IDLE_TIMEOUT)
}

// 空闲超时断开：不自动重连，置 idleClosed 供 UI 提示；下次发消息/重开会话时才重连
function idleDisconnect() {
  console.info('[IM] 空闲超过 5 分钟，会话超时断开')
  idleClosed.value = true
  manualClosed = true
  clearTimers()
  connected.value = false
  if (ws) {
    try { ws.close() } catch { /* ignore */ }
    ws = null
  }
}

function startHeartbeat() {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  heartbeatTimer = setInterval(() => {
    sendFrame({ action: 'ping' })
  }, HEARTBEAT_INTERVAL)
}

function scheduleReconnect() {
  if (manualClosed) return
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), MAX_RECONNECT_DELAY)
  reconnectAttempts++
  if (reconnectTimer) clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => connectIm(), delay)
}

export function setFrameHandler(handler: FrameHandler) {
  frameHandler = handler
}

export function connectIm() {
  if (import.meta.server) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  const url = buildUrl()
  if (!url) return // 未登录

  manualClosed = false
  try {
    ws = new WebSocket(url)
  } catch (e) {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    console.info('[IM] WebSocket 已连接')
    connected.value = true
    idleClosed.value = false
    reconnectAttempts = 0
    startHeartbeat()
    resetIdleTimer()
    // 补发断线期间排队的用户消息
    if (pendingFrames.length) {
      const q = pendingFrames.splice(0)
      q.forEach(f => sendFrame(f))
    }
  }

  ws.onmessage = (evt) => {
    let frame: Frame
    try {
      frame = JSON.parse(evt.data)
    } catch {
      return
    }
    if (frame.action === 'pong') return
    // 收到业务帧视为会话活动，延长空闲计时
    resetIdleTimer()
    if (frameHandler) frameHandler(frame)
  }

  ws.onclose = () => {
    connected.value = false
    clearTimers()
    scheduleReconnect()
  }

  ws.onerror = () => {
    // onclose 会随后触发，统一在那里重连
    try { ws?.close() } catch { /* ignore */ }
  }
}

export function sendFrame(frame: Frame): boolean {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(frame))
    // 心跳不算活动；其余发送均延长空闲计时
    if (frame.action !== 'ping') resetIdleTimer()
    return true
  }
  return false
}

// 用户主动发送：若已（超时）断开，则排队暂存并触发重连，连上后自动补发
export function sendOrReconnect(frame: Frame) {
  if (sendFrame(frame)) return
  pendingFrames.push(frame)
  connectIm()
}

// 标记会话活动（打开面板/切换会话等），仅在连接存活时延长空闲计时
export function markActivity() {
  if (ws && ws.readyState === WebSocket.OPEN) resetIdleTimer()
}

export function disconnectIm() {
  manualClosed = true
  idleClosed.value = false
  pendingFrames = []
  clearTimers()
  reconnectAttempts = 0
  connected.value = false
  if (ws) {
    try { ws.close() } catch { /* ignore */ }
    ws = null
  }
}

export function useImSocket() {
  return { connected, idleClosed, connectIm, disconnectIm, sendFrame, sendOrReconnect, markActivity, setFrameHandler }
}
