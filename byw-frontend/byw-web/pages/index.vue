<template>
  <div>
    <!-- 首屏：左分类悬浮大菜单 + 中轮播 + 右活动图框（均可在管理端配置） -->
    <HomeHero />

    <!-- 快捷入口 -->
    <section class="max-w-7xl mx-auto px-4 mt-6">
      <div class="grid grid-cols-5 sm:grid-cols-10 gap-3">
        <div
          v-for="shortcut in shortcuts"
          :key="shortcut.label"
          class="flex flex-col items-center gap-1 py-3 bg-white rounded-lg transition-shadow"
          :class="shortcut.disabled ? 'opacity-50 cursor-not-allowed' : 'hover:shadow-md cursor-pointer'"
          @click="handleShortcutClick(shortcut)"
        >
          <span class="text-2xl">{{ shortcut.icon }}</span>
          <span class="text-xs text-gray-600">{{ shortcut.label }}</span>
        </div>
      </div>
    </section>

    <!-- 热门商品 -->
    <section class="max-w-7xl mx-auto px-4 mt-8">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-800">热门商品</h2>
        <NuxtLink to="/search?sort=hot" class="text-sm text-primary hover:text-primary-600">
          查看更多 &gt;
        </NuxtLink>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <ProductCard v-for="product in hotProducts" :key="product.id" :product="product" />
      </div>
    </section>

    <!-- 新品推荐 -->
    <section class="max-w-7xl mx-auto px-4 mt-10">
      <div class="flex items-center justify-between mb-4">
        <h2 class="text-xl font-bold text-gray-800">新品推荐</h2>
        <NuxtLink to="/search?sort=new" class="text-sm text-primary hover:text-primary-600">
          查看更多 &gt;
        </NuxtLink>
      </div>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <ProductCard v-for="product in newProducts" :key="product.id" :product="product" />
      </div>
    </section>

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
import { get } from '~/utils/request'

const toast = reactive({ visible: false, message: '', type: 'success' as 'success' | 'error' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true; toast.message = message; toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

// 快捷入口
const shortcuts = [
  { icon: '⚡', label: '限时秒杀', to: '/seckill' },
  { icon: '🎁', label: '新人专享', to: '/coupons?scene=new' },
  { icon: '🏷️', label: '品牌特卖', to: '/search?brandSale=1' },
  { icon: '💰', label: '领券中心', to: '/coupons' },
  { icon: '🔥', label: '热卖榜', to: '/search?sort=sales' },
  { icon: '✨', label: '新品首发', to: '/search?sort=new' },
  { icon: '🌍', label: '海外精选', disabled: true },
  { icon: '🎮', label: '充值中心', disabled: true },
  { icon: '📦', label: '物流查询', to: '/logistics' },
  { icon: '💬', label: '在线客服', disabled: true },
]

const handleShortcutClick = (s: { to?: string; disabled?: boolean; label: string }) => {
  if (s.disabled) {
    showToast(`${s.label}：功能开发中，敬请期待`)
    return
  }
  if (s.to) navigateTo(s.to)
}

// 热门商品从接口获取
const hotProducts = ref<any[]>([])
const newProducts = ref<any[]>([])

const fetchProducts = async () => {
  try {
    // 获取热门商品（按销量排序）
    const hotData = await get('/product/list', { pageNum: 1, pageSize: 10, sort: 'sales' })
    hotProducts.value = (hotData?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.minPrice,
      salesCount: p.salesCount
    }))
  } catch (e) {
    console.error('获取热门商品失败:', e)
  }

  try {
    // 获取新品（按创建时间排序）
    const newData = await get('/product/list', { pageNum: 1, pageSize: 5, sort: 'new' })
    newProducts.value = (newData?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.minPrice,
      salesCount: p.salesCount
    }))
  } catch (e) {
    console.error('获取新品失败:', e)
  }
}

onMounted(() => {
  fetchProducts()
})
</script>
