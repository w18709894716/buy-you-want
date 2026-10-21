// 商家端 IM WebSocket 客户端（模块级单例）
// 浏览器 WebSocket 无法设置请求头，因此改用 HttpOnly Cookie 鉴权（握手时浏览器自动携带）；
// Vite devProxy 不支持 ws，开发环境直连网关 8080。
import { getToken } from './token'
import { ref } from 'vue'

const WS_BASE = (import.meta.env.VITE_WS_BASE as string) || 'ws://localhost:8080'

export const connected = ref(false)

let ws: WebSocket | null = null
let frameHandler: ((frame: Record<string, any>) => void) | null = null
// 支持多个订阅者：客服工作台页面渲染消息，同时布局层维护全局未读角标
const frameListeners = new Set<(frame: Record<string, any>) => void>()
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
let manualClosed = false

function buildUrl(): string | null {
  // 未登录不连；登录态现在由 HttpOnly Cookie 承载，浏览器会在 WS 握手时自动带上 Cookie，
  // 不再把 token 拼进 query（避免 token 出现在 URL 与访问日志里）。
  if (!getToken()) return null
  return `${WS_BASE}/ws/im`
}

export function setFrameHandler(handler: (frame: Record<string, any>) => void) {
  frameHandler = handler
}

/** 订阅帧（可多个订阅者并存），返回取消订阅函数 */
export function addFrameHandler(handler: (frame: Record<string, any>) => void) {
  frameListeners.add(handler)
  return () => frameListeners.delete(handler)
}

export function removeFrameHandler(handler: (frame: Record<string, any>) => void) {
  frameListeners.delete(handler)
}

export function connectIm() {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  const url = buildUrl()
  if (!url) return
  manualClosed = false
  try {
    ws = new WebSocket(url)
  } catch {
    scheduleReconnect()
    return
  }

  ws.onopen = () => {
    connected.value = true
    reconnectAttempts = 0
    startHeartbeat()
  }

  ws.onmessage = (ev) => {
    let frame: any
    try {
      frame = JSON.parse(ev.data)
    } catch {
      return
    }
    if (frame?.action === 'pong') return
    if (frameHandler) frameHandler(frame)
    frameListeners.forEach((h) => { try { h(frame) } catch { /* 单个订阅者异常不影响其他订阅者 */ } })
  }

  ws.onclose = () => {
    connected.value = false
    stopHeartbeat()
    if (!manualClosed) scheduleReconnect()
  }

  ws.onerror = () => {
    try { ws?.close() } catch { /* ignore */ }
  }
}

export function sendFrame(frame: Record<string, any>): boolean {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(frame))
    return true
  }
  return false
}

export function disconnectIm() {
  manualClosed = true
  stopHeartbeat()
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  if (ws) {
    try { ws.close() } catch { /* ignore */ }
    ws = null
  }
  connected.value = false
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    sendFrame({ action: 'ping' })
  }, 25000)
}

function stopHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

function scheduleReconnect() {
  if (manualClosed) return
  if (reconnectTimer) return
  reconnectAttempts++
  const delay = Math.min(30000, 1000 * Math.pow(2, Math.min(reconnectAttempts, 5)))
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connectIm()
  }, delay)
}
