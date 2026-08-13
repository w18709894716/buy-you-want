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

          <!-- FAQ 引导兜底：无锚点（全新会话/历史太远）时作为消息流第一条渲染，问答消息在其下方推进 -->
          <div v-if="showFaqGuide && !guideAnchorId && lastServiceEndedIndex < 0" class="flex items-start gap-2">
            <div class="w-8 h-8 rounded-full bg-primary text-white text-sm flex items-center justify-center flex-shrink-0">智</div>
            <div class="max-w-[75%]">
              <div class="text-[11px] text-gray-400 mb-1">智能客服</div>
              <div class="bg-white rounded-2xl rounded-bl-sm shadow-sm px-3 py-2">
                <div class="text-sm text-gray-800 mb-2">
                  <template v-if="inServiceTime">智能客服为您服务，请选择您要咨询的问题：</template>
                  <template v-else>{{ offHoursTip || '智能客服为您服务，请选择您要咨询的问题：' }}</template>
                </div>
                <div class="flex flex-col gap-1.5">
                  <button
                    v-for="faq in faqList"
                    :key="faq.id"
                    class="text-left text-xs text-primary bg-primary/5 hover:bg-primary/10 rounded-lg px-3 py-1.5 transition-colors"
                    @click="im.sendFaq(faq.question)"
                  >{{ faq.question }}</button>
                </div>
              </div>
            </div>
          </div>

          <template v-for="(m, i) in im.messages" :key="m.id || ('l' + i)">
            <!-- 系统提示（如客服接入）：居中灰色小字，不用气泡 -->
            <div v-if="m.systemType" class="text-center">
              <span class="text-[11px] text-gray-400">{{ m.content }}</span>
            </div>
            <!-- 评价入口：跟随最近一次“服务结束”消息渲染，随消息流滚动（不固定钉在底部） -->
            <div v-if="showSatisfactionEntry && m.systemType === 'service-ended' && i === lastServiceEndedIndex" class="text-center py-2">
              <button class="text-xs text-primary underline hover:text-primary-600" @click="openSatisfaction">评价本次服务</button>
            </div>
            <!-- FAQ 引导：优先锚定“重新引导”位置（新一轮咨询时的最新消息后）；否则锚定最后一条服务结束消息后（评价入口下方），随消息流一起滚动 -->
            <div v-if="showFaqGuide && ((guideAnchorId && m.id === guideAnchorId) || (!guideAnchorId && m.systemType === 'service-ended' && i === lastServiceEndedIndex))" class="flex items-start gap-2">
              <div class="w-8 h-8 rounded-full bg-primary text-white text-sm flex items-center justify-center flex-shrink-0">智</div>
              <div class="max-w-[75%]">
                <div class="text-[11px] text-gray-400 mb-1">智能客服</div>
                <div class="bg-white rounded-2xl rounded-bl-sm shadow-sm px-3 py-2">
                  <div class="text-sm text-gray-800 mb-2">
                    <template v-if="inServiceTime">智能客服为您服务，请选择您要咨询的问题：</template>
                    <template v-else>{{ offHoursTip || '智能客服为您服务，请选择您要咨询的问题：' }}</template>
                  </div>
                  <div class="flex flex-col gap-1.5">
                    <button
                      v-for="faq in faqList"
                      :key="faq.id"
                      class="text-left text-xs text-primary bg-primary/5 hover:bg-primary/10 rounded-lg px-3 py-1.5 transition-colors"
                      @click="im.sendFaq(faq.question)"
                    >{{ faq.question }}</button>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!m.systemType" class="flex items-start gap-2" :class="isMine(m) ? 'justify-end' : 'justify-start'" :data-msg-id="m.id" @contextmenu.prevent="showCtxMenu(m, $event)">
              <!-- 左侧：客服头像（名字首字） -->
              <div v-if="!isMine(m)" class="w-8 h-8 rounded-full bg-primary text-white text-sm flex items-center justify-center flex-shrink-0">
                {{ avatarText(m) }}
              </div>
              <div class="max-w-[75%]">
                <!-- 客服名字：仅对方消息显示，自己的消息只显示头像 -->
                <div v-if="!isMine(m)" class="text-[11px] text-gray-400 mb-1 text-left">{{ msgName(m) }}</div>
                <!-- 引用条：点击定位到被引用消息 -->
                <div
                  v-if="m.quoteId"
                  class="cursor-pointer bg-black/5 rounded-lg px-2 py-1 mb-1 text-xs text-gray-500 flex items-center gap-1 min-w-0"
                  @click="scrollToMessage(m.quoteId)"
                >
                  <span class="flex-shrink-0">{{ m.quoteSenderName || '消息' }}：</span>
                  <span v-if="isQuoteRecalled(m)" class="truncate text-gray-400">消息已撤回</span>
                  <span v-else class="truncate">{{ m.quoteContent }}</span>
                </div>
                <!-- 撤回态：统一显示灰色撤回提示，不渲染原内容 -->
                <div v-if="m.recalled" class="recalled-tip w-fit text-xs text-gray-400 bg-gray-100 rounded-lg px-3 py-1.5">{{ m.content || '消息已撤回' }}</div>
                <!-- 文本 -->
                <div
                  v-else-if="m.type === 'text'"
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
              <!-- 右侧：自己头像 -->
              <div v-if="isMine(m)" class="w-8 h-8 rounded-full bg-gray-300 text-white text-sm flex items-center justify-center flex-shrink-0">
                {{ avatarText(m) }}
              </div>
            </div>
          </template>

          <!-- 评价入口兜底：服务结束消息不在当前加载范围（历史太远）时，显示在消息流末尾 -->
          <div v-if="showSatisfactionEntry && lastServiceEndedIndex < 0" class="text-center py-2">
            <button class="text-xs text-primary underline hover:text-primary-600" @click="openSatisfaction">评价本次服务</button>
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
          <!-- 引用输入条：可取消 -->
          <div v-if="quoteTarget" class="flex items-center gap-2 px-3 py-1.5 bg-amber-50 border-b border-gray-100">
            <span class="text-xs text-gray-600 truncate flex-1">引用 {{ quoteTarget.name }}：{{ quoteTarget.content }}</span>
            <button class="text-gray-400 hover:text-gray-600 flex-shrink-0" aria-label="取消引用" @click="quoteTarget = null">
              <svg class="w-3.5 h-3.5" viewBox="0 0 20 20" fill="currentColor"><path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" /></svg>
            </button>
          </div>
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

    <!-- 操作错误提示（如撤回超时/非发送者撤回） -->
    <Teleport to="body">
      <div
        v-if="errorTip.visible"
        class="fixed top-20 left-1/2 -translate-x-1/2 z-[70] px-5 py-2.5 rounded-lg shadow-lg text-sm bg-red-500 text-white"
      >
        <span>{{ errorTip.message }}</span>
      </div>
    </Teleport>

    <!-- 右键菜单：撤回/引用 -->
    <Teleport to="body">
      <div
        v-if="ctxMenu.visible"
        ref="ctxMenuRef"
        class="fixed z-[90] bg-white rounded-lg shadow-xl border border-gray-200 py-1 min-w-[110px]"
        :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
        @click.stop
      >
        <button
          v-if="ctxMenu.canRecall"
          class="w-full px-4 py-1.5 text-sm text-left text-gray-700 hover:bg-gray-50 hover:text-red-500"
          @click="doRecall(ctxMenu.message)"
        >撤回</button>
        <button
          v-if="ctxMenu.canQuote"
          class="w-full px-4 py-1.5 text-sm text-left text-gray-700 hover:bg-gray-50"
          @click="doQuote(ctxMenu.message)"
        >引用</button>
      </div>
    </Teleport>

    <!-- 满意度评价弹窗 -->
    <Teleport to="body">
      <div v-if="satVisible" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/30" @click.self="satVisible = false">
        <div class="bg-white rounded-xl shadow-2xl w-[320px] p-5">
          <h3 class="text-sm font-medium text-gray-800 mb-4">评价本次服务</h3>
          <!-- 星星评分 -->
          <div class="flex justify-center gap-1 mb-4">
            <button v-for="i in 5" :key="i" class="text-2xl" :class="i <= satRating ? 'text-yellow-400' : 'text-gray-300'" @click="satRating = i">★</button>
          </div>
          <!-- 评价标签 -->
          <div class="flex flex-wrap gap-2 mb-3">
            <button v-for="tag in satTags" :key="tag" class="text-xs px-3 py-1 rounded-full border" :class="satSelectedTags.includes(tag) ? 'bg-primary text-white border-primary' : 'border-gray-200 text-gray-500 hover:border-gray-300'" @click="toggleSatTag(tag)">{{ tag }}</button>
          </div>
          <!-- 留言 -->
          <textarea v-model="satComment" rows="3" maxlength="500" placeholder="说说您的感受（选填）" class="w-full resize-none border border-gray-200 rounded-lg px-3 py-2 text-sm outline-none focus:border-primary" />
          <!-- 按钮 -->
          <div class="flex justify-end gap-2 mt-4">
            <button class="text-xs px-4 py-1.5 rounded-full border border-gray-200 text-gray-500 hover:bg-gray-50" @click="satVisible = false">取消</button>
            <button class="text-xs px-4 py-1.5 rounded-full bg-primary text-white disabled:opacity-40" :disabled="satRating === 0" @click="submitSat">提交</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, watch, onUnmounted } from 'vue'
import { onClickOutside } from '@vueuse/core'
import EmojiPicker from 'vue3-emoji-picker'
import 'vue3-emoji-picker/css'
import { useImStore } from '~/stores/im'
import { useImSocket } from '~/composables/useImSocket'
import { upload, post, get } from '~/utils/request'

const im = useImStore()
const { connected } = useImSocket()

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

// 引用目标（输入框上方显示，发送时随帧携带 quoteId）
const quoteTarget = ref<{ id: string; name: string; content: string } | null>(null)

// 引用摘要：图片/卡片显示占位文案，与后端 summarize 保持一致
function quoteSummary(m: any): string {
  if (m.recalled) return '消息已撤回'
  switch (m.type) {
    case 'image': return '[图片]'
    case 'product_card': return '[商品]'
    case 'order_card': return '[订单]'
    default: return m.content || ''
  }
}

// 设置引用目标：引用对方或自己的消息（已撤回的消息不支持引用）
function startQuote(m: any) {
  if (!m.id || m.recalled) return
  quoteTarget.value = {
    id: m.id,
    name: isMine(m) ? '我' : msgName(m),
    content: quoteSummary(m),
  }
}

// 被引用消息是否已撤回（从本地消息列表中查找，引用条改为显示"消息已撤回"）
function isQuoteRecalled(m: any): boolean {
  if (!m.quoteId) return false
  const quoted = im.messages.find(msg => msg.id === m.quoteId)
  return quoted?.recalled === true
}

// 仅自己的消息且发送 2 分钟内可撤回（后端强校验，前端仅控制按钮显隐）
function canRecall(m: any): boolean {
  if (!isMine(m) || m.recalled || !m.createdAt) return false
  const d = parseTime(m.createdAt)
  return d != null && Date.now() - d.getTime() < 120_000
}

// 撤回消息：发送 recall 帧，失败经 error 帧返回原因
function recallMessage(m: any) {
  if (!m.id || !im.activeId) return
  const ok = im.recallMessage(m.id)
  if (!ok) showErrorTip('连接已断开，请稍后重试')
}

// 右键菜单状态
const ctxMenu = reactive<{
  visible: boolean
  x: number
  y: number
  message: any
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
function showCtxMenu(m: any, e: MouseEvent) {
  const canDoRecall = canRecall(m)
  const canDoQuote = !!m.id && !m.recalled
  // 无可用操作（如已撤回消息）时不弹菜单，避免空白色长条
  if (!canDoRecall && !canDoQuote) return
  const menuW = 110
  const menuH = 80
  let x = e.clientX
  let y = e.clientY
  // 避免菜单溢出视口
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
function doRecall(m: any) {
  closeCtxMenu()
  recallMessage(m)
}

// 右键菜单：引用
function doQuote(m: any) {
  closeCtxMenu()
  startQuote(m)
}

// 组件卸载时清理全局监听
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
})

// ========== FAQ 智能客服引导 ==========

/** 当前会话可选的 FAQ 快捷问题（打开会话时拉取，仅启用状态） */
const faqList = ref<{ id: number; question: string }[]>([])
/** 服务时间内是否优先智能机器人（robotFirst=true 时展示 FAQ 引导） */
const robotFirst = ref(false)
/** 当前是否在服务时间内（false=非服务时间模式，机器人默认打开） */
const inServiceTime = ref(true)
/** 非服务时间提示语（非服务时间模式时由机器人回复下发，未配置为默认文案） */
const offHoursTip = ref('')
/** 是否展示智能客服引导：店铺有 FAQ 且（非服务时间 或 服务时间内机器人优先）；机器人关闭且服务时间内不展示 */
const showFaqGuide = computed(() => faqList.value.length > 0 && (!inServiceTime.value || robotFirst.value))
/** “重新引导”锚点消息 id：间隔超过阈值再次进线时，引导重新锚定到该消息之后（最新位置，避免翻聊天记录） */
const guideAnchorId = ref<string | null>(null)
/** 重新引导间隔阈值：距最后一条消息超过该时长视为新一轮咨询，引导重新出现在最新位置 */
const GUIDE_REFRESH_GAP = 30 * 60 * 1000

/**
 * 判定是否需要重新引导：无人工接待且距最后一条消息超过阈值时，把引导锚定到当前最新消息之后；
 * 短间隔/人工接待中保持原锚点（最后一条服务结束消息后）
 */
function refreshGuideAnchor() {
  const conv = im.activeConversation
  if (!conv || conv.assigneeId) { guideAnchorId.value = null; return }
  const last = parseTime(conv.lastMessageTime)
  if (!last || Date.now() - last.getTime() < GUIDE_REFRESH_GAP) { guideAnchorId.value = null; return }
  // 锚定当前快照中最后一条真实消息（从后往前找有 id 的；乐观消息无 id 不可作锚点）
  const lastReal = [...im.messages].reverse().find(m => m.id)
  guideAnchorId.value = lastReal?.id || null
}

// 切换会话时拉取该店铺的 FAQ 引导选项（immediate：面板重新打开时 activeId 可能不变，仍需重新拉取）
watch(() => im.activeId, async (id) => {
  faqList.value = []
  robotFirst.value = false
  inServiceTime.value = true
  offHoursTip.value = ''
  guideAnchorId.value = null
  if (!id) return
  const conv = im.conversations.find(c => c.id === id)
  if (!conv?.shopId) return
  try {
    const data = await get<any>('/im/faq/options', { shopId: conv.shopId })
    faqList.value = data?.faqs || []
    robotFirst.value = data?.robotFirst === true
    inServiceTime.value = data?.inServiceTime !== false
    offHoursTip.value = data?.offHoursTip || ''
  } catch {
    faqList.value = []
  }
}, { immediate: true })

// 点击引用条定位到被引用消息（滚动 + 高亮闪烁）
function scrollToMessage(messageId?: string) {
  if (!messageId) return
  const el = document.querySelector(`[data-msg-id="${messageId}"]`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('msg-flash')
  setTimeout(() => el.classList.remove('msg-flash'), 1200)
}

// ========== 满意度评价 ==========

/** 是否展示评价入口 */
const showSatisfactionEntry = ref(false)
/** 消息流中最近一次“服务结束”消息的下标（评价入口跟随其渲染随消息滚动；-1=无） */
const lastServiceEndedIndex = computed(() => {
  for (let i = im.messages.length - 1; i >= 0; i--) {
    if (im.messages[i].systemType === 'service-ended') return i
  }
  return -1
})
/** 评价弹窗可见 */
const satVisible = ref(false)
/** 评分 1-5 */
const satRating = ref(0)
/** 可选标签 */
const satTags = ['响应快', '态度好', '专业解答', '未解决问题', '其他']
/** 已选标签 */
const satSelectedTags = ref<string[]>([])
/** 留言 */
const satComment = ref('')

function toggleSatTag(tag: string) {
  const idx = satSelectedTags.value.indexOf(tag)
  if (idx >= 0) {
    satSelectedTags.value.splice(idx, 1)
  } else {
    satSelectedTags.value.push(tag)
  }
}

/** 打开评价弹窗 */
function openSatisfaction() {
  satRating.value = 0
  satSelectedTags.value = []
  satComment.value = ''
  satVisible.value = true
}

/** 提交评价 */
async function submitSat() {
  if (satRating.value === 0) return
  try {
    await post('/im/satisfaction', {
      conversationId: im.activeId,
      rating: satRating.value,
      tags: satSelectedTags.value.join(','),
      comment: satComment.value,
    })
    satVisible.value = false
    showSatisfactionEntry.value = false
    showErrorTip('评价已提交，感谢您的反馈')
  } catch {
    showErrorTip('评价提交失败，请稍后重试')
  }
}

/** 检查当前会话是否可评价（有人工回复且存在已结束未评价的服务） */
async function checkSatisfaction() {
  const hasHumanReply = im.messages.some(m => m.senderRole === 'merchant' && !m.systemType)
  if (!hasHumanReply) { showSatisfactionEntry.value = false; return }
  try {
    const ratable = await get<boolean>('/im/satisfaction/check', { conversationId: im.activeId })
    showSatisfactionEntry.value = ratable
  } catch {
    showSatisfactionEntry.value = false
  }
}

// 会话切换/消息加载完成时检查评价入口
watch([() => im.activeId, () => im.loadingMessages], ([id, loading]) => {
  if (!id || loading) { showSatisfactionEntry.value = false; return }
  nextTick(() => checkSatisfaction())
})

// 服务超时自动结束（service-ended 系统消息）到达时重新检查评价入口
watch(() => im.messages[im.messages.length - 1]?.systemType, (t) => {
  if (t === 'service-ended' && im.activeId) nextTick(() => checkSatisfaction())
})

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

// 操作错误提示：后端 error 帧（如撤回超时）与本地失败（连接断开）统一展示
const errorTip = reactive({ visible: false, message: '' })
let errorTimer: ReturnType<typeof setTimeout> | null = null
function showErrorTip(message: string) {
  if (errorTimer) clearTimeout(errorTimer)
  errorTip.visible = true
  errorTip.message = message
  errorTimer = setTimeout(() => { errorTip.visible = false }, 2500)
}
// 后端 error 帧到达时经 store 透出，此处消费并清空
watch(() => im.lastError, (msg) => {
  if (!msg) return
  showErrorTip(msg)
  im.clearError()
})

function isMine(m: any) {
  return m.senderRole === 'user'
}

// 消息发送者显示名：客服消息显示客服姓名（后端已填真实姓名，仅对方消息使用）
function msgName(m: any) {
  return m.senderName || '客服'
}

// 头像文字：名字首字；用户用"我"占位
function avatarText(m: any) {
  if (m.senderRole === 'user') return '我'
  return (m.senderName || '客').charAt(0)
}

function backToList() {
  im.activeId = null
  im.loadConversations()
}

function send() {
  const text = draft.value.trim()
  if (!text) return
  im.sendText(text, quoteTarget.value?.id)
  draft.value = ''
  quoteTarget.value = null
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
// 会话加载完成后：先判定是否需要“重新引导”（间隔超阈值时锚定最新消息），再按其位置滚动——
// 引导锚定在服务结束消息后（存量会话，滚底即见）；无锚点且尚无人工接待（全新会话，引导在消息流开头）则滚顶；其余一律滚底看最新消息
watch(() => im.loadingMessages, (loading) => {
  if (loading) return
  refreshGuideAnchor()
  nextTick(() => {
    if (showFaqGuide.value && !guideAnchorId.value && !im.activeConversation?.assigneeId && lastServiceEndedIndex.value < 0) scrollToTop()
    else scrollToBottom()
  })
})
// FAQ 选项异步拉取（可能晚于消息加载完成）：引导此时才出现，按其锚点位置滚动使其可见
watch(faqList, (list) => {
  if (!list.length || !showFaqGuide.value) return
  nextTick(() => {
    if (!guideAnchorId.value && !im.activeConversation?.assigneeId && lastServiceEndedIndex.value < 0) scrollToTop()
    else scrollToBottom()
  })
})

// 滚动到顶部：引导卡片位于消息流顶部，打开会话时需让用户看到
function scrollToTop() {
  nextTick(() => {
    const el = msgScroll.value
    if (el) el.scrollTop = 0
  })
}
</script>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-up-enter-active, .slide-up-leave-active { transition: transform .25s ease, opacity .25s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(20px); opacity: 0; }

/* 引用定位高亮闪烁 */
.msg-flash {
  animation: msg-flash 1.2s ease;
  border-radius: 8px;
}
@keyframes msg-flash {
  0%, 100% { box-shadow: none; }
  30% { box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.4); }
}
</style>
