<template>
  <div class="w-full h-full">
    <!-- 有轮播数据 -->
    <div
      v-if="banners.length"
      class="relative rounded-lg overflow-hidden w-full h-full group"
      @mouseenter="pause"
      @mouseleave="resume"
    >
      <!-- 滑动轨道 -->
      <div
        class="flex h-full transition-transform duration-500 ease-out"
        :style="{ transform: `translateX(-${current * 100}%)` }"
      >
        <div
          v-for="(banner, idx) in banners"
          :key="banner.id"
          class="relative w-full h-full flex-shrink-0 cursor-pointer"
          @click="handleClick(banner)"
        >
          <!-- 有图片 -->
          <img
            v-if="banner.imageUrl && !failedIds.has(banner.id)"
            :src="banner.imageUrl"
            :alt="banner.title"
            class="w-full h-full object-cover"
            @error="failedIds.add(banner.id)"
          />
          <!-- 无图片/加载失败：渐变 + 标题兜底 -->
          <div
            v-else
            class="w-full h-full flex items-center"
            :class="gradients[idx % gradients.length]"
          >
            <div class="text-white px-6 sm:px-12">
              <h2 class="text-2xl sm:text-4xl font-bold mb-2 sm:mb-4">{{ banner.title }}</h2>
              <span class="inline-block bg-white/90 text-primary px-5 sm:px-8 py-2 rounded-full font-medium text-sm sm:text-base">
                立即抢购
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 左右箭头（桌面端悬停显示） -->
      <template v-if="banners.length > 1">
        <button
          class="absolute left-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/30 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-black/50"
          @click="prev"
          aria-label="上一张"
        >
          ‹
        </button>
        <button
          class="absolute right-3 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/30 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity hover:bg-black/50"
          @click="next"
          aria-label="下一张"
        >
          ›
        </button>
      </template>

      <!-- 指示器 -->
      <div v-if="banners.length > 1" class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
        <button
          v-for="(banner, idx) in banners"
          :key="banner.id"
          class="h-1.5 rounded-full transition-all duration-300"
          :class="idx === current ? 'w-8 bg-white' : 'w-4 bg-white/50 hover:bg-white/80'"
          :aria-label="`第${idx + 1}张`"
          @click="goTo(idx)"
        />
      </div>
    </div>

    <!-- 无数据兜底 -->
    <div
      v-else
      class="relative rounded-lg overflow-hidden bg-gradient-to-r from-primary-500 to-primary-700 w-full h-full flex items-center"
    >
      <div class="text-white px-6 sm:px-12">
        <h2 class="text-2xl sm:text-4xl font-bold mb-2 sm:mb-4">买你所想，尽在此刻</h2>
        <p class="text-sm sm:text-lg opacity-90">全场满减优惠，新品上市特惠</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { get } from '~/utils/request'

interface Banner {
  id: number
  title: string
  imageUrl: string
  linkType: number
  linkValue: string
}

const banners = ref<Banner[]>([])
const current = ref(0)
const failedIds = ref<Set<number>>(new Set())

// 无图兜底渐变色（循环使用）
const gradients = [
  'bg-gradient-to-r from-primary-500 to-primary-700',
  'bg-gradient-to-r from-orange-400 to-pink-600',
  'bg-gradient-to-r from-indigo-500 to-purple-700',
  'bg-gradient-to-r from-emerald-500 to-teal-700'
]

let timer: ReturnType<typeof setInterval> | null = null
const INTERVAL = 4000

const fetchBanners = async () => {
  try {
    const data = await get<any[]>('/banner/list', { position: 0 })
    banners.value = (data || []).map(b => ({
      id: b.id,
      title: b.title,
      imageUrl: b.imageUrl,
      linkType: b.linkType,
      linkValue: b.linkValue
    }))
    startAutoPlay()
  } catch (e) {
    console.error('获取轮播图失败:', e)
  }
}

const goTo = (idx: number) => {
  current.value = (idx + banners.value.length) % banners.value.length
}
const next = () => goTo(current.value + 1)
const prev = () => goTo(current.value - 1)

const startAutoPlay = () => {
  stopAutoPlay()
  if (banners.value.length <= 1) return
  timer = setInterval(next, INTERVAL)
}
const stopAutoPlay = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
const pause = stopAutoPlay
const resume = startAutoPlay

// 根据跳转类型分发
const handleClick = (banner: Banner) => {
  if (!banner.linkValue) return
  switch (banner.linkType) {
    case 1: // 搜索关键词
      navigateTo(`/search?keyword=${encodeURIComponent(banner.linkValue)}`)
      break
    case 2: // 商品详情
      navigateTo(`/product/${banner.linkValue}`)
      break
    case 3: // 商品分类
      navigateTo(`/search?category=${encodeURIComponent(banner.linkValue)}`)
      break
    case 4: // 自定义链接
      if (banner.linkValue.startsWith('http')) {
        window.open(banner.linkValue, '_blank')
      } else {
        navigateTo(banner.linkValue)
      }
      break
  }
}

onMounted(fetchBanners)
onBeforeUnmount(stopAutoPlay)
</script>
