<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 顶部标题栏 -->
    <div class="bg-gradient-to-r from-red-500 to-orange-500 py-6">
      <div class="max-w-5xl mx-auto px-4">
        <h1 class="text-2xl font-bold text-white flex items-center gap-2">
          <span>⚡</span> 限时秒杀
        </h1>
        <p class="text-white/80 text-sm mt-1">超值好物，限时限量抢购</p>
      </div>
    </div>

    <!-- 秒杀商品列表 -->
    <div class="max-w-5xl mx-auto px-4 py-6">
      <!-- 加载中 -->
      <div v-if="loading" class="flex items-center justify-center py-20 text-gray-400">
        <span class="animate-spin mr-2">⟳</span> 加载中...
      </div>

      <!-- 空状态 -->
      <div v-else-if="!cards.length" class="text-center py-20 text-gray-400">
        <p class="text-5xl mb-4">⚡</p>
        <p class="text-lg">暂无秒杀活动</p>
        <p class="text-sm mt-2">请关注后续活动安排</p>
      </div>

      <!-- 商品卡片列表（一个活动下可含多个商品） -->
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <div
          v-for="card in cards"
          :key="card.itemId"
          class="bg-white rounded-lg overflow-hidden shadow-sm hover:shadow-md transition-shadow"
        >
          <!-- 商品图片 -->
          <div class="relative aspect-square bg-gray-100 overflow-hidden">
            <img
              v-if="card.productImage"
              :src="card.productImage"
              :alt="card.productName"
              class="w-full h-full object-cover"
              @error="($event.target as HTMLImageElement).src = ''"
            />
            <div v-else class="w-full h-full flex items-center justify-center text-gray-300 text-4xl">📦</div>
            <!-- 状态标签 -->
            <span
              class="absolute top-2 left-2 px-2 py-0.5 rounded text-xs font-medium text-white"
              :class="statusClass(card)"
            >
              {{ statusText(card) }}
            </span>
            <!-- 活动名角标 -->
            <span class="absolute top-2 right-2 px-2 py-0.5 rounded text-xs font-medium bg-black/50 text-white max-w-[60%] truncate">
              {{ card.activityName }}
            </span>
          </div>

          <!-- 商品信息 -->
          <div class="p-3">
            <h3 class="font-medium text-gray-800 line-clamp-2 min-h-[2.5rem]">
              {{ card.productName }}
            </h3>
            <p v-if="card.skuName" class="text-xs text-gray-400 mt-0.5 truncate">规格：{{ card.skuName }}</p>
            <!-- 价格区 -->
            <div class="flex items-baseline gap-2 mt-2">
              <span class="text-red-500 text-xl font-bold">¥{{ card.seckillPrice }}</span>
              <span v-if="card.originalPrice" class="text-gray-400 text-sm line-through">
                ¥{{ card.originalPrice }}
              </span>
              <span
                v-if="card.originalPrice && card.seckillPrice"
                class="bg-red-50 text-red-500 text-xs px-1.5 py-0.5 rounded"
              >
                {{ discount(card) }}折
              </span>
            </div>

            <!-- 库存进度条 -->
            <div class="mt-3">
              <div class="flex justify-between text-xs text-gray-500 mb-1">
                <span>已售 {{ soldCount(card) }}</span>
                <span>总库存 {{ card.totalStock }}</span>
              </div>
              <div class="h-2 bg-gray-100 rounded-full overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-500"
                  :class="stockColor(card)"
                  :style="{ width: `${soldPercent(card)}%` }"
                />
              </div>
            </div>

            <!-- 倒计时 -->
            <div v-if="card.status === 0 || card.status === 1" class="mt-3 text-center">
              <div class="text-xs text-gray-500 mb-1">
                {{ card.status === 0 ? '距开始' : '距结束' }}
              </div>
              <div class="flex justify-center gap-1 text-sm font-mono">
                <span class="bg-gray-800 text-white px-1.5 py-0.5 rounded">{{ pad(countdown(card).hours) }}</span>
                <span class="text-gray-600">:</span>
                <span class="bg-gray-800 text-white px-1.5 py-0.5 rounded">{{ pad(countdown(card).minutes) }}</span>
                <span class="text-gray-600">:</span>
                <span class="bg-gray-800 text-white px-1.5 py-0.5 rounded">{{ pad(countdown(card).seconds) }}</span>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="mt-3">
              <button
                v-if="card.status === 1 && card.availableStock > 0"
                class="w-full bg-red-500 hover:bg-red-600 text-white font-medium py-2 rounded transition-colors text-sm"
                :disabled="card._seckilling"
                @click="handleSeckill(card)"
              >
                {{ card._seckilling ? '抢购中...' : '立即抢购' }}
              </button>
              <button
                v-else-if="card.status === 1 && card.availableStock <= 0"
                class="w-full bg-gray-300 text-gray-500 font-medium py-2 rounded text-sm cursor-not-allowed"
                disabled
              >
                已售罄
              </button>
              <button
                v-else-if="card.status === 0"
                class="w-full bg-orange-100 text-orange-600 font-medium py-2 rounded text-sm cursor-not-allowed"
                disabled
              >
                即将开始
              </button>
              <button
                v-else
                class="w-full bg-gray-100 text-gray-400 font-medium py-2 rounded text-sm cursor-not-allowed"
                disabled
              >
                已结束
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toast.visible"
        :class="['fixed top-20 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2',
          toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white']">
        {{ toast.message }}
      </div>
    </Transition>

    <!-- 抢购确认弹框（选择收货地址） -->
    <Teleport to="body">
      <Transition name="toast">
        <div v-if="confirmCard" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="closeConfirm" />
          <div class="relative bg-white rounded-xl shadow-xl w-full max-w-md p-6 max-h-[85vh] flex flex-col">
            <h3 class="text-lg font-bold text-gray-800 mb-4">确认抢购</h3>

            <!-- 商品信息 -->
            <div class="flex items-center gap-3 pb-4 border-b">
              <img
                v-if="confirmCard.productImage"
                :src="confirmCard.productImage"
                :alt="confirmCard.productName"
                class="w-16 h-16 object-cover rounded flex-shrink-0"
              />
              <div v-else class="w-16 h-16 rounded bg-gray-100 flex items-center justify-center text-gray-300 text-2xl flex-shrink-0">📦</div>
              <div class="flex-1 min-w-0">
                <p class="text-sm text-gray-800 line-clamp-2">{{ confirmCard.productName }}</p>
                <p v-if="confirmCard.skuName" class="text-xs text-gray-400 mt-0.5 truncate">规格：{{ confirmCard.skuName }}</p>
                <p class="text-red-500 font-bold mt-1">¥{{ confirmCard.seckillPrice }}</p>
              </div>
            </div>

            <!-- 收货地址 -->
            <div class="flex-1 overflow-y-auto py-4">
              <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-medium text-gray-800">收货地址</span>
                <button class="text-xs text-primary hover:text-primary-600" @click="openAddressForm(null)">+ 新增地址</button>
              </div>
              <div v-if="addresses.length" class="space-y-2">
                <div
                  v-for="addr in addresses"
                  :key="addr.id"
                  :class="[
                    'border rounded-lg p-3 cursor-pointer transition-all',
                    confirmAddressId === addr.id ? 'border-red-500 bg-red-50' : 'border-gray-200 hover:border-gray-300'
                  ]"
                  @click="confirmAddressId = addr.id"
                >
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-2">
                      <span class="font-medium text-sm">{{ addr.receiverName }}</span>
                      <span class="text-gray-500 text-xs">{{ addr.receiverPhone }}</span>
                      <span v-if="addr.isDefault === 1" class="text-xs bg-red-500 text-white px-1.5 py-0.5 rounded">默认</span>
                    </div>
                    <button class="text-xs text-gray-400 hover:text-primary" @click.stop="openAddressForm(addr)">编辑</button>
                  </div>
                  <p class="text-xs text-gray-500 mt-1">{{ formatAddress(addr) }}</p>
                </div>
              </div>
              <div v-else class="text-center py-6 text-gray-400 text-sm">
                <p>暂无收货地址</p>
                <button class="mt-2 text-sm text-primary hover:text-primary-600" @click="openAddressForm(null)">去添加</button>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="flex gap-3 pt-3 border-t">
              <button class="flex-1 h-10 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50" @click="closeConfirm">取消</button>
              <button
                class="flex-1 h-10 bg-red-500 text-white rounded-lg text-sm hover:bg-red-600 disabled:opacity-50 disabled:cursor-not-allowed"
                :disabled="!confirmAddressId || confirmCard._seckilling"
                @click="confirmSeckill"
              >
                {{ confirmCard._seckilling ? '抢购中...' : '确认抢购' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 新增/编辑地址弹框 -->
    <Teleport to="body">
      <Transition name="toast">
        <div v-if="showAddressForm" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="showAddressForm = false" />
          <div class="relative bg-white rounded-xl shadow-xl w-full max-w-md p-6">
            <h3 class="text-lg font-bold text-gray-800 mb-4">{{ editingAddr ? '编辑地址' : '新增地址' }}</h3>
            <div class="space-y-3">
              <div>
                <label class="block text-sm text-gray-600 mb-1">收件人</label>
                <input v-model="addrForm.receiverName" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="请输入收件人姓名" />
              </div>
              <div>
                <label class="block text-sm text-gray-600 mb-1">手机号</label>
                <input v-model="addrForm.receiverPhone" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="请输入手机号" />
              </div>
              <div class="grid grid-cols-3 gap-2">
                <div>
                  <label class="block text-sm text-gray-600 mb-1">省</label>
                  <input v-model="addrForm.province" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="省" />
                </div>
                <div>
                  <label class="block text-sm text-gray-600 mb-1">市</label>
                  <input v-model="addrForm.city" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="市" />
                </div>
                <div>
                  <label class="block text-sm text-gray-600 mb-1">区</label>
                  <input v-model="addrForm.district" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="区" />
                </div>
              </div>
              <div>
                <label class="block text-sm text-gray-600 mb-1">详细地址</label>
                <input v-model="addrForm.detailAddress" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="街道、楼牌号等" />
              </div>
              <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                <input v-model="addrIsDefault" type="checkbox" class="accent-primary" />
                设为默认地址
              </label>
            </div>
            <div class="flex gap-3 mt-6">
              <button class="flex-1 h-10 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50" @click="showAddressForm = false">取消</button>
              <button class="flex-1 h-10 bg-red-500 text-white rounded-lg text-sm hover:bg-red-600" @click="saveAddress">保存</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { get, post, put } from '~/utils/request'
import { useUserStore } from '~/stores/user'

const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true; toast.message = message; toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

/** 活动商品卡片（活动 × 商品条目 拍平） */
interface SeckillCard {
  activityId: number
  activityName: string
  status: number
  startTime: string
  endTime: string
  itemId: number
  productId: number
  skuId: number
  seckillPrice: number
  totalStock: number
  availableStock: number
  productName?: string
  productImage?: string
  skuName?: string
  originalPrice?: number
  _seckilling?: boolean
  _endTimeMs?: number
  _startTimeMs?: number
}

const userStore = useUserStore()
const cards = ref<SeckillCard[]>([])
const loading = ref(true)
const now = ref(Date.now())

// 收货地址 + 抢购确认弹框
const addresses = ref<any[]>([])
const confirmCard = ref<SeckillCard | null>(null)
const confirmAddressId = ref<number | null>(null)

let timer: ReturnType<typeof setInterval> | null = null

const formatAddress = (addr: any): string =>
  `${addr.province || ''}${addr.city || ''}${addr.district || ''}${addr.detailAddress || ''}`

const fetchAddresses = async () => {
  try {
    const data = await get<any[]>('/user/address/list')
    addresses.value = data || []
  } catch { /* ignore */ }
}

// 地址新增/编辑弹框
const showAddressForm = ref(false)
const editingAddr = ref<any>(null)
const addrIsDefault = ref(false)
const addrForm = ref({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
})

const openAddressForm = (addr: any) => {
  editingAddr.value = addr
  if (addr) {
    addrForm.value = {
      receiverName: addr.receiverName || '',
      receiverPhone: addr.receiverPhone || '',
      province: addr.province || '',
      city: addr.city || '',
      district: addr.district || '',
      detailAddress: addr.detailAddress || '',
    }
    addrIsDefault.value = addr.isDefault === 1
  } else {
    addrForm.value = { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '' }
    addrIsDefault.value = false
  }
  showAddressForm.value = true
}

const saveAddress = async () => {
  if (!addrForm.value.receiverName || !addrForm.value.receiverPhone || !addrForm.value.detailAddress) {
    showToast('请填写完整信息', 'error')
    return
  }
  try {
    const data = { ...addrForm.value, isDefault: addrIsDefault.value ? 1 : 0 }
    if (editingAddr.value) {
      await put('/user/address', { id: editingAddr.value.id, ...data })
      showAddressForm.value = false
      await fetchAddresses()
    } else {
      const oldIds = addresses.value.map(a => a.id)
      await post('/user/address', data)
      showAddressForm.value = false
      await fetchAddresses()
      // 新增后自动选中新地址
      const added = addresses.value.find(a => !oldIds.includes(a.id))
      if (added) confirmAddressId.value = added.id
    }
    showToast('保存成功')
  } catch (e: any) {
    showToast(e?.message || '保存失败', 'error')
  }
}

const fetchActivities = async () => {
  loading.value = true
  try {
    const data = await get<any[]>('/seckill/list/detail')
    // 一个活动下含多个商品条目，拍平为卡片列表
    const list: SeckillCard[] = []
    for (const act of data || []) {
      for (const item of act.items || []) {
        list.push({
          activityId: act.id,
          activityName: act.name,
          status: act.status,
          startTime: act.startTime,
          endTime: act.endTime,
          itemId: item.itemId,
          productId: item.productId,
          skuId: item.skuId,
          seckillPrice: item.seckillPrice,
          totalStock: item.totalStock,
          availableStock: item.availableStock,
          productName: item.productName,
          productImage: item.productImage,
          skuName: item.skuName,
          originalPrice: item.originalPrice,
          _seckilling: false,
          _startTimeMs: act.startTime ? new Date(act.startTime).getTime() : 0,
          _endTimeMs: act.endTime ? new Date(act.endTime).getTime() : 0,
        })
      }
    }
    cards.value = list
  } catch (e) {
    console.error('获取秒杀活动失败:', e)
  } finally {
    loading.value = false
  }
}

const countdown = (card: SeckillCard) => {
  const target = card.status === 0 ? (card._startTimeMs || 0) : (card._endTimeMs || 0)
  const diff = Math.max(0, target - now.value)
  const totalSec = Math.floor(diff / 1000)
  return {
    hours: Math.floor(totalSec / 3600),
    minutes: Math.floor((totalSec % 3600) / 60),
    seconds: totalSec % 60,
  }
}

const pad = (n: number) => String(n).padStart(2, '0')

const soldCount = (card: SeckillCard) => card.totalStock - card.availableStock
const soldPercent = (card: SeckillCard) =>
  card.totalStock ? Math.round(((card.totalStock - card.availableStock) / card.totalStock) * 100) : 0

const stockColor = (card: SeckillCard) => {
  const p = soldPercent(card)
  if (p >= 80) return 'bg-red-500'
  if (p >= 50) return 'bg-orange-400'
  return 'bg-green-400'
}

const discount = (card: SeckillCard) => {
  if (!card.originalPrice || !card.seckillPrice) return '-'
  return (card.seckillPrice / card.originalPrice * 10).toFixed(1)
}

const statusText = (card: SeckillCard) =>
  ({ 0: '即将开始', 1: '抢购中', 2: '已结束' } as Record<number, string>)[card.status] || '未知'

const statusClass = (card: SeckillCard) =>
  ({ 0: 'bg-orange-400', 1: 'bg-red-500', 2: 'bg-gray-400' } as Record<number, string>)[card.status] || 'bg-gray-400'

const handleSeckill = async (card: SeckillCard) => {
  if (!userStore.isLoggedIn) {
    navigateTo('/login')
    return
  }
  // 确保地址列表已加载，开启确认弹框选择收货地址
  if (!addresses.value.length) await fetchAddresses()
  const defaultAddr = addresses.value.find(a => a.isDefault === 1)
  confirmAddressId.value = defaultAddr ? defaultAddr.id : (addresses.value[0]?.id ?? null)
  confirmCard.value = card
}

const closeConfirm = () => {
  if (confirmCard.value?._seckilling) return
  confirmCard.value = null
}

const confirmSeckill = async () => {
  const card = confirmCard.value
  if (!card) return
  if (!confirmAddressId.value) {
    showToast('请选择收货地址', 'error')
    return
  }
  if (card._seckilling) return
  card._seckilling = true
  try {
    await post(`/seckill/seckill/${card.activityId}/${card.itemId}?addressId=${confirmAddressId.value}`)
    confirmCard.value = null
    showToast('秒杀成功！请尽快前往订单页面支付')
    setTimeout(() => navigateTo('/user/orders'), 1500)
  } catch (e: any) {
    showToast(e.message || '秒杀失败', 'error')
  } finally {
    card._seckilling = false
    fetchActivities()
  }
}

onMounted(() => {
  fetchActivities()
  if (userStore.isLoggedIn) fetchAddresses()
  timer = setInterval(() => { now.value = Date.now() }, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
