<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <!-- 顶部导航栏 -->
    <header class="bg-white shadow-sm sticky top-0 z-50">
      <!-- 顶部条 - 用户信息 -->
      <div class="bg-gray-100 text-xs text-gray-500 hidden sm:block">
        <div class="max-w-7xl mx-auto px-4 flex justify-between items-center h-8">
          <div>
            <span v-if="userStore.username">
              欢迎回来，<span class="text-primary">{{ userStore.nickname || userStore.username }}</span>
            </span>
            <span v-else>
              <NuxtLink to="/login" class="hover:text-primary">登录</NuxtLink>
              <span class="mx-2">|</span>
              <NuxtLink to="/register" class="hover:text-primary">注册</NuxtLink>
            </span>
          </div>
          <div class="flex items-center gap-4">
            <NuxtLink to="/user/orders" class="hover:text-primary">我的订单</NuxtLink>
            <NuxtLink to="/user" class="hover:text-primary">个人中心</NuxtLink>
          </div>
        </div>
      </div>

      <!-- 主头部 -->
      <div class="max-w-7xl mx-auto px-4 py-3 md:py-4 flex items-center gap-2 sm:gap-3 md:gap-8">
        <!-- Logo -->
        <NuxtLink to="/" class="flex-shrink-0 inline-flex items-center">
          <h1 class="text-base sm:text-lg md:text-2xl font-bold text-primary leading-none">BuyYouWant</h1>
          <p class="text-xs text-gray-400 ml-1 hidden sm:block">买你所想</p>
        </NuxtLink>

        <!-- 搜索栏 -->
        <div class="flex-1 min-w-0 mx-1 sm:mx-0">
          <SearchBar />
        </div>

        <!-- 购物车图标 -->
        <NuxtLink
          to="/cart"
          class="relative flex items-center gap-1 md:gap-2 px-2 sm:px-3 md:px-4 py-2 border border-primary text-primary rounded-full hover:bg-primary hover:text-white transition-colors flex-shrink-0"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path d="M3 1a1 1 0 000 2h1.22l.305 1.222a.997.997 0 00.01.042l1.358 5.43-.893.892C3.74 11.846 4.632 14 6.414 14H15a1 1 0 000-2H6.414l1-1H14a1 1 0 00.894-.553l3-6A1 1 0 0017 3H6.28l-.31-1.243A1 1 0 005 1H3zM16 16.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM6.5 18a1.5 1.5 0 100-3 1.5 1.5 0 000 3z" />
          </svg>
          <span class="text-sm font-medium hidden sm:inline">购物车</span>
          <span
            v-if="cartStore.totalCount > 0"
            class="absolute -top-2 -right-2 bg-red-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center"
          >
            {{ cartStore.totalCount > 99 ? '99+' : cartStore.totalCount }}
          </span>
        </NuxtLink>
      </div>

      <!-- 分类导航 -->
      <nav class="border-t border-gray-100">
        <div class="max-w-7xl mx-auto px-4">
          <ul class="flex items-center gap-4 md:gap-6 h-10 text-sm overflow-x-auto whitespace-nowrap scrollbar-hide">
            <li class="flex-shrink-0"><NuxtLink to="/" class="text-primary font-medium hover:text-primary-700">首页</NuxtLink></li>
            <li v-for="cat in categories" :key="cat" class="flex-shrink-0">
              <NuxtLink :to="`/search?category=${cat}`" class="text-gray-600 hover:text-primary">{{ cat }}</NuxtLink>
            </li>
          </ul>
        </div>
      </nav>
    </header>

    <!-- 主内容区 -->
    <main class="flex-1">
      <slot />
    </main>

    <!-- 页脚 -->
    <footer class="bg-gray-800 text-gray-400 mt-12">
      <div class="max-w-7xl mx-auto px-4 py-10">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-6 md:gap-8 mb-8">
          <div>
            <h3 class="text-white font-medium mb-4">购物指南</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">购物流程</a></li>
              <li><a href="#" class="hover:text-white">会员制度</a></li>
              <li><a href="#" class="hover:text-white">常见问题</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">配送方式</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">配送范围</a></li>
              <li><a href="#" class="hover:text-white">配送费用</a></li>
              <li><a href="#" class="hover:text-white">配送时效</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">支付方式</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">在线支付</a></li>
              <li><a href="#" class="hover:text-white">货到付款</a></li>
              <li><a href="#" class="hover:text-white">分期付款</a></li>
            </ul>
          </div>
          <div>
            <h3 class="text-white font-medium mb-4">售后服务</h3>
            <ul class="space-y-2 text-sm">
              <li><a href="#" class="hover:text-white">退换货政策</a></li>
              <li><a href="#" class="hover:text-white">退换货流程</a></li>
              <li><a href="#" class="hover:text-white">联系客服</a></li>
            </ul>
          </div>
        </div>
        <div class="border-t border-gray-700 pt-6 text-center text-sm">
          <p>© 2026 BuyYouWant 买你所想 - 版权所有</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'

const userStore = useUserStore()
const cartStore = useCartStore()

const categories = ['数码电器', '服装鞋包', '食品生鲜', '美妆护肤', '家居家装', '运动户外', '图书文具']
</script>
