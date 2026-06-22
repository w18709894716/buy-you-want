<template>
  <div class="flex w-full">
    <input
      v-model="keyword"
      type="text"
      :placeholder="placeholder"
      class="flex-1 h-10 px-4 border-2 border-primary rounded-l-full outline-none text-sm focus:border-primary-600 transition-colors"
      @keyup.enter="handleSearch"
    />
    <button
      class="h-10 px-4 sm:px-6 bg-primary text-white rounded-r-full hover:bg-primary-600 transition-colors flex items-center gap-1 flex-shrink-0"
      @click="handleSearch"
    >
      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" />
      </svg>
      <span class="text-sm font-medium hidden sm:inline">搜索</span>
    </button>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()

const keyword = ref<string>((route.query.keyword as string) || '')
const placeholder = '搜索你想要的商品...'

function handleSearch() {
  if (keyword.value.trim()) {
    navigateTo({
      path: '/search',
      query: { keyword: keyword.value.trim() },
    })
  }
}

// 监听路由变化更新关键词
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
})
</script>
