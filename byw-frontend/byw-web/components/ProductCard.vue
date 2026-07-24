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
      <!-- 收藏按钮 -->
      <button
        class="absolute top-2 right-2 w-8 h-8 rounded-full bg-white/80 backdrop-blur flex items-center justify-center shadow-sm hover:bg-white transition-colors"
        :aria-label="favorited ? '取消收藏' : '收藏'"
        @click.prevent.stop="onToggleFavorite"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          class="w-4 h-4 transition-colors"
          :class="favorited ? 'text-primary' : 'text-gray-400'"
          :fill="favorited ? 'currentColor' : 'none'"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
        </svg>
      </button>
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

const props = defineProps<{
  product: Product
}>()

const { isFavorited, toggleFavorite, loadFavoriteIds } = useFavorites()

const currentProductId = computed(() => Number(props.product.productId || props.product.id))
const favorited = computed(() => isFavorited(currentProductId.value))

async function onToggleFavorite() {
  await toggleFavorite(currentProductId.value)
}

onMounted(() => {
  loadFavoriteIds()
})

function formatPrice(price: number): string {
  if (price == null) return '0.00'
  return price.toFixed(2)
}
</script>
