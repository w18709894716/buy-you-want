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

      <h2 class="text-xl font-bold text-gray-800 text-center mb-6">用户注册</h2>

      <form @submit.prevent="handleRegister" class="space-y-4">
        <!-- 用户名 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">用户名</label>
          <input
            v-model="form.username"
            type="text"
            placeholder="请输入用户名（4-20位字符）"
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
            placeholder="请输入密码（至少6位）"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 确认密码 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">确认密码</label>
          <input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 手机号 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">手机号</label>
          <input
            v-model="form.phone"
            type="tel"
            placeholder="请输入手机号"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 昵称 -->
        <div>
          <label class="block text-sm text-gray-600 mb-1">昵称</label>
          <input
            v-model="form.nickname"
            type="text"
            placeholder="请输入昵称"
            class="w-full h-11 px-4 border border-gray-300 rounded-lg focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-all"
            required
          />
        </div>

        <!-- 错误信息 -->
        <div v-if="errorMsg" class="text-sm text-red-500 bg-red-50 p-2 rounded">
          {{ errorMsg }}
        </div>

        <!-- 成功信息 -->
        <div v-if="successMsg" class="text-sm text-green-600 bg-green-50 p-2 rounded">
          {{ successMsg }}
        </div>

        <!-- 注册按钮 -->
        <button
          type="submit"
          :disabled="loading"
          class="w-full h-11 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors"
        >
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>

      <!-- 底部链接 -->
      <div class="mt-6 text-center text-sm text-gray-500">
        已有账号？
        <NuxtLink to="/login" class="text-primary hover:text-primary-600">立即登录</NuxtLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'

definePageMeta({ layout: false })

const userStore = useUserStore()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  nickname: '',
})

const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

async function handleRegister() {
  errorMsg.value = ''
  successMsg.value = ''

  // 表单校验
  if (!form.username || !form.password || !form.phone || !form.nickname) {
    errorMsg.value = '请填写所有必填项'
    return
  }
  if (form.password !== form.confirmPassword) {
    errorMsg.value = '两次密码输入不一致'
    return
  }
  if (form.password.length < 6) {
    errorMsg.value = '密码至少6位'
    return
  }
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }

  loading.value = true

  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      phone: form.phone,
      nickname: form.nickname,
    })
    successMsg.value = '注册成功！即将跳转到登录页面...'
    setTimeout(() => navigateTo('/login'), 1500)
  } catch (error: any) {
    errorMsg.value = error?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
