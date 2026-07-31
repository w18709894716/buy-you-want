<template>
  <div class="im-workbench">
    <!-- 左栏：会话列表 -->
    <div class="conv-panel">
      <div class="conv-header">
        <span class="title">客服会话</span>
        <span class="online-dot" :class="connected ? 'on' : 'off'" :title="connected ? '在线' : '连接中'" />
        <el-button link size="small" @click="loadConversations">刷新</el-button>
      </div>
      <div class="conv-list">
        <div v-if="conversations.length === 0" class="empty">暂无会话</div>
        <div
          v-for="c in conversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === activeId }"
          @click="selectConversation(c.id)"
        >
          <el-avatar :size="40" class="avatar">{{ ('U' + c.userId).slice(-2) }}</el-avatar>
          <div class="conv-main">
            <div class="conv-line">
              <span class="name">用户 {{ c.userId }}</span>
              <span class="time">{{ shortTime(c.lastMessageTime) }}</span>
            </div>
            <div class="conv-line">
              <span class="last">{{ c.lastMessage || '暂无消息' }}</span>
              <el-badge v-if="c.unread" :value="c.unread > 99 ? '99+' : c.unread" class="unread" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 中栏：聊天窗 -->
    <div class="chat-panel">
      <template v-if="activeId">
        <div class="chat-header">
          <span>用户 {{ activeConversation?.userId }}</span>
          <span v-if="peerTyping" class="typing">对方正在输入…</span>
        </div>
        <div ref="msgScroll" class="chat-body">
          <div v-if="loadingMessages" class="loading">加载中…</div>
          <div
            v-for="(m, i) in messages"
            :key="m.id || ('l' + i)"
            class="msg-row"
            :class="isMine(m) ? 'mine' : 'peer'"
          >
            <div class="bubble-wrap">
              <!-- 文本 -->
              <div v-if="m.type === 'text'" class="bubble" :class="isMine(m) ? 'b-mine' : 'b-peer'">{{ m.content }}</div>
              <!-- 图片 -->
              <a v-else-if="m.type === 'image'" :href="m.content" target="_blank" class="img-msg">
                <img :src="m.content" />
              </a>
              <!-- 商品卡片 -->
              <div v-else-if="m.type === 'product_card'" class="card">
                <div class="card-tag">商品咨询</div>
                <div class="card-body">
                  <img :src="m.extra?.image" class="card-img" />
                  <div class="card-info">
                    <div class="card-name">{{ m.extra?.name }}</div>
                    <div class="card-price">¥{{ fmtPrice(m.extra?.price) }}</div>
                  </div>
                </div>
              </div>
              <!-- 订单卡片（可点击查看详情） -->
              <div v-else-if="m.type === 'order_card'" class="card card-clickable" @click="openOrderDetail(m.extra?.orderNo)">
                <div class="card-tag">订单咨询 · {{ m.extra?.status }}</div>
                <div class="card-body">
                  <img v-if="m.extra?.image" :src="m.extra?.image" class="card-img" />
                  <div class="card-info">
                    <div class="card-name">{{ m.extra?.productName || '订单商品' }}</div>
                    <div class="card-no">订单号 {{ m.extra?.orderNo }}</div>
                  </div>
                </div>
                <div class="card-detail-link">查看订单详情 ›</div>
              </div>
              <div class="msg-meta" :class="isMine(m) ? 'right' : 'left'">
                {{ shortTime(m.createdAt) }}
                <span v-if="isMine(m) && m.read" class="read">已读</span>
              </div>
            </div>
          </div>
        </div>
        <div class="chat-input">
          <!-- 工具栏 -->
          <div class="input-toolbar">
            <el-popover placement="top-start" :width="320" trigger="click" popper-class="emoji-popover">
              <template #reference>
                <el-button link title="表情">
                  <el-icon :size="20"><svg viewBox="0 0 20 20" fill="currentColor" width="20" height="20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-.464 5.535a1 1 0 10-1.415-1.414 3 3 0 01-4.242 0 1 1 0 00-1.415 1.414 5 5 0 007.072 0z" clip-rule="evenodd" /></svg></el-icon>
                </el-button>
              </template>
              <EmojiPicker :native="true" :hide-search="false" :disable-skin-tones="true" @select="onSelectEmoji" />
            </el-popover>
            <el-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :show-file-list="false"
              accept="image/*"
              :before-upload="beforeUpload"
              :on-success="onImageUploaded"
              class="img-upload"
            >
              <el-button link title="图片">
                <el-icon :size="20"><Picture /></el-icon>
              </el-button>
            </el-upload>
          </div>
          <!-- 文本输入 -->
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            resize="none"
            maxlength="500"
            placeholder="请输入消息，按 Enter 发送 / Shift+Enter 换行"
            class="input-textarea"
            @input="notifyTyping"
            @keydown.enter.exact.prevent="send"
          />
          <!-- 底部操作条：字数统计 + 发送按钮 -->
          <div class="input-footer">
            <span class="count">{{ draft.length }}/500</span>
            <el-button type="primary" :disabled="!draft.trim()" @click="send">发送</el-button>
          </div>
        </div>
      </template>
      <div v-else class="chat-empty">
        <el-icon :size="48"><ChatDotRound /></el-icon>
        <p>选择左侧会话开始回复</p>
      </div>
    </div>

    <!-- 右栏：买家与关联信息 -->
    <div class="info-panel">
      <template v-if="activeConversation">
        <div class="info-block">
          <div class="info-title">买家信息</div>
          <div class="info-row"><span>用户ID</span><span>{{ activeConversation.userId }}</span></div>
          <div class="info-row"><span>会话ID</span><span>{{ activeConversation.id }}</span></div>
        </div>
        <div class="info-block">
          <div class="info-title">咨询上下文</div>
          <div v-if="lastCard" class="ctx-card">
            <template v-if="lastCard.type === 'product_card'">
              <img v-if="lastCard.extra?.image" :src="lastCard.extra.image" class="ctx-img" />
              <div class="ctx-name">{{ lastCard.extra?.name }}</div>
              <div class="ctx-sub">¥{{ fmtPrice(lastCard.extra?.price) }}</div>
            </template>
            <template v-else-if="lastCard.type === 'order_card'">
              <img v-if="lastCard.extra?.image" :src="lastCard.extra.image" class="ctx-img" />
              <div class="ctx-name">{{ lastCard.extra?.productName || '订单商品' }}</div>
              <div class="ctx-sub">订单号 {{ lastCard.extra?.orderNo }}</div>
              <div class="ctx-sub">状态：{{ lastCard.extra?.status }}</div>
            </template>
          </div>
          <div v-else class="info-empty">暂无关联商品/订单</div>
        </div>
      </template>
      <div v-else class="info-empty full">未选择会话</div>
    </div>

    <!-- 订单详情弹窗（问题5：订单卡片可点击查看） -->
    <el-dialog v-model="orderDetailVisible" title="订单详情" width="680px" append-to-body>
      <div v-loading="orderDetailLoading" style="min-height:120px;">
        <template v-if="orderDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ orderDetail.userId }}</el-descriptions-item>
            <el-descriptions-item label="订单金额">¥{{ formatAmount(orderDetail.payAmount) }}</el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="statusType(orderDetail.status)">{{ statusLabel(orderDetail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="下单时间">{{ formatFullTime(orderDetail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="收货地址" :span="2">{{ orderDetail.receiverAddress || '-' }}</el-descriptions-item>
          </el-descriptions>

          <el-divider>订单商品</el-divider>
          <el-table :data="orderDetail.items || []" stripe border size="small">
            <el-table-column prop="productName" label="商品名称" min-width="180" />
            <el-table-column prop="skuName" label="规格" width="120" />
            <el-table-column prop="price" label="单价" width="100">
              <template #default="{ row }">¥{{ formatAmount(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column prop="subtotal" label="小计" width="100">
              <template #default="{ row }">¥{{ formatAmount((row.price || 0) * (row.quantity || 0)) }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else-if="!orderDetailLoading" description="未获取到订单详情" />
      </div>
      <template #footer>
        <el-button @click="orderDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ChatDotRound, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EmojiPicker from 'vue3-emoji-picker'
import 'vue3-emoji-picker/css'
import request from '../../utils/request'
import { connected, connectIm, sendFrame, addFrameHandler, removeFrameHandler } from '../../utils/imSocket'

interface ImMessage {
  id?: string
  conversationId: number
  senderId?: number
  senderRole: string
  shopId?: number
  userId?: number
  type: string
  content?: string
  extra?: Record<string, any>
  read?: boolean
  createdAt?: any
}

interface ImConversation {
  id: number
  userId: number
  shopId: number
  lastMessage?: string
  lastMessageType?: string
  lastMessageTime?: any
  unread?: number
}

const conversations = ref<ImConversation[]>([])
const activeId = ref<number | null>(null)
const messages = ref<ImMessage[]>([])
const loadingMessages = ref(false)
const peerTyping = ref(false)
const draft = ref('')
const msgScroll = ref<HTMLElement | null>(null)
let typingTimer: ReturnType<typeof setTimeout> | null = null

const activeConversation = computed(() => conversations.value.find(c => c.id === activeId.value))

// 取当前会话内最近一条商品/订单卡片作为右栏上下文
const lastCard = computed<ImMessage | undefined>(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const t = messages.value[i].type
    if (t === 'product_card' || t === 'order_card') return messages.value[i]
  }
  return undefined
})

const uploadAction = '/api/file/upload?folder=im'
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('merchant_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

function isMine(m: ImMessage) {
  return m.senderRole === 'merchant'
}

async function loadConversations() {
  try {
    const list = await request.get<any, ImConversation[]>('/im/conversations')
    conversations.value = list || []
  } catch { /* handled */ }
}

async function loadMessages(conversationId: number) {
  loadingMessages.value = true
  try {
    const page = await request.get<any, any>('/im/messages', { params: { conversationId, page: 1, pageSize: 50 } })
    // 后端按时间倒序返回，前端展示需正序
    const fetched = ((page?.list || []) as ImMessage[]).slice().reverse()
    // 合并：GET 在途期间可能已有实时消息经 WS echo 进入当前会话，
    // 若直接整体替换会把这些新消息冲掉（首句消失、刷新才出现），故按 id 去重合并。
    if (conversationId === activeId.value) {
      const fetchedIds = new Set(fetched.filter(m => m.id).map(m => m.id))
      const extras = messages.value.filter(
        m => m.conversationId === conversationId && (!m.id || !fetchedIds.has(m.id)),
      )
      messages.value = [...fetched, ...extras]
    } else {
      messages.value = fetched
    }
  } catch {
    // 加载失败不清空已有消息（可能含 echo 到达的实时消息）
    if (conversationId !== activeId.value) messages.value = []
  } finally {
    loadingMessages.value = false
    scrollToBottom()
  }
}

async function selectConversation(conversationId: number) {
  activeId.value = conversationId
  peerTyping.value = false
  await loadMessages(conversationId)
  markRead(conversationId)
}

function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value) return
  const ok = sendFrame({ action: 'send', conversationId: activeId.value, type: 'text', content: text })
  if (!ok) { connectIm(); ElMessage.warning('连接已断开，正在重连…'); return }
  draft.value = ''
}

// 表情选择：把 Unicode 字符追加到输入框，仍作为 text 消息发送
function onSelectEmoji(emoji: { i: string }) {
  draft.value += emoji.i
}

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isImage) { ElMessage.error('只能上传图片文件'); return false }
  if (!isLt5M) { ElMessage.error('图片大小不能超过 5MB'); return false }
  return true
}

function onImageUploaded(response: any) {
  const url = typeof response?.data === 'string' ? response.data : (response?.data?.url || response?.url || '')
  if (!url || !activeId.value) { ElMessage.error('上传成功但未获取到文件地址'); return }
  sendFrame({ action: 'send', conversationId: activeId.value, type: 'image', content: url })
}

function notifyTyping() {
  if (!activeId.value) return
  sendFrame({ action: 'typing', conversationId: activeId.value })
}

function markRead(conversationId: number) {
  const conv = conversations.value.find(c => c.id === conversationId)
  if (conv) conv.unread = 0
  sendFrame({ action: 'read', conversationId })
  request.post('/im/read', { conversationId }).catch(() => { /* ignore */ })
}

// 处理服务端下推帧
function onFrame(frame: Record<string, any>) {
  const action = frame.action
  const data = frame.data
  if (action === 'message') {
    onMessage(data as ImMessage)
  } else if (action === 'typing') {
    if (data?.conversationId === activeId.value && data?.senderRole === 'user') {
      peerTyping.value = true
      if (typingTimer) clearTimeout(typingTimer)
      typingTimer = setTimeout(() => { peerTyping.value = false }, 3000)
    }
  } else if (action === 'read') {
    // 买家已读商家消息 -> 更新回执
    if (data?.conversationId === activeId.value && data?.readerRole === 'user') {
      messages.value.forEach(m => { if (m.senderRole === 'merchant') m.read = true })
    }
  }
}

function onMessage(msg: ImMessage) {
  if (!msg || !msg.conversationId) return
  const fromPeer = msg.senderRole === 'user'
  const isActive = msg.conversationId === activeId.value

  let conv = conversations.value.find(c => c.id === msg.conversationId)
  if (!conv) {
    conv = { id: msg.conversationId, userId: msg.userId || 0, shopId: msg.shopId || 0, unread: 0 }
    conversations.value.unshift(conv)
  }
  conv.lastMessage = summarize(msg)
  conv.lastMessageType = msg.type
  conv.lastMessageTime = msg.createdAt
  // 置顶最新会话
  const idx = conversations.value.indexOf(conv)
  if (idx > 0) {
    conversations.value.splice(idx, 1)
    conversations.value.unshift(conv)
  }

  if (isActive) {
    if (!msg.id || !messages.value.some(m => m.id && m.id === msg.id)) {
      messages.value.push(msg)
      scrollToBottom()
    }
    if (fromPeer) markRead(msg.conversationId)
  } else if (fromPeer) {
    conv.unread = (conv.unread || 0) + 1
  }
}

function summarize(msg: ImMessage): string {
  switch (msg.type) {
    case 'image': return '[图片]'
    case 'product_card': return '[商品]'
    case 'order_card': return '[订单]'
    default: return msg.content || ''
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = msgScroll.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function fmtPrice(p: any): string {
  const n = Number(p)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

// ---- 订单详情弹窗（问题5：订单卡片可点击查看详情） ----
const orderDetailVisible = ref(false)
const orderDetailLoading = ref(false)
const orderDetail = ref<any>(null)

const orderStatusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待付款', type: 'info' },
  1: { label: '待发货', type: 'warning' },
  2: { label: '已发货', type: 'primary' },
  3: { label: '交易完成', type: 'success' },
  4: { label: '交易关闭', type: 'danger' },
  5: { label: '退款中', type: 'warning' },
  7: { label: '部分发货', type: 'warning' },
}
const statusLabel = (s: number) => orderStatusMap[s]?.label || s
const statusType = (s: number) => (orderStatusMap[s]?.type as any) || 'info'
const formatAmount = (a: any) => (a === null || a === undefined ? '0.00' : Number(a).toFixed(2))

function formatFullTime(t: any): string {
  if (!t) return '-'
  if (Array.isArray(t)) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = t
    return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(mi).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return String(t)
}

async function openOrderDetail(orderNo?: string) {
  if (!orderNo) { ElMessage.warning('该卡片缺少订单号'); return }
  orderDetailVisible.value = true
  orderDetailLoading.value = true
  orderDetail.value = null
  try {
    orderDetail.value = await request.get<any, any>(`/merchant/order/${orderNo}`)
  } catch (e: any) {
    if (!e?._handled) ElMessage.error(e?.message || '获取订单详情失败')
    orderDetailVisible.value = false
  } finally {
    orderDetailLoading.value = false
  }
}

function shortTime(t: any): string {
  if (!t) return ''
  let d: Date
  if (Array.isArray(t)) {
    const [y, mo, day, h = 0, mi = 0] = t
    d = new Date(y, (mo || 1) - 1, day, h, mi)
  } else {
    d = new Date(t)
  }
  if (isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

onMounted(() => {
  addFrameHandler(onFrame)
  // 连接由布局层全局接管；此处 connectIm 幂等，仅处理直接深入 /im 页面的场景
  connectIm()
  loadConversations()
})

onUnmounted(() => {
  // 仅移除本页面的帧监听；不断开全局连接，以保证离开客服页后菜单未读角标仍能实时更新
  removeFrameHandler(onFrame)
  if (typingTimer) clearTimeout(typingTimer)
})
</script>

<style scoped lang="scss">
.im-workbench {
  display: flex;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

/* 左栏 */
.conv-panel {
  width: 280px;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;

  .conv-header {
    height: 52px;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 16px;
    border-bottom: 1px solid #f5f5f5;

    .title { font-weight: 600; flex: 1; }
    .online-dot { width: 8px; height: 8px; border-radius: 50%; }
    .online-dot.on { background: #67c23a; }
    .online-dot.off { background: #dcdfe6; }
  }

  .conv-list {
    flex: 1;
    overflow-y: auto;

    .empty { padding: 40px 0; text-align: center; color: #c0c4cc; font-size: 13px; }

    .conv-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      cursor: pointer;
      border-bottom: 1px solid #fafafa;

      &:hover { background: #f7f8fa; }
      &.active { background: var(--el-color-primary-light-9); }

      .avatar { flex-shrink: 0; background: var(--el-color-primary-light-7); color: var(--el-color-primary); }
      .conv-main { flex: 1; min-width: 0; }
      .conv-line { display: flex; align-items: center; justify-content: space-between; }
      .name { font-size: 14px; color: #303133; font-weight: 500; }
      .time { font-size: 12px; color: #c0c4cc; }
      .last { font-size: 12px; color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 160px; margin-top: 2px; }
    }
  }
}

/* 中栏 */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;

  .chat-header {
    height: 52px;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 16px;
    border-bottom: 1px solid #f5f5f5;
    font-weight: 500;

    .typing { font-size: 12px; color: var(--el-color-primary); font-weight: normal; }
  }

  .chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    background: #f7f8fa;

    .loading { text-align: center; color: #c0c4cc; font-size: 12px; padding: 8px; }

    .msg-row { display: flex; margin-bottom: 14px; }
    .msg-row.mine { justify-content: flex-end; }
    .msg-row.peer { justify-content: flex-start; }

    .bubble-wrap { max-width: 60%; }

    .bubble {
      padding: 8px 12px;
      border-radius: 10px;
      font-size: 14px;
      line-height: 1.5;
      word-break: break-word;
      white-space: pre-wrap;
    }
    .b-mine { background: var(--el-color-primary); color: #fff; border-bottom-right-radius: 2px; }
    .b-peer { background: #fff; color: #303133; border-bottom-left-radius: 2px; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }

    .img-msg img { max-width: 200px; border-radius: 8px; display: block; }

    .card {
      background: #fff;
      border: 1px solid #f0f0f0;
      border-radius: 8px;
      padding: 8px;
      width: 240px;

      .card-tag { font-size: 11px; color: #c0c4cc; margin-bottom: 6px; }
      .card-body { display: flex; gap: 8px; }
      .card-img { width: 56px; height: 56px; border-radius: 4px; object-fit: cover; background: #f5f5f5; flex-shrink: 0; }
      .card-info { min-width: 0; }
      .card-name { font-size: 13px; color: #303133; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
      .card-price { color: var(--el-color-primary); font-weight: 600; margin-top: 4px; }
      .card-no { font-size: 12px; color: #909399; margin-top: 4px; }
    }

    .card-clickable {
      cursor: pointer;
      transition: border-color .2s, box-shadow .2s;

      &:hover {
        border-color: var(--el-color-primary);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      }

      .card-detail-link {
        margin-top: 8px;
        padding-top: 6px;
        border-top: 1px solid #f5f5f5;
        font-size: 12px;
        color: var(--el-color-primary);
        text-align: right;
      }
    }

    .msg-meta { font-size: 11px; color: #c0c4cc; margin-top: 4px; }
    .msg-meta.right { text-align: right; }
    .msg-meta.left { text-align: left; }
    .read { color: var(--el-color-primary); margin-left: 4px; }
  }

  .chat-input {
    display: flex;
    flex-direction: column;
    padding: 6px 12px 10px;
    border-top: 1px solid #f5f5f5;

    .input-toolbar {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 2px 0;

      .img-upload { line-height: 1; }
      .el-button { color: #909399; }
      .el-button:hover { color: var(--el-color-primary); }
    }

    // 输入框融入容器：去掉默认边框与内阴影
    .input-textarea {
      :deep(.el-textarea__inner) {
        box-shadow: none;
        padding: 4px 4px 0;
        font-size: 14px;
      }
    }

    .input-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      padding-top: 6px;

      .count { font-size: 12px; color: #c0c4cc; }
    }
  }

  .chat-empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #c0c4cc;
    gap: 12px;
  }
}

/* 右栏 */
.info-panel {
  width: 260px;
  border-left: 1px solid #f0f0f0;
  padding: 16px;
  flex-shrink: 0;
  overflow-y: auto;

  .info-block { margin-bottom: 20px; }
  .info-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 10px; }
  .info-row { display: flex; justify-content: space-between; font-size: 13px; color: #606266; padding: 4px 0; }
  .info-empty { color: #c0c4cc; font-size: 13px; text-align: center; padding: 16px 0; }
  .info-empty.full { padding-top: 60px; }

  .ctx-card {
    background: #f7f8fa;
    border-radius: 8px;
    padding: 12px;
    text-align: center;

    .ctx-img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; margin-bottom: 8px; }
    .ctx-name { font-size: 13px; color: #303133; }
    .ctx-sub { font-size: 12px; color: #909399; margin-top: 4px; }
  }
}
</style>

<!-- 表情选择器 popover 被 teleport 到 body，需非 scoped 样式去掉默认内边距 -->
<style lang="scss">
.emoji-popover.el-popover.el-popper {
  padding: 0;
  min-width: unset;
}
</style>
