<template>
  <NuxtLink
    :to="`/product/${product.productId || product.id}`"
    class="group block bg-white rounded-lg overflow-hidden hover:shadow-lg transition-shadow duration-300"
  >
    <!-- 商品图片 -->
    <div class="aspect-square bg-gray-100 relative overflow-hidden">
      <img
        :src="product.image || 'https://via.placeholder.com/300x300?text=商品图片'"
        :alt="product.title || product.productName"
        class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
      />
      <!-- 促销标签 -->
      <span
        v-if="product.promotion"
        class="absolute top-2 left-2 bg-primary text-white text-xs px-2 py-0.5 rounded"
      >
        {{ product.promotion }}
      </span>
    </div>

    <!-- 商品信息 -->
    <div class="p-3">
      <h3 class="text-sm text-gray-800 line-clamp-2 min-h-[2.5rem] leading-tight">
        {{ product.title || product.productName }}
      </h3>
      <div class="mt-2 flex items-baseline gap-1">
        <span class="text-primary text-lg font-bold">¥{{ formatPrice(product.price) }}</span>
        <span v-if="product.originalPrice" class="text-gray-400 text-xs line-through">
          ¥{{ formatPrice(product.originalPrice) }}
        </span>
      </div>
      <div class="mt-1 flex items-center justify-between text-xs text-gray-400">
        <span>{{ product.salesCount || 0 }}人已付款</span>
        <span v-if="product.shopName">{{ product.shopName }}</span>
      </div>
    </div>
  </NuxtLink>
</template>

<script setup lang="ts">
interface Product {
  id?: number
  productId?: number
  title?: string
  productName?: string
  image: string
  price: number
  originalPrice?: number
  salesCount?: number
  promotion?: string
  shopName?: string
}

defineProps<{
  product: Product
}>()

function formatPrice(price: number): string {
  return price.toFixed(2)
}
</script>
