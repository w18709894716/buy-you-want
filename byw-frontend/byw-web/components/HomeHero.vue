<template>
  <section class="max-w-7xl mx-auto px-4 mt-4">
    <div class="relative flex gap-3 h-64 md:h-[400px]">
      <!-- 左侧分类 + 悬浮大菜单 -->
      <div
        class="hidden md:block relative shrink-0"
        @mouseleave="hoveredCat = null"
      >
        <aside class="w-52 h-full bg-white rounded-lg shadow-sm overflow-hidden">
          <ul class="h-full flex flex-col py-1.5 overflow-y-auto">
            <li
              v-for="cat in categoryTree"
              :key="cat.id"
              class="px-4 py-[7px] flex items-center justify-between text-sm text-gray-700 hover:bg-primary hover:text-white cursor-pointer transition-colors"
              @mouseenter="hoveredCat = cat"
            >
              <NuxtLink :to="`/search?category=${encodeURIComponent(cat.name)}`" class="flex items-center gap-1.5 flex-1 min-w-0">
                <img v-if="cat.icon && cat.icon.startsWith('http')" :src="cat.icon" class="w-4 h-4 object-contain" alt="" />
                <span v-else-if="cat.icon">{{ cat.icon }}</span>
                <span class="truncate">{{ cat.name }}</span>
              </NuxtLink>
              <span class="text-xs opacity-60">›</span>
            </li>
          </ul>
        </aside>

        <!-- 悬浮大菜单：覆盖轮播区 -->
        <div
          v-show="hoveredCat && hoveredCat.children && hoveredCat.children.length"
          class="absolute left-full top-0 z-40 h-full w-[640px] bg-white shadow-2xl rounded-r-lg border-l p-5 overflow-y-auto"
        >
          <div v-for="sub in hoveredCat?.children || []" :key="sub.id" class="flex items-start gap-3 py-2 border-b border-gray-50 last:border-0">
            <NuxtLink
              :to="`/search?category=${encodeURIComponent(sub.name)}`"
              class="text-sm font-medium text-gray-800 hover:text-primary shrink-0 w-24 truncate"
            >
              {{ sub.name }}
            </NuxtLink>
            <div v-if="sub.children && sub.children.length" class="flex flex-wrap gap-x-3 gap-y-1.5 flex-1">
              <NuxtLink
                v-for="third in sub.children"
                :key="third.id"
                :to="`/search?category=${encodeURIComponent(third.name)}`"
                class="text-xs text-gray-500 hover:text-primary"
              >
                {{ third.name }}
              </NuxtLink>
            </div>
          </div>
        </div>
      </div>

      <!-- 中间轮播 -->
      <div class="flex-1 min-w-0 h-full">
        <HomeBanner />
      </div>

      <!-- 右侧活动图框：四行 -->
      <div class="hidden lg:flex flex-col gap-3 w-60 shrink-0 h-full">
        <div
          v-for="(act, idx) in activityFrames"
          :key="act.id"
          class="relative flex-1 min-h-0 rounded-lg overflow-hidden cursor-pointer group"
          @click="handleClick(act)"
        >
          <img
            v-if="act.imageUrl && !failedIds.has(act.id)"
            :src="act.imageUrl"
            :alt="act.title"
            class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
            @error="failedIds.add(act.id)"
          />
          <div
            v-else
            class="w-full h-full flex items-center justify-between gap-2 px-4 text-white"
            :class="gradients[idx % gradients.length]"
          >
            <span class="text-base font-semibold">{{ act.title }}</span>
            <span class="text-xs opacity-90 whitespace-nowrap">立即查看 ›</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { get } from '~/utils/request'

interface Category {
  id: number
  name: string
  icon?: string
  children?: Category[]
}

interface Banner {
  id: number
  title: string
  imageUrl: string
  linkType: number
  linkValue: string
}

const categoryTree = ref<Category[]>([])
const hoveredCat = ref<Category | null>(null)

const activityFrames = ref<Banner[]>([])
const failedIds = ref<Set<number>>(new Set())

const gradients = [
  'bg-gradient-to-br from-orange-400 to-pink-600',
  'bg-gradient-to-br from-indigo-500 to-purple-700',
  'bg-gradient-to-br from-emerald-500 to-teal-700',
  'bg-gradient-to-br from-sky-500 to-blue-700'
]

const fetchCategories = async () => {
  try {
    const data = await get<Category[]>('/product/category/tree')
    categoryTree.value = data || []
  } catch (e) {
    console.error('获取分类失败:', e)
  }
}

const fetchActivities = async () => {
  try {
    const data = await get<any[]>('/banner/list', { position: 1 })
    activityFrames.value = (data || []).slice(0, 4).map(b => ({
      id: b.id,
      title: b.title,
      imageUrl: b.imageUrl,
      linkType: b.linkType,
      linkValue: b.linkValue
    }))
  } catch (e) {
    console.error('获取活动位失败:', e)
  }
}

// 与轮播图一致的跳转分发
const handleClick = (act: Banner) => {
  if (!act.linkValue) return
  switch (act.linkType) {
    case 1:
      navigateTo(`/search?keyword=${encodeURIComponent(act.linkValue)}`)
      break
    case 2:
      navigateTo(`/product/${act.linkValue}`)
      break
    case 3:
      navigateTo(`/search?category=${encodeURIComponent(act.linkValue)}`)
      break
    case 4:
      if (act.linkValue.startsWith('http')) {
        window.open(act.linkValue, '_blank')
      } else {
        navigateTo(act.linkValue)
      }
      break
  }
}

onMounted(() => {
  fetchCategories()
  fetchActivities()
})
</script>
