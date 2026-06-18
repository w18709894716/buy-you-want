<template>
  <div class="min-h-[80vh] flex items-center justify-center bg-gradient-to-br from-primary-50 to-white">
    <div class="w-full max-w-md bg-white rounded-xl shadow-lg p-8">
      <!-- Logo -->
      <div class="text-center mb-8">
        <NuxtLink to="/">
          <h1 class="text-3xl font-bold text-primary">BuyYouWant</h1>
          <p class="text-gray-400 text-sm mt-1">买你所想</p>
        </NuxtLink>
      </div>

      <h2 class="text-xl font-bold text-gray-800 text-center mb-6">用户登录</h2>

      <form @submit.prevent="handleLogin" class="space-y-4">
        <!-- 用户名 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">用户名</label>
          <input
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 密码 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">密码</label>
          <input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 错误信息 -->
        <div v-if="errorMsg" class="text-sm text-red-500 bg-red-50 p-2 rounded">
          {{ errorMsg }}
        </div>

        <!-- 登录按钮 -->
        <button
          type="submit"
          :disabled="loading"
          class="w-full h-11 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors"
        >
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <!-- 底部链接 -->
      <div class="mt-6 text-center text-sm text-gray-500">
        还没有账号？
        <NuxtLink to="/register" class="text-primary hover:text-primary-600">立即注册</NuxtLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'

definePageMeta({ layout: false })

const userStore = useUserStore()
const route = useRoute()

const form = reactive({
  username: '',
  password: '',
})

const loading = ref(false)
const errorMsg = ref('')

async function handleLogin() {
  if (!form.username || !form.password) {
    errorMsg.value = '请填写用户名和密码'
    return
  }

  loading.value = true
  errorMsg.value = ''

  try {
    await userStore.login(form.username, form.password)
    const redirect = (route.query.redirect as string) || '/'
    navigateTo(redirect)
  } catch (error: any) {
    errorMsg.value = error?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>
