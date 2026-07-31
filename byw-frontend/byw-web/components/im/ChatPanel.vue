<template>
  <div>
    <!-- 遮罩（移动端） -->
    <transition name="fade">
      <div v-if="im.open" class="fixed inset-0 bg-black/20 z-[59] md:hidden" @click="im.closePanel()" />
    </transition>

    <!-- 抽屉面板 -->
    <transition name="slide-up">
      <div
        v-if="im.open"
        class="fixed z-[60] bg-white shadow-2xl flex flex-col overflow-hidden
               bottom-0 right-0 w-full h-[80vh] rounded-t-2xl
               md:bottom-24 md:right-6 md:w-[380px] md:h-[560px] md:rounded-2xl"
      >
        <!-- 头部 -->
        <div class="flex items-center gap-2 px-4 h-12 bg-primary text-white flex-shrink-0">
          <button v-if="im.activeId" class="p-1 -ml-1 hover:opacity-80" @click="backToList">
            <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M12.79 5.23a.75.75 0 01-.02 1.06L8.832 10l3.938 3.71a.75.75 0 11-1.04 1.08l-4.5-4.25a.75.75 0 010-1.08l4.5-4.25a.75.75 0 011.06.02z" clip-rule="evenodd" /></svg>
          </button>
          <span class="font-medium text-sm flex-1 truncate">
            {{ im.activeId ? (im.activeConversation?.shopName || '客服') : '我的客服消息' }}
          </span>
          <span class="w-2 h-2 rounded-full" :class="connected ? 'bg-green-400' : 'bg-gray-300'" :title="connected ? '在线' : '连接中'" />
          <button class="p-1 -mr-1 hover:opacity-80" @click="im.closePanel()">
            <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" /></svg>
          </button>
        </div>

        <!-- 会话列表 -->
        <div v-if="!im.activeId" class="flex-1 overflow-y-auto">
          <div v-if="im.conversations.length === 0" class="p-8 text-center text-gray-400 text-sm">暂无会话</div>
          <button
            v-for="c in im.conversations"
            :key="c.id"
            class="w-full flex items-center gap-3 px-4 py-3 border-b border-gray-50 hover:bg-gray-50 text-left"
            @click="im.selectConversation(c.id)"
          >
            <div class="w-10 h-10 rounded-full bg-primary/10 text-primary flex items-center justify-center flex-shrink-0 font-medium">
              {{ (c.shopName || '店').charAt(0) }}
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-gray-800 truncate">{{ c.shopName || ('店铺' + c.shopId) }}</span>
                <span class="text-xs text-gray-400 flex-shrink-0 ml-2">{{ shortTime(c.lastMessageTime) }}</span>
              </div>
              <div class="text-xs text-gray-500 truncate mt-0.5">{{ c.lastMessage || '暂无消息' }}</div>
            </div>
            <span v-if="c.unread" class="bg-red-500 text-white text-xs rounded-full min-w-[18px] h-[18px] px-1 flex items-center justify-center flex-shrink-0">
              {{ c.unread > 99 ? '99+' : c.unread }}
            </span>
          </button>
        </div>

        <!-- 消息流 -->
        <div v-else ref="msgScroll" class="flex-1 overflow-y-auto px-3 py-3 bg-gray-50 space-y-3">
          <div v-if="im.loadingMessages" class="text-center text-gray-400 text-xs py-4">加载中…</div>
          <div v-for="(m, i) in im.messages" :key="m.id || ('l' + i)" class="flex" :class="isMine(m) ? 'justify-end' : 'justify-start'">
            <div class="max-w-[75%]">
              <!-- 文本 -->
              <div
                v-if="m.type === 'text'"
                class="px-3 py-2 rounded-2xl text-sm break-words"
                :class="isMine(m) ? 'bg-primary text-white rounded-br-sm' : 'bg-white text-gray-800 rounded-bl-sm shadow-sm'"
              >{{ m.content }}</div>

              <!-- 图片 -->
              <a v-else-if="m.type === 'image'" :href="m.content" target="_blank" class="block">
                <img :src="m.content" class="max-w-[180px] rounded-lg border border-gray-100" />
              </a>

              <!-- 商品卡片 -->
              <div v-else-if="m.type === 'product_card'" class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 w-[220px]">
                <div class="text-[11px] text-gray-400 mb-1">商品咨询</div>
                <div class="flex gap-2">
                  <img :src="m.extra?.image" class="w-14 h-14 rounded object-cover bg-gray-100 flex-shrink-0" />
                  <div class="min-w-0">
                    <div class="text-xs text-gray-800 line-clamp-2">{{ m.extra?.name }}</div>
                    <div class="text-primary text-sm font-medium mt-1">¥{{ fmtPrice(m.extra?.price) }}</div>
                  </div>
                </div>
              </div>

              <!-- 订单卡片 -->
              <div v-else-if="m.type === 'order_card'" class="bg-white rounded-lg shadow-sm border border-gray-100 p-2 w-[220px]">
                <div class="text-[11px] text-gray-400 mb-1">订单咨询 · {{ m.extra?.status }}</div>
                <div class="flex gap-2">
                  <img v-if="m.extra?.image" :src="m.extra?.image" class="w-14 h-14 rounded object-cover bg-gray-100 flex-shrink-0" />
                  <div class="min-w-0">
                    <div class="text-xs text-gray-800 line-clamp-2">{{ m.extra?.productName || '订单商品' }}</div>
                    <div class="text-[11px] text-gray-400 mt-1">订单号 {{ m.extra?.orderNo }}</div>
                  </div>
                </div>
              </div>

              <!-- 时间 + 已读回执 -->
              <div class="text-[10px] text-gray-400 mt-0.5" :class="isMine(m) ? 'text-right' : 'text-left'">
                {{ shortTime(m.createdAt) }}
                <span v-if="isMine(m) && m.read" class="text-primary ml-1">已读</span>
                <span v-else-if="isMine(m)" class="text-gray-400 ml-1">未读</span>
              </div>
            </div>
          </div>

         <!-- 会话超时提示：空闲 5 分钟自动断开，发消息会自动重连 -->
          <div v-if="idleClosed" class="text-center py-1">
            <span class="text-[11px] text-gray-500 bg-gray-100 rounded-full px-3 py-1">会话已超时结束，发送消息将自动重新连接</span>
          </div>

          <!-- 正在输入 -->
          <div v-if="im.peerTyping" class="flex justify-start">
            <div class="bg-white text-gray-400 text-xs px-3 py-2 rounded-2xl rounded-bl-sm shadow-sm">对方正在输入…</div>
          </div>
        </div>

        <!-- 咨询卡片发送确认横幅：进入会话不自动发送，由用户确认 -->
        <div v-if="im.activeId && im.pendingCard" class="border-t border-gray-100 bg-amber-50 px-3 py-2 flex-shrink-0">
          <div class="text-[11px] text-gray-500 mb-1.5">
            是否将以下{{ im.pendingCard.type === 'order_card' ? '订单' : '商品' }}信息发送给客服？
          </div>
          <div class="flex items-center gap-2">
            <img
              v-if="im.pendingCard.extra?.image"
              :src="im.pendingCard.extra.image"
              class="w-10 h-10 rounded object-cover bg-gray-100 flex-shrink-0"
            />
            <div class="flex-1 min-w-0">
              <div class="text-xs text-gray-800 truncate">
                {{ im.pendingCard.extra?.name || im.pendingCard.extra?.productName || '咨询信息' }}
              </div>
              <div class="text-[11px] text-gray-400 truncate">
                <template v-if="im.pendingCard.type === 'order_card'">订单号 {{ im.pendingCard.extra?.orderNo }}</template>
                <template v-else>¥{{ fmtPrice(im.pendingCard.extra?.price) }}</template>
              </div>
            </div>
            <button class="text-xs text-gray-500 px-2 py-1 rounded hover:bg-gray-100" @click="im.cancelPendingCard()">取消</button>
            <button class="text-xs text-white bg-primary px-3 py-1 rounded hover:bg-primary-600" @click="im.confirmSendCard()">发送</button>
          </div>
        </div>

        <!-- 输入区（淘宝式：工具栏在上，输入框居中，发送按钮右下） -->
        <div v-if="im.activeId" class="relative border-t border-gray-100 flex-shrink-0 bg-white">
          <!-- 表情选择器面板 -->
          <div v-if="showEmoji" ref="emojiPanel" class="absolute bottom-full left-2 mb-2 z-10">
            <ClientOnly>
              <EmojiPicker :native="true" :hide-search="false" :disable-skin-tones="true" @select="onSelectEmoji" />
            </ClientOnly>
          </div>
          <!-- 工具栏 -->
          <div class="flex items-center gap-1 px-2 pt-1.5">
            <button
              ref="emojiBtn"
              class="p-1.5 text-gray-500 hover:text-primary rounded"
              :class="showEmoji ? 'text-primary' : ''"
              aria-label="表情"
              @click="toggleEmoji"
            >
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-.464 5.535a1 1 0 10-1.415-1.414 3 3 0 01-4.242 0 1 1 0 00-1.415 1.414 5 5 0 007.072 0z" clip-rule="evenodd" /></svg>
            </button>
            <label class="p-1.5 text-gray-500 hover:text-primary cursor-pointer rounded" :class="uploading ? 'opacity-50 pointer-events-none' : ''" aria-label="图片">
              <svg class="w-5 h-5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M1 5.25A2.25 2.25 0 013.25 3h13.5A2.25 2.25 0 0119 5.25v9.5A2.25 2.25 0 0116.75 17H3.25A2.25 2.25 0 011 14.75v-9.5zm1.5 5.81v3.69c0 .414.336.75.75.75h13.5a.75.75 0 00.75-.75v-2.69l-2.22-2.219a.75.75 0 00-1.06 0l-1.91 1.909.47.47a.75.75 0 11-1.06 1.06L6.53 8.091a.75.75 0 00-1.06 0l-2.97 2.97zM12 7a1 1 0 11-2 0 1 1 0 012 0z" clip-rule="evenodd" /></svg>
              <input type="file" accept="image/*" class="hidden" :disabled="uploading" @change="onPickImage" />
            </label>
          </div>
          <!-- 文本输入：Enter 发送，Shift+Enter 换行 -->
          <textarea
            v-model="draft"
            rows="3"
            maxlength="500"
            placeholder="请输入消息，按 Enter 发送 / Shift+Enter 换行"
            class="w-full resize-none px-3 py-1 text-sm leading-relaxed outline-none bg-transparent text-gray-800 placeholder-gray-400"
            @input="im.notifyTyping()"
            @keydown.enter.exact.prevent="send"
          />
          <!-- 底部操作条：字数统计 + 发送按钮 -->
          <div class="flex items-center justify-end gap-3 px-3 pb-2">
            <span class="text-xs text-gray-400">{{ draft.length }}/500</span>
            <button
              class="h-8 px-5 rounded-full bg-primary text-white text-sm font-medium disabled:opacity-40"
              :disabled="!draft.trim()"
              @click="send"
            >发送</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import EmojiPicker from 'vue3-emoji-picker'
import 'vue3-emoji-picker/css'
import { useImStore } from '~/stores/im'
import { useImSocket } from '~/composables/useImSocket'
import { upload } from '~/utils/request'

const im = useImStore()
const { connected, idleClosed } = useImSocket()

// 表情选择器：选中后把 Unicode 字符追加到输入框，仍作为 text 消息发送
const showEmoji = ref(false)
const emojiPanel = ref<HTMLElement | null>(null)
const emojiBtn = ref<HTMLElement | null>(null)
function toggleEmoji() {
  showEmoji.value = !showEmoji.value
}
function onSelectEmoji(emoji: { i: string }) {
  draft.value += emoji.i
}
// 点击面板与按钮之外关闭
onClickOutside(emojiPanel, () => { showEmoji.value = false }, { ignore: [emojiBtn] })

const draft = ref('')
const uploading = ref(false)
const msgScroll = ref<HTMLElement | null>(null)

function isMine(m: any) {
  return m.senderRole === 'user'
}

function backToList() {
  im.activeId = null
  im.loadConversations()
}

function send() {
  const text = draft.value.trim()
  if (!text) return
  im.sendText(text)
  draft.value = ''
}

async function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = await upload<string>('/file/upload?folder=im', formData)
    im.sendImage(url)
  } catch { /* ignore */ } finally {
    uploading.value = false
    input.value = ''
  }
}

function fmtPrice(p: any): string {
  const n = Number(p)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

function shortTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t)
  if (isNaN(d.getTime())) return ''
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}`
}

// 滚动到底部：nextTick 等 DOM 更新，再用 rAF 兜底图片等异步高度
function scrollToBottom() {
  nextTick(() => {
    const el = msgScroll.value
    if (el) el.scrollTop = el.scrollHeight
    requestAnimationFrame(() => {
      const e2 = msgScroll.value
      if (e2) e2.scrollTop = e2.scrollHeight
    })
  })
}

// 新消息到达时滚动到底部
watch(() => im.messages.length, scrollToBottom)
watch(() => im.peerTyping, scrollToBottom)
// 会话加载完成后滚动到底部：切换会话/重新打开时消息条数可能不变，仅靠 length 监听会漏触发导致停留在顶部
watch(() => im.loadingMessages, (loading) => { if (!loading) scrollToBottom() })
// 会话超时横幅出现时滚动到底部，避免被输入区遮挡
watch(idleClosed, (closed) => { if (closed) scrollToBottom() })
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-up-enter-active, .slide-up-leave-active { transition: transform .25s ease, opacity .25s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(20px); opacity: 0; }
</style>
