<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="loginModalVisible" class="fixed inset-0 z-[60] flex items-center justify-center px-4">
        <!-- 遮罩 -->
        <div class="absolute inset-0 bg-black/50" @click="handleClose"></div>

        <!-- 弹框主体：复用登录页表单，原地登录不跳转 -->
        <div class="relative w-full max-w-md bg-white rounded-xl shadow-lg p-8">
          <!-- 关闭按钮 -->
          <button
            class="absolute top-4 right-4 w-8 h-8 flex items-center justify-center text-gray-400 hover:text-gray-600 transition-colors"
            aria-label="关闭"
            @click="handleClose"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          <div class="text-center mb-6">
            <h1 class="text-2xl font-bold text-primary">BuyYouWant</h1>
            <p class="text-gray-400 text-xs mt-1">登录后继续操作</p>
          </div>

          <form @submit.prevent="handleLogin" class="space-y-4">
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

            <div v-if="errorMsg" class="text-sm text-red-500 bg-red-50 p-2 rounded">
              {{ errorMsg }}
            </div>

            <button
              type="submit"
              :disabled="loading"
              class="w-full h-11 bg-primary text-white rounded-lg font-medium hover:bg-primary-600 disabled:opacity-50 transition-colors"
            >
              {{ loading ? '登录中...' : '登录' }}
            </button>
          </form>

          <div class="mt-6 text-center text-sm text-gray-500">
            还没有账号？
            <NuxtLink to="/register" class="text-primary hover:text-primary-600" @click="handleClose">立即注册</NuxtLink>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { useUserStore } from '~/stores/user'
import { useCartStore } from '~/stores/cart'

const { loginModalVisible, closeLoginModal } = useLoginModal()
const userStore = useUserStore()
const cartStore = useCartStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMsg = ref('')

// 弹框每次打开时重置表单，避免残留上次输入
watch(loginModalVisible, (v) => {
  if (v) {
    form.username = ''
    form.password = ''
    errorMsg.value = ''
  }
})

function handleClose() {
  if (loading.value) return
  closeLoginModal()
}

async function handleLogin() {
  if (!form.username || !form.password) {
    errorMsg.value = '请填写用户名和密码'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    await userStore.login(form.username, form.password)
    // 原地登录成功：刷新购物车与收藏态，停留当前页面
    cartStore.getCartList()
    useFavorites().loadFavoriteIds(true)
    closeLoginModal()
  } catch (error: any) {
    errorMsg.value = error?.message || '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
