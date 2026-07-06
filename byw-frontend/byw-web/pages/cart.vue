<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <h2 class="text-xl font-bold text-gray-800 mb-4">我的购物车</h2>

    <div v-if="cartStore.items.length === 0" class="bg-white rounded-lg p-16 text-center">
      <div class="text-6xl mb-4">🛒</div>
      <p class="text-gray-400 mb-4">购物车空空如也</p>
      <NuxtLink to="/" class="text-primary hover:text-primary-600">去逛逛 &gt;</NuxtLink>
    </div>

    <div v-else class="flex flex-col lg:flex-row gap-6">
      <!-- 购物车列表 -->
      <div class="flex-1 min-w-0">
        <!-- 表头 - 桌面端显示 -->
        <div class="bg-white rounded-t-lg p-3 items-center gap-4 text-sm text-gray-500 border-b hidden lg:flex">
          <label class="flex items-center gap-2 w-12">
            <input
              type="checkbox"
              :checked="cartStore.isAllChecked"
              class="w-4 h-4 accent-primary cursor-pointer"
              @change="cartStore.toggleAll()"
            />
            <span>全选</span>
          </label>
          <span class="flex-1">商品信息</span>
          <span class="w-24 text-center">单价</span>
          <span class="w-32 text-center">数量</span>
          <span class="w-24 text-center">小计</span>
          <span class="w-16 text-center">操作</span>
        </div>

        <!-- 移动端全选 -->
        <div class="bg-white rounded-t-lg lg:hidden p-3 flex items-center gap-2 border-b">
          <input
            type="checkbox"
            :checked="cartStore.isAllChecked"
            class="w-4 h-4 accent-primary cursor-pointer"
            @change="cartStore.toggleAll()"
          />
          <span class="text-sm text-gray-500">全选</span>
        </div>

        <!-- 商品列表 -->
        <div class="bg-white rounded-b-lg lg:divide-y">
          <div
            v-for="item in cartStore.items"
            :key="item.cartId"
            class="p-4 flex items-start lg:items-center gap-3 lg:gap-4 border-b lg:border-b last:border-b-0"
          >
            <!-- 选中框 -->
            <input
              type="checkbox"
              :checked="item.checked"
              class="w-4 h-4 accent-primary cursor-pointer flex-shrink-0 mt-5 lg:mt-0"
              @change="cartStore.toggleItem(item.cartId)"
            />

            <!-- 商品图片 -->
            <NuxtLink :to="`/product/${item.productId}`" class="w-20 h-20 flex-shrink-0">
              <img
                :src="item.image || 'https://via.placeholder.com/80x80?text=商品'"
                :alt="item.productName"
                class="w-full h-full object-cover rounded"
              />
            </NuxtLink>

            <!-- 商品信息 + 移动端价格/数量 -->
            <div class="flex-1 min-w-0">
              <NuxtLink :to="`/product/${item.productId}`" class="text-sm text-gray-800 hover:text-primary line-clamp-2">
                {{ item.productName }}
              </NuxtLink>
              <!-- 已选规格展示 + 更换按钮 -->
              <div v-if="parseSpecs(item.specData).length > 0" class="flex items-center gap-2 mt-1">
                <span class="text-xs text-gray-400">已选：</span>
                <span class="text-xs text-gray-600">{{ parseSpecs(item.specData).join(' / ') }}</span>
                <button class="text-xs text-primary hover:text-primary-600" @click="openSpecSwitcher(item)">更换</button>
              </div>

              <!-- 移动端：价格和数量 -->
              <div class="flex items-center justify-between mt-2 lg:hidden">
                <span class="text-sm text-primary font-bold">¥{{ item.price.toFixed(2) }}</span>
                <div class="flex items-center border rounded">
                  <button
                    class="w-7 h-7 flex items-center justify-center text-gray-500 hover:bg-gray-100 disabled:opacity-50"
                    :disabled="item.quantity <= 1"
                    @click="cartStore.updateQuantity(item.cartId, item.quantity - 1)"
                  >-</button>
                  <input
                    :value="item.quantity"
                    type="number"
                    min="1"
                    class="w-10 h-7 text-center border-x outline-none text-sm"
                    @change="(e: Event) => cartStore.updateQuantity(item.cartId, Number((e.target as HTMLInputElement).value) || 1)"
                  />
                  <button
                    class="w-7 h-7 flex items-center justify-center text-gray-500 hover:bg-gray-100"
                    @click="cartStore.updateQuantity(item.cartId, item.quantity + 1)"
                  >+</button>
                </div>
              </div>
              <div class="flex items-center justify-between mt-1 lg:hidden">
                <span class="text-sm font-bold text-primary">小计: ¥{{ (item.price * item.quantity).toFixed(2) }}</span>
                <button class="text-gray-400 hover:text-red-500 text-xs" @click="handleRemove(item.cartId)">删除</button>
              </div>
            </div>

            <!-- 桌面端列 -->
            <div class="w-24 text-center hidden lg:block">
              <span class="text-sm text-gray-800">¥{{ item.price.toFixed(2) }}</span>
            </div>
            <div class="w-32 justify-center hidden lg:flex">
              <div class="flex items-center border rounded">
                <button
                  class="w-7 h-7 flex items-center justify-center text-gray-500 hover:bg-gray-100 disabled:opacity-50"
                  :disabled="item.quantity <= 1"
                  @click="cartStore.updateQuantity(item.cartId, item.quantity - 1)"
                >-</button>
                <input
                  :value="item.quantity"
                  type="number"
                  min="1"
                  class="w-10 h-7 text-center border-x outline-none text-sm"
                  @change="(e: Event) => cartStore.updateQuantity(item.cartId, Number((e.target as HTMLInputElement).value) || 1)"
                />
                <button
                  class="w-7 h-7 flex items-center justify-center text-gray-500 hover:bg-gray-100"
                  @click="cartStore.updateQuantity(item.cartId, item.quantity + 1)"
                >+</button>
              </div>
            </div>
            <div class="w-24 text-center hidden lg:block">
              <span class="text-sm font-bold text-primary">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
            </div>
            <div class="w-16 text-center hidden lg:block">
              <button class="text-gray-400 hover:text-red-500 text-sm" @click="handleRemove(item.cartId)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 结算栏 -->
      <div class="w-full lg:w-72 lg:flex-shrink-0">
        <div class="bg-white rounded-lg p-4 lg:sticky lg:top-40">
          <h3 class="font-medium text-gray-800 mb-4">订单摘要</h3>
          <div class="space-y-2 text-sm">
            <div class="flex justify-between text-gray-600">
              <span>已选商品 ({{ cartStore.checkedCount }} 件)</span>
              <span>¥{{ cartStore.totalPrice.toFixed(2) }}</span>
            </div>
            <div class="flex justify-between text-gray-600">
              <span>运费</span>
              <span class="text-green-500">免运费</span>
            </div>
            <div class="flex justify-between text-gray-600">
              <span>优惠</span>
              <span class="text-green-500">-¥0.00</span>
            </div>
          </div>
          <div class="border-t mt-4 pt-4">
            <div class="flex justify-between items-baseline">
              <span class="text-gray-800 font-medium">合计</span>
              <span class="text-2xl font-bold text-primary">¥{{ cartStore.totalPrice.toFixed(2) }}</span>
            </div>
          </div>
          <button
            :disabled="cartStore.checkedCount === 0"
            class="w-full h-11 mt-4 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            @click="handleCheckout"
          >
            去结算 ({{ cartStore.checkedCount }})
          </button>
        </div>
      </div>
    </div>
    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="removeTarget" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="removeTarget = null" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-sm p-6">
            <h3 class="text-base font-medium text-gray-800 mb-2">确认删除</h3>
            <p class="text-sm text-gray-500 mb-5">确定要从购物车中删除该商品吗？</p>
            <div class="flex justify-end gap-3">
              <button
                class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                @click="removeTarget = null"
              >取消</button>
              <button
                class="px-4 h-9 text-sm text-white bg-red-500 rounded-lg hover:bg-red-600 transition-colors"
                @click="confirmRemove"
              >删除</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 规格切换弹窗 -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="specSwitcherTarget" class="fixed inset-0 z-50 flex items-center justify-center p-4">
          <div class="fixed inset-0 bg-black/40" @click="specSwitcherTarget = null" />
          <div class="relative bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h3 class="text-base font-medium text-gray-800 mb-4">选择规格</h3>
            <!-- 当前商品图片+名称 -->
            <div class="flex items-center gap-3 mb-4 p-3 bg-gray-50 rounded-lg">
              <img :src="specSwitcherTarget.image || 'https://via.placeholder.com/60x60?text=商品'" class="w-14 h-14 object-cover rounded" />
              <div>
                <div class="text-sm text-gray-800">{{ specSwitcherTarget.productName }}</div>
                <div class="text-sm text-primary font-bold mt-1">¥{{ specSwitcherPrice.toFixed(2) }}</div>
              </div>
            </div>
            <!-- 规格选择器 -->
            <div v-if="specSwitcherSkus.length > 0" class="space-y-4">
              <div v-for="(group, gIdx) in specSwitcherGroups" :key="group.name">
                <div class="text-sm text-gray-600 mb-2">{{ group.name }}</div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="option in group.options"
                    :key="option"
                    :class="[
                      'px-3 py-1.5 border rounded text-sm transition-all',
                      specSwitcherSelected[gIdx] === option
                        ? 'border-primary bg-primary-50 text-primary'
                        : 'border-gray-200 text-gray-600 hover:border-gray-300'
                    ]"
                    @click="specSwitcherSelected[gIdx] = option"
                  >{{ option }}</button>
                </div>
              </div>
            </div>
            <div v-else class="text-sm text-gray-400 py-4 text-center">该商品无可选规格</div>
            <!-- 操作按钮 -->
            <div class="flex justify-end gap-3 mt-6">
              <button
                class="px-4 h-9 text-sm text-gray-600 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                @click="specSwitcherTarget = null"
              >取消</button>
              <button
                class="px-4 h-9 text-sm text-white bg-primary rounded-lg hover:bg-primary-600 transition-colors disabled:opacity-50"
                :disabled="!canConfirmSpec"
                @click="confirmChangeSku"
              >确定</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'

definePageMeta({ middleware: ['auth'] })

const cartStore = useCartStore()
const removeTarget = ref<number | null>(null)

onMounted(() => {
  cartStore.getCartList()
})

function handleRemove(cartId: number) {
  removeTarget.value = cartId
}

function confirmRemove() {
  if (removeTarget.value != null) {
    cartStore.removeItem(removeTarget.value)
  }
  removeTarget.value = null
}

/** 解析 specData JSON 为可读数组 */
function parseSpecs(specData: string): string[] {
  if (!specData) return []
  try {
    const obj = typeof specData === 'string' ? JSON.parse(specData) : specData
    return Object.values(obj).map(String)
  } catch {
    return []
  }
}

// ========== 规格切换弹窗 ==========
interface CartItemLike { cartId: number; productId: number; skuId: number; specData: string; productName: string; image: string; price: number }

const specSwitcherTarget = ref<CartItemLike | null>(null)
const specSwitcherSkus = ref<any[]>([])
const specSwitcherGroups = ref<{ name: string; options: string[] }[]>([])
const specSwitcherSelected = ref<Record<number, string>>({})

async function openSpecSwitcher(item: CartItemLike) {
  specSwitcherTarget.value = item
  specSwitcherSelected.value = {}
  specSwitcherSkus.value = []
  specSwitcherGroups.value = []

  try {
    const data: any = await get(`/product/${item.productId}`)
    if (!data?.skus) return
    specSwitcherSkus.value = data.skus

    // 构建规格组
    const groupMap: Record<string, Set<string>> = {}
    for (const sku of data.skus) {
      if (sku.specData) {
        const sd = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
        for (const [k, v] of Object.entries(sd)) {
          if (!groupMap[k]) groupMap[k] = new Set()
          groupMap[k].add(v as string)
        }
      }
    }
    specSwitcherGroups.value = Object.entries(groupMap).map(([name, options]) => ({ name, options: Array.from(options) }))

    // 预选当前规格
    if (item.specData) {
      const currentSpecs = typeof item.specData === 'string' ? JSON.parse(item.specData) : item.specData
      specSwitcherGroups.value.forEach((group, idx) => {
        if (currentSpecs[group.name]) {
          specSwitcherSelected.value[idx] = currentSpecs[group.name]
        }
      })
    }
  } catch (e) {
    console.error('加载商品规格失败', e)
  }
}

/** 规格弹窗实时价格（跟随选中 SKU 变化） */
const specSwitcherPrice = computed(() => {
  const matched = findMatchedSku()
  return matched ? matched.price : specSwitcherTarget.value?.price ?? 0
})

/** 根据已选规格匹配 SKU */
const canConfirmSpec = computed(() => {
  if (specSwitcherGroups.value.length === 0) return false
  const allSelected = specSwitcherGroups.value.every((_, idx) => specSwitcherSelected.value[idx])
  if (!allSelected) return false
  const matched = findMatchedSku()
  return matched && matched.id !== specSwitcherTarget.value?.skuId
})

function findMatchedSku(): any | null {
  const selected: Record<string, string> = {}
  specSwitcherGroups.value.forEach((group, idx) => {
    if (specSwitcherSelected.value[idx]) {
      selected[group.name] = specSwitcherSelected.value[idx]
    }
  })
  return specSwitcherSkus.value.find((sku: any) => {
    const sd = typeof sku.specData === 'string' ? JSON.parse(sku.specData) : sku.specData
    return Object.entries(selected).every(([k, v]) => sd[k] === v)
  }) || null
}

async function confirmChangeSku() {
  const matched = findMatchedSku()
  if (!matched || !specSwitcherTarget.value) return
  try {
    await cartStore.changeSku(specSwitcherTarget.value.cartId, matched.id)
    specSwitcherTarget.value = null
  } catch (e) {
    console.error('切换规格失败', e)
  }
}

function handleCheckout() {
  if (cartStore.checkedCount === 0) return
  navigateTo('/checkout')
}
</script>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
