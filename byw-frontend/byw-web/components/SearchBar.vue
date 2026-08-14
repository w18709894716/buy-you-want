<template>
  <div class="flex w-full">
    <!-- 搜索范围下拉（淘宝式）：宝贝 / 店铺 -->
    <div class="relative flex-shrink-0">
      <button
        class="h-10 px-3 sm:px-4 border-2 border-r-0 border-primary rounded-l-full bg-white text-sm text-gray-700 flex items-center gap-1 hover:text-primary transition-colors"
        @click="scopeMenuOpen = !scopeMenuOpen"
      >
        {{ scope === 'shop' ? '店铺' : '宝贝' }}
        <svg xmlns="http://www.w3.org/2000/svg" class="w-3 h-3" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
        </svg>
      </button>
      <!-- 下拉与输入框之间的灰色短竖线分隔 -->
      <span class="absolute right-0 top-1/2 -translate-y-1/2 h-4 w-px bg-gray-300 pointer-events-none"></span>
      <!-- 遮罩：点击外部收起下拉 -->
      <div v-if="scopeMenuOpen" class="fixed inset-0 z-40" @click="scopeMenuOpen = false"></div>
      <div v-if="scopeMenuOpen" class="absolute left-0 top-11 z-50 w-24 bg-white border border-gray-200 rounded-md shadow-lg py-1">
        <button
          class="block w-full text-left px-4 py-1.5 text-sm hover:bg-gray-50 transition-colors"
          :class="scope === 'product' ? 'text-primary font-medium' : 'text-gray-700'"
          @click="selectScope('product')"
        >宝贝</button>
        <button
          class="block w-full text-left px-4 py-1.5 text-sm hover:bg-gray-50 transition-colors"
          :class="scope === 'shop' ? 'text-primary font-medium' : 'text-gray-700'"
          @click="selectScope('shop')"
        >店铺</button>
      </div>
    </div>
    <input
      v-model="keyword"
      type="text"
      :placeholder="scope === 'shop' ? '搜索店铺...' : '搜索你想要的商品...'"
      class="flex-1 h-10 px-4 border-2 border-l-0 border-primary outline-none text-sm focus:border-primary-600 transition-colors"
      @keyup.enter="handleEnter"
    />
    <!-- 店铺主页内 + 宝贝范围：搜本店（店内搜索，空关键词=清除店内筛选） -->
    <button
      v-if="inShop && scope === 'product'"
      class="h-10 px-3 sm:px-4 bg-primary text-white hover:bg-primary-600 transition-colors flex items-center flex-shrink-0"
      @click="searchShop"
    >
      <span class="text-sm font-medium">搜本店</span>
    </button>
    <button
      class="h-10 px-4 sm:px-6 bg-primary text-white rounded-r-full hover:bg-primary-600 transition-colors flex items-center gap-1 flex-shrink-0"
      :class="(inShop && scope === 'product') && 'border-l border-white/30'"
      @click="handleMainSearch"
    >
      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" />
      </svg>
      <span class="text-sm font-medium hidden sm:inline">{{ mainLabel }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()

const keyword = ref<string>((route.query.keyword as string) || '')
/** 搜索范围：product-宝贝 shop-店铺（与搜索页 scope 参数双向同步） */
const scope = ref<'product' | 'shop'>((route.query.scope as string) === 'shop' ? 'shop' : 'product')
const scopeMenuOpen = ref(false)

/** 店铺主页内（宝贝范围）搜索框变为「搜本店 + 搜全站」双按钮，其余保持原样 */
const inShop = computed(() => route.path.startsWith('/shop/'))
const shopId = computed(() => route.params.id as string)
const mainLabel = computed(() => (scope.value === 'product' && inShop.value) ? '搜全站' : '搜索')

function selectScope(s: 'product' | 'shop') {
  scope.value = s
  scopeMenuOpen.value = false
}

/** 搜本店：关键词走 query，店铺页监听后做店内过滤；空关键词=清除店内搜索 */
function searchShop() {
  const kw = keyword.value.trim()
  navigateTo({
    path: `/shop/${shopId.value}`,
    query: kw ? { keyword: kw } : {},
  })
}

/** 搜全站宝贝：原有全站搜索 */
function searchAll() {
  if (keyword.value.trim()) {
    navigateTo({
      path: '/search',
      query: { keyword: keyword.value.trim() },
    })
  }
}

/** 搜店铺：店名模糊搜索，结果在搜索页店铺范围下展示 */
function searchShops() {
  if (keyword.value.trim()) {
    navigateTo({
      path: '/search',
      query: { keyword: keyword.value.trim(), scope: 'shop' },
    })
  }
}

function handleEnter() {
  if (scope.value === 'shop') searchShops()
  else if (inShop.value) searchShop()
  else searchAll()
}

function handleMainSearch() {
  if (scope.value === 'shop') searchShops()
  else searchAll()
}

// 监听路由变化更新关键词与范围
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
})
watch(() => route.query.scope, (val) => {
  scope.value = (val as string) === 'shop' ? 'shop' : 'product'
})
</script>
