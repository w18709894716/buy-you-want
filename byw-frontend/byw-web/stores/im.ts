import { defineStore } from 'pinia'
import { connectIm, disconnectIm, sendFrame, sendOrReconnect, setFrameHandler } from '~/composables/useImSocket'

export interface ImMessage {
  id?: string
  conversationId: number
  senderId?: number
  senderRole: string // user | merchant
  shopId?: number
  userId?: number
  type: string // text | image | product_card | order_card
  content?: string
  /** 引用消息ID（im_messages._id），非空表示该消息为引用消息 */
  quoteId?: string
  /** 被引用消息内容快照（防原消息撤回后引用失效） */
  quoteContent?: string
  /** 被引用消息发送者姓名 */
  quoteSenderName?: string
  /** 是否已撤回（软撤回：内容替换为提示文案，保留记录） */
  recalled?: boolean
  extra?: Record<string, any>
  read?: boolean
  createdAt?: string
  /** 本地乐观消息标记（无 id，等待 WS echo 到达后按内容+时间差匹配移除） */
  _local?: boolean
  /** 乐观消息的本地发送时间戳（ms，用于 echo 匹配） */
  _sentAt?: number
  /** 系统消息类型（如 assign=客服接入），命中时用居中灰色小字提示，不走气泡 */
  systemType?: string
}

export interface ImConversation {
  id: number
  userId: number
  shopId: number
  shopName?: string
  /** 当前接待客服ID（无接待/服务已结束为空；用于判断是否处于机器人引导阶段） */
  assigneeId?: number
  lastMessage?: string
  lastMessageType?: string
  lastMessageTime?: string
  unread?: number
}

interface ImState {
  open: boolean
  inited: boolean
  conversations: ImConversation[]
  activeId: number | null
  messages: ImMessage[]
  unreadTotal: number
  peerTyping: boolean
  loadingMessages: boolean
  /** 待确认发送的商品/订单卡片：进入会话不自动发送，需用户点“发送”确认 */
  pendingCard: { type: string; extra: Record<string, any> } | null
  /** 最近一次后端操作失败原因（如撤回超时/非发送者撤回），UI 消费后调用 clearError 清空 */
  lastError: string | null
}

let typingTimer: any = null
// 消息加载请求序号：快速切换会话/重复加载时用于丢弃过期响应，防止旧快照覆盖新会话消息
let msgLoadSeq = 0

export const useImStore = defineStore('im', {
  state: (): ImState => ({
    open: false,
    inited: false,
    conversations: [],
    activeId: null,
    messages: [],
    unreadTotal: 0,
    peerTyping: false,
    loadingMessages: false,
    pendingCard: null,
    lastError: null,
  }),

  getters: {
    activeConversation: (state): ImConversation | undefined =>
      state.conversations.find(c => c.id === state.activeId),
  },

  actions: {
    /** 登录后初始化：建立 WS、注册收帧、拉取未读总数 */
    init() {
      if (import.meta.server || this.inited) return
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) return
      this.inited = true
      setFrameHandler((frame) => this.onFrame(frame))
      connectIm()
      this.loadUnreadTotal()
    },

    /** 退出登录：断开并清空 */
    teardown() {
      disconnectIm()
      this.$reset()
    },

    togglePanel() {
      this.open ? this.closePanel() : this.openPanel()
    },

    openPanel() {
      this.init()
      // 面板可能在空闲超时断开后重新打开，此处确保重连（connectIm 幂等）
      connectIm()
      this.open = true
      this.loadConversations()
      // 面板重新打开时，若已有活动会话：容器重建会丢失滚动位置（回到顶部），
      // 且断线期间可能漏收消息。重新拉取该会话消息，既补齐遗漏又触发 loadingMessages 监听自动滚到底部
      if (this.activeId) this.selectConversation(this.activeId)
    },

    closePanel() {
      this.open = false
      this.pendingCard = null
    },

    async loadUnreadTotal() {
      try {
        this.unreadTotal = await get<number>('/im/unread-total')
      } catch { /* ignore */ }
    },

    async loadConversations() {
      try {
        const list = await get<ImConversation[]>('/im/conversations')
        this.conversations = list || []
      } catch { /* ignore */ }
    },

    async loadMessages(conversationId: number) {
      const seq = ++msgLoadSeq
      this.loadingMessages = true
      try {
        const page = await get<any>('/im/messages', { conversationId, page: 1, pageSize: 50 })
        // 响应过期（期间已切换会话/再次加载）：丢弃，避免旧会话快照覆盖当前会话消息
        if (conversationId !== this.activeId || seq !== msgLoadSeq) return
        // 后端按时间倒序返回，前端展示需正序
        const fetched: ImMessage[] = (page?.list || []).slice().reverse()
        // 合并：GET 在途期间可能已有实时消息经 WS echo 进入当前会话，
        // 分页快照未必包含它们，若直接整体替换会把这些新消息冲掉（首句消失、刷新才出现）。
        // extras 只保留快照中没有的消息：按 id 排除快照已有消息（否则每次重开面板都会把
        // 整份快照再 append 一遍导致重复）；无 id 的乐观消息直接保留；再按时间过滤掉
        // 早于快照最早消息的历史残留（防止旧消息被带到最新区域）。
        const fetchedIds = new Set(fetched.filter(m => m.id).map(m => m.id))
        const snapshotStart = fetched.length ? (fetched[0].createdAt || '') : null
        // 快照中已含同内容 echo 时，切走期间未在本地处理的乐观消息应移除（防重复显示）
        const fetchedContents = new Set(fetched.filter(m => m.content).map(m => m.type + '|' + m.content))
        const extras = this.messages.filter(
          m => m.conversationId === conversationId
            && (!m.id || !fetchedIds.has(m.id))
            && (snapshotStart == null || (m.createdAt && m.createdAt > snapshotStart))
            && !(m._local && fetchedContents.has(m.type + '|' + m.content)),
        )
        this.messages = [...fetched, ...extras].sort(
          (a, b) => (a.createdAt || '\uffff').localeCompare(b.createdAt || '\uffff'),
        )
      } catch {
        // 加载失败不清空已有消息（可能含 echo 到达的实时消息）；本会话无任何消息时清空残留
        if (conversationId === this.activeId && seq === msgLoadSeq
          && !this.messages.some(m => m.conversationId === conversationId)) {
          this.messages = []
        }
      } finally {
        if (seq === msgLoadSeq) this.loadingMessages = false
      }
    },

    async selectConversation(conversationId: number) {
      this.activeId = conversationId
      this.peerTyping = false
      this.pendingCard = null
      await this.loadMessages(conversationId)
      this.markRead(conversationId)
    },

    /** 从商品/订单页发起会话，可携带卡片上下文（不自动发送，暂存为待确认卡片） */
    async startWithContext(ctx: { shopId: number; shopName?: string; card?: { type: string; extra: Record<string, any> } }) {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        useLoginModal().openLoginModal()
        return
      }
      this.init()
      this.open = true
      try {
        const conv = await post<ImConversation>('/im/conversation', { shopId: ctx.shopId })
        if (ctx.shopName) conv.shopName = ctx.shopName
        this.upsertConversation(conv)
        await this.selectConversation(conv.id)
        // 不再一进会话就自动发送卡片，改为暂存，由用户在确认横幅中决定是否发送
        this.pendingCard = ctx.card || null
      } catch { /* ignore */ }
    },

    /** 确认发送待确认的商品/订单卡片 */
    confirmSendCard() {
      if (!this.pendingCard || !this.activeId) { this.pendingCard = null; return }
      this.sendCard(this.pendingCard.type, this.pendingCard.extra)
      this.pendingCard = null
    },

    /** 取消待确认卡片 */
    cancelPendingCard() {
      this.pendingCard = null
    },

    sendText(content: string, quoteId?: string) {
      const text = (content || '').trim()
      if (!text || !this.activeId) return
      this.doSend({ type: 'text', content: text, quoteId })
    },

    /** 发送 FAQ 快捷问题（extra.faqClick 标记：后端据此走机器人自动回复，不创建服务、不转人工） */
    sendFaq(question: string) {
      const text = (question || '').trim()
      if (!text || !this.activeId) return
      this.doSend({ type: 'text', content: text, extra: { faqClick: true } })
    },

    sendImage(url: string) {
      if (!url || !this.activeId) return
      this.doSend({ type: 'image', content: url })
    },

    sendCard(type: string, extra: Record<string, any>) {
      if (!this.activeId) return
      this.doSend({ type, content: '', extra })
    },

    doSend(payload: { type: string; content: string; extra?: Record<string, any>; quoteId?: string }) {
      const conv = this.activeConversation
      const shopId = conv?.shopId
      // 本地乐观消息：点击发送后立即上屏（无 id，_local 标记），避免等待 WS echo 的空窗；
      // echo 到达后按“会话+类型+内容+发送时间差”匹配移除，保证不重复
      if (this.activeId) {
        this.messages.push({
          conversationId: this.activeId,
          senderRole: 'user',
          type: payload.type,
          content: payload.content,
          extra: payload.extra,
          quoteId: payload.quoteId,
          createdAt: new Date().toISOString(),
          _local: true,
          _sentAt: Date.now(),
        })
        this.messages.sort((a, b) => (a.createdAt || '\uffff').localeCompare(b.createdAt || '\uffff'))
      }
      // 若已因空闲超时断开，sendOrReconnect 会排队暂存并触发重连，连上后自动补发
      sendOrReconnect({
        action: 'send',
        conversationId: this.activeId,
        shopId,
        type: payload.type,
        content: payload.content,
        extra: payload.extra || null,
        quoteId: payload.quoteId || null,
      })
    },

    /** 限时撤回消息：发送 recall 帧，仅发送者本人 2 分钟内有效（后端强校验，失败经 error 帧返回原因） */
    recallMessage(messageId: string): boolean {
      if (!messageId || !this.activeId) return false
      return sendFrame({ action: 'recall', conversationId: this.activeId, messageId })
    },

    /** 清空最近一次后端操作错误（UI 展示后调用） */
    clearError() {
      this.lastError = null
    },

    notifyTyping() {
      if (!this.activeId) return
      sendFrame({ action: 'typing', conversationId: this.activeId })
    },

    async markRead(conversationId: number) {
      const conv = this.conversations.find(c => c.id === conversationId)
      if (conv) conv.unread = 0
      // 通知后端 + 广播 read 回执
      sendFrame({ action: 'read', conversationId })
      try {
        await post('/im/read', { conversationId })
      } catch { /* ignore */ }
      // 以后端为准重新同步未读总数：避免本地“绝对值/增量+1/减量”多来源混算长期累积漂移
      // （典型表现：角标一直卡在 1）。后端 markRead 已清零该会话未读，此处读回权威总数。
      await this.loadUnreadTotal()
    },

    upsertConversation(conv: ImConversation) {
      const idx = this.conversations.findIndex(c => c.id === conv.id)
      if (idx >= 0) {
        this.conversations[idx] = { ...this.conversations[idx], ...conv }
      } else {
        this.conversations.unshift(conv)
      }
    },

    /** 处理服务端下推帧 */
    onFrame(frame: Record<string, any>) {
      const action = frame.action
      const data = frame.data
      if (action === 'message') {
        this.onMessage(data as ImMessage)
      } else if (action === 'typing') {
        if (data?.conversationId === this.activeId && data?.senderRole !== 'user') {
          this.peerTyping = true
          if (typingTimer) clearTimeout(typingTimer)
          typingTimer = setTimeout(() => { this.peerTyping = false }, 3000)
        }
      } else if (action === 'read') {
        // 对端(商家)已读我方消息 -> 更新回执
        if (data?.conversationId === this.activeId && data?.readerRole === 'merchant') {
          this.messages.forEach(m => { if (m.senderRole === 'user') m.read = true })
        }
      } else if (action === 'recall') {
        // 消息被撤回：更新对应消息内容与标记（客服端/买家端均实时同步）
        const d = data as ImMessage
        if (!d?.id) return
        const target = this.messages.find(m => m.id === d.id)
        if (target) {
          target.content = d.content
          target.recalled = true
        }
        // 被撤回消息是会话最后一条时，同步会话列表摘要
        const conv = this.conversations.find(c => c.id === d.conversationId)
        if (conv && target && this.messages.indexOf(target) === this.messages.length - 1) {
          conv.lastMessage = d.content
        }
      } else if (action === 'error') {
        // 后端操作失败（如撤回超时/非发送者撤回），携带具体原因
        this.lastError = data?.message || '操作失败'
      }
    },

    onMessage(msg: ImMessage) {
      if (!msg || !msg.conversationId) return
      const fromPeer = msg.senderRole !== 'user' && !msg.systemType
      const isActive = msg.conversationId === this.activeId && this.open

      // 接待/接管/服务结束系统消息会改变会话接待状态（assigneeId），刷新列表让 FAQ 引导实时切换
      if (msg.systemType === 'assign' || msg.systemType === 'takeover' || msg.systemType === 'service-ended') {
        this.loadConversations()
      }

      // 更新会话摘要
      let conv = this.conversations.find(c => c.id === msg.conversationId)
      if (!conv) {
        // 收到新会话的消息，补一条占位（列表下次刷新会补全）
        conv = { id: msg.conversationId, userId: msg.userId || 0, shopId: msg.shopId || 0, unread: 0 }
        this.conversations.unshift(conv)
      }
      conv.lastMessage = summarize(msg)
      conv.lastMessageType = msg.type
      conv.lastMessageTime = msg.createdAt

      // 合并到当前会话消息流（按 id 去重）
      if (msg.conversationId === this.activeId) {
        if (msg.id) {
          // echo 到达：先移除对应的本地乐观消息（同会话+同类型+同内容，且发送时间差 3 秒内；只删一条，避免连发相同内容误删）
          const localIdx = this.messages.findIndex(m =>
            m._local && m.conversationId === msg.conversationId && m.type === msg.type && m.content === msg.content
            && Math.abs((m._sentAt || 0) - new Date(msg.createdAt || '').getTime()) < 3000)
          if (localIdx >= 0) this.messages.splice(localIdx, 1)
        }
        if (!msg.id || !this.messages.some(m => m.id && m.id === msg.id)) {
          this.messages.push(msg)
          // 实时消息可能乱序到达（如 FAQ 机器人回复先于用户消息 echo），按时间排序恢复正确顺序
          this.messages.sort((a, b) => (a.createdAt || '\uffff').localeCompare(b.createdAt || '\uffff'))
        }
      }

      if (fromPeer) {
        if (isActive) {
          this.markRead(msg.conversationId)
        } else {
          conv.unread = (conv.unread || 0) + 1
          this.unreadTotal += 1
        }
      }
    },
  },
})

function summarize(msg: ImMessage): string {
  if (msg.systemType) return msg.content || '[系统通知]'
  switch (msg.type) {
    case 'image': return '[图片]'
    case 'product_card': return '[商品]'
    case 'order_card': return '[订单]'
    default: return msg.content || ''
  }
}
