<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <div class="flex gap-6">
      <!-- 侧边栏 -->
      <aside class="w-52 flex-shrink-0 hidden md:block">
        <div class="bg-white rounded-lg p-4 mb-4">
          <div class="flex items-center gap-3">
            <div class="w-14 h-14 bg-primary-100 rounded-full flex items-center justify-center text-primary text-xl font-bold">
              {{ (userStore.nickname || userStore.username || '?')[0] }}
            </div>
            <div>
              <div class="font-medium text-gray-800">{{ userStore.nickname || userStore.username }}</div>
              <div class="text-xs text-gray-400">{{ userStore.username }}</div>
            </div>
          </div>
        </div>
        <nav class="bg-white rounded-lg overflow-hidden">
          <NuxtLink
            v-for="item in sidebarMenu"
            :key="item.path"
            :to="item.path"
            :class="[
              'flex items-center gap-3 px-4 py-3 text-sm transition-colors',
              $route.path === item.path ? 'bg-primary-50 text-primary border-l-2 border-primary' : 'text-gray-600 hover:bg-gray-50'
            ]"
          >
            <span class="text-lg">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </NuxtLink>
        </nav>
      </aside>

      <!-- 主内容区 -->
      <div class="flex-1">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-xl font-bold text-gray-800">收货地址</h2>
          <button
            class="px-4 py-2 bg-primary text-white text-sm rounded-lg hover:bg-primary-600 transition-colors"
            @click="openForm(null)"
          >
            + 新增地址
          </button>
        </div>

        <!-- 地址列表 -->
        <div v-if="addresses.length" class="space-y-3">
          <div
            v-for="addr in addresses"
            :key="addr.id"
            class="bg-white rounded-lg p-4 border hover:shadow-md transition-shadow"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-1">
                  <span class="font-medium text-gray-800">{{ addr.receiverName }}</span>
                  <span class="text-gray-500">{{ addr.receiverPhone }}</span>
                  <span v-if="addr.isDefault === 1" class="px-1.5 py-0.5 bg-primary-100 text-primary text-xs rounded">默认</span>
                </div>
                <p class="text-sm text-gray-600">
                  {{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.detailAddress }}
                </p>
              </div>
              <div class="flex items-center gap-2 ml-4">
                <button class="text-sm text-gray-500 hover:text-primary" @click="openForm(addr)">编辑</button>
                <button
                  v-if="addr.isDefault !== 1"
                  class="text-sm text-gray-500 hover:text-primary"
                  @click="setDefault(addr.id)"
                >设为默认</button>
                <button class="text-sm text-red-400 hover:text-red-600" @click="confirmDelete(addr.id)">删除</button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="bg-white rounded-lg p-12 text-center text-gray-400">
          <p class="text-4xl mb-3">📍</p>
          <p>暂无收货地址</p>
          <button class="mt-3 text-sm text-primary hover:text-primary-600" @click="openForm(null)">去添加</button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <Teleport to="body">
      <div v-if="showForm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showForm = false">
        <div class="bg-white rounded-xl w-full max-w-md p-6 shadow-xl">
          <h3 class="text-lg font-bold text-gray-800 mb-4">{{ editingAddr ? '编辑地址' : '新增地址' }}</h3>
          <div class="space-y-3">
            <div>
              <label class="block text-sm text-gray-600 mb-1">收件人</label>
              <input v-model="form.receiverName" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="请输入收件人姓名" />
            </div>
            <div>
              <label class="block text-sm text-gray-600 mb-1">手机号</label>
              <input v-model="form.receiverPhone" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="请输入手机号" />
            </div>
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="block text-sm text-gray-600 mb-1">省</label>
                <input v-model="form.province" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="省" />
              </div>
              <div>
                <label class="block text-sm text-gray-600 mb-1">市</label>
                <input v-model="form.city" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="市" />
              </div>
              <div>
                <label class="block text-sm text-gray-600 mb-1">区</label>
                <input v-model="form.district" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="区" />
              </div>
            </div>
            <div>
              <label class="block text-sm text-gray-600 mb-1">详细地址</label>
              <input v-model="form.detailAddress" class="w-full h-10 px-3 border rounded-lg text-sm focus:outline-none focus:border-primary" placeholder="街道、楼牌号等" />
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
              <input v-model="isDefaultChecked" type="checkbox" class="accent-primary" />
              设为默认地址
            </label>
          </div>
          <div class="flex gap-3 mt-6">
            <button class="flex-1 h-10 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50" @click="showForm = false">取消</button>
            <button class="flex-1 h-10 bg-primary text-white rounded-lg text-sm hover:bg-primary-600" @click="saveAddress">保存</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="deleteId" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="deleteId = null">
        <div class="bg-white rounded-xl w-full max-w-xs p-6 shadow-xl text-center">
          <p class="text-4xl mb-3">🗑️</p>
          <p class="text-gray-800 mb-4">确定删除这个地址吗？</p>
          <div class="flex gap-3">
            <button class="flex-1 h-10 border border-gray-300 rounded-lg text-sm text-gray-600 hover:bg-gray-50" @click="deleteId = null">取消</button>
            <button class="flex-1 h-10 bg-red-500 text-white rounded-lg text-sm hover:bg-red-600" @click="doDelete">删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toast.visible"
        :class="['fixed top-20 left-1/2 -translate-x-1/2 z-50 px-6 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2',
          toast.type === 'success' ? 'bg-green-500 text-white' : 'bg-red-500 text-white']">
        {{ toast.message }}
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { get, post, put, del } from '~/utils/request'

definePageMeta({ middleware: ['auth'] })

const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true; toast.message = message; toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

const userStore = useUserStore()

const sidebarMenu = [
  { icon: '👤', label: '个人中心', path: '/user' },
  { icon: '📦', label: '我的订单', path: '/user/orders' },
  { icon: '📍', label: '收货地址', path: '/user/address' },
  { icon: '🎟️', label: '我的优惠券', path: '/user/coupons' },
  { icon: '⭐', label: '我的评价', path: '/user/reviews' },
  { icon: '❤️', label: '我的收藏', path: '/user/favorites' },
]

const addresses = ref<any[]>([])
const showForm = ref(false)
const editingAddr = ref<any>(null)
const deleteId = ref<number | null>(null)
const isDefaultChecked = ref(false)

const form = ref({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
})

async function fetchAddresses() {
  try {
    addresses.value = await get<any[]>('/user/address/list')
  } catch (e) {
    console.error(e)
  }
}

function openForm(addr: any) {
  editingAddr.value = addr
  if (addr) {
    form.value = {
      receiverName: addr.receiverName,
      receiverPhone: addr.receiverPhone,
      province: addr.province,
      city: addr.city,
      district: addr.district,
      detailAddress: addr.detailAddress,
    }
    isDefaultChecked.value = addr.isDefault === 1
  } else {
    form.value = { receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '' }
    isDefaultChecked.value = false
  }
  showForm.value = true
}

async function saveAddress() {
  if (!form.value.receiverName || !form.value.receiverPhone || !form.value.detailAddress) {
    showToast('请填写完整信息', 'error')
    return
  }
  try {
    const data = { ...form.value, isDefault: isDefaultChecked.value ? 1 : 0 }
    if (editingAddr.value) {
      await put('/user/address', { id: editingAddr.value.id, ...data })
    } else {
      await post('/user/address', data)
    }
    showForm.value = false
    await fetchAddresses()
    showToast('保存成功')
  } catch (e: any) {
    showToast(e.message || '保存失败', 'error')
  }
}

function confirmDelete(id: number) {
  deleteId.value = id
}

async function doDelete() {
  if (!deleteId.value) return
  try {
    await del(`/user/address/${deleteId.value}`)
    deleteId.value = null
    await fetchAddresses()
    showToast('删除成功')
  } catch (e: any) {
    showToast(e.message || '删除失败', 'error')
  }
}

async function setDefault(id: number) {
  try {
    await put(`/user/address/${id}/default`)
    await fetchAddresses()
    showToast('设置成功')
  } catch (e: any) {
    showToast(e.message || '设置失败', 'error')
  }
}

onMounted(() => {
  userStore.getUserInfo()
  fetchAddresses()
})
</script>
