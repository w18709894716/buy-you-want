<template>
  <div class="im-workbench">
    <!-- 左栏：会话列表 -->
    <div class="conv-panel">
      <div class="conv-header">
        <span class="title">客服会话</span>
        <span class="online-dot" :class="connected ? 'on' : 'off'" :title="connected ? '在线' : '连接中'" />
        <el-button link size="small" @click="loadConversations">刷新</el-button>
      </div>
      <!-- 会话筛选：全部 / 待接入 / 我的 / 介入 / 已结束（服务结束的会话不属待接入，单独分组只读查看） -->
      <div class="conv-tabs">
        <span class="tab" :class="{ on: filterState === 'all' }" @click="filterState = 'all'">全部</span>
        <span class="tab" :class="{ on: filterState === 'pending' }" @click="filterState = 'pending'">待接入</span>
        <span class="tab" :class="{ on: filterState === 'mine' }" @click="filterState = 'mine'">我的</span>
        <span class="tab" :class="{ on: filterState === 'joined' }" @click="filterState = 'joined'">介入</span>
        <span class="tab" :class="{ on: filterState === 'ended' }" @click="filterState = 'ended'">服务结束</span>
      </div>
      <div class="conv-filter">
        <el-select v-model="selectedSkillGroupId" placeholder="全部技能组" clearable size="small" style="width: 100%" @change="onSkillGroupFilterChange">
          <el-option label="全部技能组" :value="null" />
          <el-option v-for="g in skillGroupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
        </el-select>
      </div>
      <div class="conv-list">
        <div v-if="filteredConversations.length === 0" class="empty">暂无会话</div>
        <div
          v-for="c in filteredConversations"
          :key="c.id"
          class="conv-item"
          :class="{ active: c.id === activeId }"
          @click="selectConversation(c.id)"
        >
          <el-avatar :size="40" class="avatar">{{ convAvatarText(c) }}</el-avatar>
          <div class="conv-main">
            <div class="conv-line">
              <span class="name">{{ convName(c) }}</span>
              <span class="assign-tag" :class="assignTagClass(c)">{{ assignTagText(c) }}</span>
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
          <span>{{ convName(activeConversation) }}</span>
          <span v-if="peerTyping" class="typing">对方正在输入…</span>
          <el-button v-if="isMyConversation" link size="small" type="primary" class="transfer-btn" @click="openTransferDialog">转接</el-button>
        </div>
        <!-- 只读提示：非接待/非介入客服打开已分配会话时显示，介入或接管后可回复；已结束服务会话显示专属提示 -->
        <div v-if="!canReply" class="readonly-bar">
          <template v-if="activeConversation?.assigneeId == null && activeConversation?.serviceActive === false">
            <span>该会话服务已结束，等待用户再次发起消息后自动分配</span>
          </template>
          <template v-else>
            <span>当前由 {{ activeConversation?.assigneeName || '其他客服' }} 接待，只读模式</span>
            <el-button size="small" @click="joinActive">介入</el-button>
            <el-button type="primary" size="small" @click="takeOverActive">接管</el-button>
          </template>
        </div>
        <div ref="msgScroll" class="chat-body">
          <div v-if="loadingMessages" class="loading">加载中…</div>
          <template v-for="(m, i) in messages" :key="m.id || ('l' + i)">
            <!-- 系统提示（如客服接入）：居中灰色小字，不用气泡 -->
            <div v-if="m.systemType" class="sys-msg">{{ m.content }}</div>
            <div v-else class="msg-row" :class="isMine(m) ? 'mine' : 'peer'" :data-msg-id="m.id" @contextmenu.prevent="showCtxMenu(m, $event)">
              <!-- 左侧：对方头像（名字首字） -->
              <div v-if="!isMine(m)" class="msg-avatar" :class="avatarClass(m)">{{ avatarText(m) }}</div>
              <div class="bubble-wrap">
                <!-- 名字：仅对方消息显示，自己的消息只显示头像 -->
                <div v-if="!isMine(m)" class="msg-name left">{{ msgName(m) }}</div>
                <!-- 引用条：点击定位到被引用消息 -->
                <div v-if="m.quoteId" class="quote-bar" @click="scrollToMessage(m.quoteId)">
                  <span class="q-name">{{ m.quoteSenderName || '消息' }}：</span>
                  <span v-if="isQuoteRecalled(m)" class="q-content">消息已撤回</span>
                  <span v-else class="q-content">{{ m.quoteContent }}</span>
                </div>
                <!-- 撤回态：统一显示撤回提示，不渲染原内容 -->
                <div v-if="m.recalled" class="recalled-tip">{{ m.content || '消息已撤回' }}</div>
                <!-- 文本 -->
                <div v-else-if="m.type === 'text'" class="bubble" :class="isMine(m) ? 'b-mine' : 'b-peer'">{{ m.content }}</div>
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
              <!-- 右侧：自己头像（名字首字） -->
              <div v-if="isMine(m)" class="msg-avatar mine-avatar">{{ avatarText(m) }}</div>
            </div>
          </template>
        </div>
        <div class="chat-input">
          <!-- 引用输入条：可取消 -->
          <div v-if="quoteTarget" class="quote-input-bar">
            <span class="q-text">引用 {{ quoteTarget.name }}：{{ quoteTarget.content }}</span>
            <el-icon class="q-close" @click="quoteTarget = null"><Close /></el-icon>
          </div>
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
            :disabled="!canReply"
            placeholder="请输入消息，按 Enter 发送 / Shift+Enter 换行"
            class="input-textarea"
            @input="notifyTyping"
            @keydown.enter.exact.prevent="send"
          />
          <!-- 底部操作条：字数统计 + 发送按钮 -->
          <div class="input-footer">
            <span class="count">{{ draft.length }}/500</span>
            <el-button type="primary" :disabled="!canReply || !draft.trim()" @click="send">发送</el-button>
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
    <!-- 转接弹窗：接待客服选择在线客服进行转接 -->
    <el-dialog v-model="transferVisible" title="转接给在线客服" width="420px" append-to-body>
      <div v-loading="transferLoading" class="transfer-list">
        <div v-for="s in onlineStaff" :key="s.id" class="transfer-item" @click="doTransfer(s)">
          <span class="t-name">{{ s.name }}</span>
          <span class="t-id">ID: {{ s.id }}</span>
        </div>
        <el-empty v-if="!transferLoading && onlineStaff.length === 0" description="暂无其他在线客服" />
      </div>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
      </template>
    </el-dialog>

    <!-- 右键菜单：撤回/引用 -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        ref="ctxMenuRef"
        class="im-ctx-menu"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <div v-if="ctxMenu.canRecall" class="im-ctx-item" @click="doRecall(ctxMenu.message)">撤回</div>
        <div v-if="ctxMenu.canQuote" class="im-ctx-item" @click="doQuote(ctxMenu.message)">引用</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { ChatDotRound, Close, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import EmojiPicker from 'vue3-emoji-picker'
import 'vue3-emoji-picker/css'
import request from '../../utils/request'
import { connected, connectIm, sendFrame, addFrameHandler, removeFrameHandler } from '../../utils/imSocket'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
// 当前登录客服ID（merchant_account.id），用于判断会话归属
const myStaffId = computed(() => (userStore.userId ? Number(userStore.userId) : null))

interface ImMessage {
  id?: string
  conversationId: number
  senderId?: number
  senderRole: string
  shopId?: number
  userId?: number
  type: string
  content?: string
  /** 发送者展示姓名（用户=昵称，客服=真实姓名） */
  senderName?: string
  /** 引用消息ID（im_messages._id），非空表示该消息为引用消息 */
  quoteId?: string
  /** 被引用消息内容快照 */
  quoteContent?: string
  /** 被引用消息发送者姓名 */
  quoteSenderName?: string
  /** 是否已撤回（软撤回：内容替换为提示文案） */
  recalled?: boolean
  extra?: Record<string, any>
  read?: boolean
  createdAt?: any
  /** 系统消息类型（如 assign=客服接入），命中时用居中灰色小字提示，不走气泡 */
  systemType?: string
}

interface ImConversation {
  id: number
  userId: number
  shopId: number
  /** 买家昵称（商家侧展示用，反查失败为空） */
  userNickname?: string
  lastMessage?: string
  lastMessageType?: string
  lastMessageTime?: any
  unread?: number
  /** 接待客服ID（merchant_account.id），null=待接入 */
  assigneeId?: number | null
  assigneeName?: string
  /** 介入客服ID集合（介入不影响原接待客服，可共同服务用户） */
  joiners?: number[]
  /** 所属技能组ID */
  skillGroupId?: number | null
  /** 是否有进行中的服务（false=服务已结束，不可接入，等用户再次发消息自动分配） */
  serviceActive?: boolean
}

const conversations = ref<ImConversation[]>([])
const activeId = ref<number | null>(null)
const messages = ref<ImMessage[]>([])
const loadingMessages = ref(false)
const peerTyping = ref(false)
const draft = ref('')
const msgScroll = ref<HTMLElement | null>(null)
let typingTimer: ReturnType<typeof setTimeout> | null = null
// 消息加载请求序号：快速切换会话/重复加载时用于丢弃过期响应，防止旧快照覆盖新会话消息
let msgLoadSeq = 0

// 会话筛选：全部 / 待接入 / 我的 / 介入 / 服务结束（纯前端过滤）
const filterState = ref<'all' | 'pending' | 'mine' | 'joined' | 'ended'>('all')
const skillGroupOptions = ref<any[]>([])
const selectedSkillGroupId = ref<number | null>(null)

const filteredConversations = computed(() => {
  let list = conversations.value
  // 技能组筛选
  if (selectedSkillGroupId.value != null) {
    list = list.filter(c => c.skillGroupId != null && Number(c.skillGroupId) === Number(selectedSkillGroupId.value))
  }
  if (filterState.value === 'pending') return list.filter(c => c.assigneeId == null && c.serviceActive !== false)
  if (filterState.value === 'mine') return list.filter(
    c => c.assigneeId != null && Number(c.assigneeId) === myStaffId.value,
  )
  // 我介入的会话：joiners 包含当前客服（介入不影响原接待客服）
  if (filterState.value === 'joined') return list.filter(
    c => myStaffId.value != null && (c.joiners || []).some(j => Number(j) === myStaffId.value),
  )
  // 服务已结束的会话：只读查看历史，不可接入（等用户再次发消息自动分配新服务）
  if (filterState.value === 'ended') return list.filter(c => c.assigneeId == null && c.serviceActive === false)
  return list
})

const activeConversation = computed(() => conversations.value.find(c => c.id === activeId.value))

// 是否可回复：待接入会话点开后自动接入即可回复；已分配会话仅接待者/介入者可回复（其余只读）；
// 已结束服务的会话不可回复（接入无意义，等用户再次发消息自动分配）
const canReply = computed(() => {
  const c = activeConversation.value
  if (!c) return false
  if (c.assigneeId == null) return c.serviceActive !== false
  if (Number(c.assigneeId) === myStaffId.value) return true
  return (c.joiners || []).some(j => Number(j) === myStaffId.value)
})
// 是否我接待的会话（显示转接按钮）
const isMyConversation = computed(() => {
  const c = activeConversation.value
  return !!c && c.assigneeId != null && Number(c.assigneeId) === myStaffId.value
})

// 会话归属标签：待接入（橙）/ 服务结束（灰）/ 我（蓝）/ 介入中（紫）/ 客服名（灰）
function assignTagText(c: ImConversation): string {
  if (c.assigneeId == null) return c.serviceActive === false ? '服务结束' : '待接入'
  if (Number(c.assigneeId) === myStaffId.value) return '我'
  if ((c.joiners || []).some(j => Number(j) === myStaffId.value)) return '介入中'
  return c.assigneeName || '其他客服'
}
function assignTagClass(c: ImConversation): string {
  if (c.assigneeId == null) return c.serviceActive === false ? 'ended' : 'pending'
  if (Number(c.assigneeId) === myStaffId.value) return 'mine'
  if ((c.joiners || []).some(j => Number(j) === myStaffId.value)) return 'join'
  return 'other'
}

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
  // 仅当前登录客服自己的消息在右侧；其他客服的消息在左侧（带头像/名字）
  return m.senderRole === 'merchant' && m.senderId != null && Number(m.senderId) === myStaffId.value
}

// 会话/聊天框对端展示名：昵称优先，兜底"用户 {id}"
function convName(c: ImConversation | null | undefined): string {
  if (!c) return ''
  return c.userNickname || ('用户 ' + c.userId)
}

// 会话列表头像文字：昵称首字，无昵称时取用户ID后两位
function convAvatarText(c: ImConversation): string {
  if (c.userNickname) return c.userNickname.charAt(0)
  return ('U' + c.userId).slice(-2)
}

// 消息发送者显示名：用户消息昵称（实时消息已填充，历史消息兜底会话昵称），客服消息真实姓名（仅对方消息使用）
function msgName(m: ImMessage): string {
  if (m.senderRole === 'user') {
    return m.senderName || activeConversation.value?.userNickname || ('用户 ' + (m.userId ?? ''))
  }
  return m.senderName || '客服'
}

// 头像文字：名字首字；用户消息无昵称时用"用"占位
function avatarText(m: ImMessage): string {
  if (m.senderRole === 'user') return (m.senderName || '用').charAt(0)
  return (m.senderName || '客').charAt(0)
}

// 头像配色：用户（橙）/ 其他客服（紫）；自己的头像用主色
function avatarClass(m: ImMessage): string {
  return m.senderRole === 'user' ? 'user-avatar' : 'staff-avatar'
}

// 引用目标（输入框上方显示，发送时随帧携带 quoteId）
const quoteTarget = ref<{ id: string; name: string; content: string } | null>(null)

// 设置引用目标：引用对方或自己的消息（已撤回的消息不支持引用）
function startQuote(m: ImMessage) {
  if (!m.id || m.recalled || !canReply.value) return
  quoteTarget.value = {
    id: m.id,
    name: msgName(m),
    content: m.recalled ? '消息已撤回' : (m.content || ''),
  }
}

// 被引用消息是否已撤回（从本地消息列表中查找，引用条改为显示"消息已撤回"）
function isQuoteRecalled(m: ImMessage): boolean {
  if (!m.quoteId) return false
  const quoted = messages.value.find(msg => msg.id === m.quoteId)
  return quoted?.recalled === true
}

// 仅自己的消息且发送 2 分钟内可撤回（后端强校验，前端仅控制按钮显隐）
function canRecall(m: ImMessage): boolean {
  if (!isMine(m) || m.recalled || !m.createdAt) return false
  const d = parseTime(m.createdAt)
  return d != null && Date.now() - d.getTime() < 120_000
}

// 撤回消息：发送 recall 帧，失败经 error 帧返回原因
function recallMessage(m: ImMessage) {
  if (!m.id || !activeId.value) return
  const ok = sendFrame({ action: 'recall', conversationId: activeId.value, messageId: m.id })
  if (!ok) { connectIm(); ElMessage.warning('连接已断开，正在重连…'); return }
}

// 右键菜单状态
const ctxMenu = reactive<{
  visible: boolean
  x: number
  y: number
  message: ImMessage | null
  canRecall: boolean
  canQuote: boolean
}>({
  visible: false,
  x: 0,
  y: 0,
  message: null,
  canRecall: false,
  canQuote: false,
})
const ctxMenuRef = ref<HTMLElement | null>(null)

// 点击菜单外部任意处关闭（document 级监听，不遮挡页面其他交互）
function onDocClick(e: MouseEvent) {
  const el = ctxMenuRef.value
  if (el && !el.contains(e.target as Node)) closeCtxMenu()
}
function closeCtxMenu() {
  ctxMenu.visible = false
  document.removeEventListener('click', onDocClick)
}

// 右键点击气泡显示操作菜单
function showCtxMenu(m: ImMessage, e: MouseEvent) {
  const canDoRecall = canRecall(m)
  const canDoQuote = !!m.id && !m.recalled && canReply.value
  // 无可用操作（如已撤回消息）时不弹菜单，避免空白色长条
  if (!canDoRecall && !canDoQuote) return
  const menuW = 110
  const menuH = 80
  let x = e.clientX
  let y = e.clientY
  if (x + menuW > window.innerWidth) x = window.innerWidth - menuW - 8
  if (y + menuH > window.innerHeight) y = window.innerHeight - menuH - 8
  ctxMenu.x = x
  ctxMenu.y = y
  ctxMenu.message = m
  ctxMenu.canRecall = canDoRecall
  ctxMenu.canQuote = canDoQuote
  ctxMenu.visible = true
  document.addEventListener('click', onDocClick)
}

// 右键菜单：撤回
function doRecall(m: ImMessage) {
  closeCtxMenu()
  recallMessage(m)
}

// 右键菜单：引用
function doQuote(m: ImMessage) {
  closeCtxMenu()
  startQuote(m)
}

// 点击引用条定位到被引用消息（滚动 + 高亮闪烁）
function scrollToMessage(messageId?: string) {
  if (!messageId) return
  const el = document.querySelector(`[data-msg-id="${messageId}"]`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('msg-flash')
  setTimeout(() => el.classList.remove('msg-flash'), 1200)
}

// 解析服务端时间（LocalDateTime 序列化可能为数组或字符串）
function parseTime(t: any): Date | null {
  if (!t) return null
  let d: Date
  if (Array.isArray(t)) {
    const [y, mo, day, h = 0, mi = 0, s = 0] = t
    d = new Date(y, (mo || 1) - 1, day, h, mi, s)
  } else {
    d = new Date(t)
  }
  return isNaN(d.getTime()) ? null : d
}

async function loadConversations() {
  try {
    const list = await request.get<any, ImConversation[]>('/im/conversations')
    conversations.value = list || []
  } catch { /* handled */ }
}

const fetchSkillGroups = async () => {
  try {
    const data: any = await request.get('/im/skill-group/list')
    skillGroupOptions.value = data || []
  } catch { /* handled */ }
}

function onSkillGroupFilterChange() {
  loadConversations()
}

async function loadMessages(conversationId: number) {
  const seq = ++msgLoadSeq
  loadingMessages.value = true
  try {
    const page = await request.get<any, any>('/im/messages', { params: { conversationId, page: 1, pageSize: 50 } })
    // 响应过期（期间已切换会话/再次加载）：丢弃，避免旧会话快照覆盖当前会话消息
    if (conversationId !== activeId.value || seq !== msgLoadSeq) return
    // 后端按时间倒序返回，前端展示需正序
    const fetched = ((page?.list || []) as ImMessage[]).slice().reverse()
    // 合并：GET 在途期间可能已有实时消息经 WS echo 进入当前会话，
    // 分页快照未必包含它们，若直接整体替换会把这些新消息冲掉（首句消失、刷新才出现）。
    // extras 只保留快照中没有的消息：按 id 排除快照已有消息（否则每次重开面板都会把
    // 整份快照再 append 一遍导致重复）；无 id 的乐观消息直接保留；再按时间过滤掉
    // 早于快照最早消息的历史残留（防止旧消息被带到最新区域）。
    const fetchedIds = new Set(fetched.filter(m => m.id).map(m => m.id))
    const snapshotStart = fetched.length ? (fetched[0].createdAt || '') : null
    const extras = messages.value.filter(
      m => m.conversationId === conversationId
        && (!m.id || !fetchedIds.has(m.id))
        && (snapshotStart == null || (m.createdAt && m.createdAt > snapshotStart)),
    )
    messages.value = [...fetched, ...extras].sort(
      (a, b) => (a.createdAt || '\uffff').localeCompare(b.createdAt || '\uffff'),
    )
  } catch {
    // 加载失败不清空已有消息（可能含 echo 到达的实时消息）；本会话无任何消息时清空残留
    if (conversationId === activeId.value && seq === msgLoadSeq
      && !messages.value.some(m => m.conversationId === conversationId)) {
      messages.value = []
    }
  } finally {
    if (seq === msgLoadSeq) loadingMessages.value = false
    scrollToBottom()
  }
}

async function selectConversation(conversationId: number) {
  activeId.value = conversationId
  peerTyping.value = false
  await loadMessages(conversationId)
  markRead(conversationId)
  // 待接入会话：客服点开对话框即主动接入（仅 assigneeId 为空且服务未结束时生效，不抢占已分配会话）
  const conv = conversations.value.find(c => c.id === conversationId)
  if (conv && conv.assigneeId == null && conv.serviceActive !== false) {
    sendFrame({ action: 'take', conversationId })
  }
}

function send() {
  const text = draft.value.trim()
  if (!text || !activeId.value) return
  if (!canReply.value) { ElMessage.warning('当前会话为只读，请先接管'); return }
  const ok = sendFrame({
    action: 'send',
    conversationId: activeId.value,
    type: 'text',
    content: text,
    quoteId: quoteTarget.value?.id,
  })
  if (!ok) { connectIm(); ElMessage.warning('连接已断开，正在重连…'); return }
  draft.value = ''
  quoteTarget.value = null
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
  if (!canReply.value) { ElMessage.warning('当前会话为只读，请先接管'); return }
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

// 介入当前会话：不影响原接待客服，可共同服务用户（广播后 onMessage 同步 joiners 并自动解锁输入）
function joinActive() {
  if (!activeId.value) return
  const ok = sendFrame({ action: 'join', conversationId: activeId.value })
  if (!ok) { connectIm(); ElMessage.warning('连接已断开，正在重连…'); return }
}

// 接管当前会话：待接入直接接入，已分配则替换接待者（广播后 onMessage 同步归属并自动解锁输入）
function takeOverActive() {
  if (!activeId.value) return
  const ok = sendFrame({ action: 'takeover', conversationId: activeId.value })
  if (!ok) { connectIm(); ElMessage.warning('连接已断开，正在重连…'); return }
}

// ---- 转接：拉取在线客服列表，选择目标后发送 transfer 帧 ----
const transferVisible = ref(false)
const transferLoading = ref(false)
const onlineStaff = ref<{ id: number; name: string }[]>([])

async function openTransferDialog() {
  if (!activeId.value) return
  transferVisible.value = true
  transferLoading.value = true
  try {
    const list = await request.get<any, any[]>('/im/staff/online')
    // 不显示自己（接待者不能转给自己）
    onlineStaff.value = (list || []).filter(s => Number(s.id) !== myStaffId.value)
  } catch {
    onlineStaff.value = []
  } finally {
    transferLoading.value = false
  }
}

function doTransfer(target: { id: number; name: string }) {
  if (!activeId.value) return
  const ok = sendFrame({ action: 'transfer', conversationId: activeId.value, targetStaffId: target.id })
  if (ok) {
    transferVisible.value = false
    ElMessage.success(`已转接给 ${target.name}`)
  } else {
    connectIm(); ElMessage.warning('连接已断开，正在重连…')
  }
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
    // 其他客服已读该会话 -> 清零本店会话未读角标（本店客服共享未读状态，实时同步无需刷新）
    if (data?.readerRole === 'merchant') {
      const conv = conversations.value.find(c => c.id === data.conversationId)
      if (conv) conv.unread = 0
    }
  } else if (action === 'recall') {
    // 消息被撤回：更新对应消息内容与标记（本店客服/买家端均实时同步）
    const d = data as ImMessage
    if (!d?.id) return
    const target = messages.value.find(m => m.id === d.id)
    if (target) {
      target.content = d.content
      target.recalled = true
    }
    // 被撤回消息是会话最后一条时，同步会话列表摘要
    const conv = conversations.value.find(c => c.id === d.conversationId)
    if (conv && target && messages.value.indexOf(target) === messages.value.length - 1) {
      conv.lastMessage = d.content
      conv.lastMessageType = d.type
    }
  } else if (action === 'error') {
    // 后端操作失败（如撤回超时/非发送者撤回），携带具体原因
    ElMessage.error(data?.message || '操作失败')
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
  // 系统消息（客服接入/接管/转接）：同步会话归属，其他客服实时看到最新接待者
  if (msg.systemType === 'assign' || msg.systemType === 'takeover' || msg.systemType === 'transfer') {
    conv.assigneeId = msg.senderId
    conv.assigneeName = msg.senderName
    // 服务已结束的会话收到接入广播 = 用户已再次发消息触发新服务，刷新列表同步服务状态标记
    if (conv.serviceActive === false) loadConversations()
  }
  // 系统消息（客服介入）：同步介入者集合，不影响原接待客服
  if (msg.systemType === 'join' && msg.senderId != null) {
    if (!(conv.joiners || []).some(j => Number(j) === Number(msg.senderId))) {
      conv.joiners = [...(conv.joiners || []), msg.senderId]
    }
  }
  // 服务超时结束：会话回到待接入状态，刷新会话列表（下次用户发消息自动重新分配）
  if (msg.systemType === 'service-ended') {
    conv.assigneeId = null
    conv.assigneeName = null
    loadConversations()
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
  if (msg.systemType) return msg.content || '[系统通知]'
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
  const d = parseTime(t)
  if (!d) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

onMounted(() => {
  addFrameHandler(onFrame)
  // 连接由布局层全局接管；此处 connectIm 幂等，仅处理直接深入 /im 页面的场景
  connectIm()
  loadConversations()
  fetchSkillGroups()
})

onUnmounted(() => {
  // 仅移除本页面的帧监听；不断开全局连接，以保证离开客服页后菜单未读角标仍能实时更新
  removeFrameHandler(onFrame)
  if (typingTimer) clearTimeout(typingTimer)
  // 清理右键菜单全局点击监听
  document.removeEventListener('click', onDocClick)
})
</script>

<style scoped lang="scss">
.transfer-list {
  max-height: 320px;
  overflow-y: auto;

  .transfer-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 12px;
    border-radius: 6px;
    cursor: pointer;

    &:hover { background: #f5f7fa; }
    .t-name { font-size: 14px; color: #303133; }
    .t-id { font-size: 12px; color: #c0c4cc; }
  }
}

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

  .conv-tabs {
    display: flex;
    gap: 6px;
    padding: 8px 12px;
    border-bottom: 1px solid #f5f5f5;

    .tab {
      flex: 1;
      text-align: center;
      font-size: 12px;
      color: #909399;
      padding: 4px 0;
      border-radius: 4px;
      cursor: pointer;

      &.on { color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-weight: 500; }
    }
  }

  .conv-filter {
    padding: 6px 12px;
    border-bottom: 1px solid #f5f5f5;
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
      .assign-tag {
        font-size: 11px;
        border-radius: 3px;
        padding: 1px 6px;
        flex-shrink: 0;
        margin-left: 6px;
      }
      .assign-tag.pending { color: #e6a23c; background: #fdf6ec; }
      .assign-tag.ended { color: #909399; background: #f4f4f5; }
      .assign-tag.mine { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
      .assign-tag.join { color: #9254de; background: #f9f0ff; }
      .assign-tag.other { color: #909399; background: #f4f4f5; }
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
    .transfer-btn { margin-left: auto; }
  }

  .readonly-bar {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    padding: 8px;
    font-size: 13px;
    color: #909399;
    background: #fafafa;
    border-bottom: 1px solid #f0f0f0;
  }

  .chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 16px;
    background: #f7f8fa;

    .loading { text-align: center; color: #c0c4cc; font-size: 12px; padding: 8px; }

    .msg-row { display: flex; margin-bottom: 14px; align-items: flex-start; }
    .msg-row.mine { justify-content: flex-end; }
    .msg-row.peer { justify-content: flex-start; }

    .msg-avatar {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      color: #fff;
      flex-shrink: 0;
    }
    .msg-row.peer .msg-avatar { margin-right: 8px; }
    .msg-row.mine .msg-avatar { margin-left: 8px; }
    .staff-avatar { background: #9254de; }
    .user-avatar { background: #e6a23c; }
    .mine-avatar { background: var(--el-color-primary); }

    .msg-name { font-size: 12px; color: #909399; margin-bottom: 4px; }

    .sys-msg {
      text-align: center;
      color: #909399;
      font-size: 12px;
      margin: 10px 0;
    }

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

    // 消息操作按钮（悬停消息行显示：引用/撤回）
    .msg-actions {
      display: none;
      margin-left: 8px;

      a {
        color: var(--el-color-primary);
        margin-left: 6px;
        cursor: pointer;
        font-size: 11px;
      }
    }
    .msg-row:hover .msg-actions { display: inline; }

    // 消息引用条：点击定位到被引用消息
    .quote-bar {
      display: flex;
      align-items: baseline;
      gap: 4px;
      max-width: 260px;
      margin-bottom: 4px;
      padding: 4px 8px;
      border-radius: 6px;
      background: rgba(0, 0, 0, 0.06);
      font-size: 12px;
      color: #909399;
      cursor: pointer;
      overflow: hidden;

      .q-name { flex-shrink: 0; }
      .q-content { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    }

    // 撤回态提示
    .recalled-tip {
      padding: 8px 12px;
      border-radius: 10px;
      font-size: 12px;
      color: #c0c4cc;
      background: rgba(0, 0, 0, 0.03);
    }

    // 引用定位高亮闪烁
    .msg-row[data-msg-id] { scroll-margin: 12px; }
    .msg-flash .bubble { animation: msg-flash 1.2s ease; }
    @keyframes msg-flash {
      0%, 100% { box-shadow: none; }
      30% { box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.4); }
    }
  }

  .chat-input {
    display: flex;
    flex-direction: column;
    padding: 6px 12px 10px;
    border-top: 1px solid #f5f5f5;

    // 引用输入条
    .quote-input-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
      padding: 4px 8px;
      border-radius: 6px;
      background: #f5f7fa;
      font-size: 12px;
      color: #606266;

      .q-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .q-close { cursor: pointer; color: #909399; }
      .q-close:hover { color: #606266; }
    }

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

/* 右键菜单：撤回/引用 */
.im-ctx-menu {
  position: fixed;
  z-index: 1001;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  border: 1px solid #e8e8e8;
  padding: 4px 0;
  min-width: 110px;
}
.im-ctx-item {
  padding: 6px 16px;
  font-size: 13px;
  color: #303133;
  cursor: pointer;
  user-select: none;
}
.im-ctx-item:hover {
  background: #f5f7fa;
}
.im-ctx-item:first-child:hover {
  color: #f56c6c;
}
</style>
