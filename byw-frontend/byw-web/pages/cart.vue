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
              <div v-if="item.skuSpecs" class="text-xs text-gray-400 mt-1">{{ item.skuSpecs }}</div>

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
  </div>
</template>

<script setup lang="ts">
import { useCartStore } from '~/stores/cart'

definePageMeta({ middleware: ['auth'] })

const cartStore = useCartStore()

onMounted(() => {
  cartStore.getCartList()
})

function handleRemove(cartId: number) {
  if (confirm('确定要删除这个商品吗？')) {
    cartStore.removeItem(cartId)
  }
}

function handleCheckout() {
  if (cartStore.checkedCount === 0) return
  navigateTo('/checkout')
}
</script>
