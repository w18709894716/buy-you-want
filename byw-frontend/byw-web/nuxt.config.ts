// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2024-04-01',
  devtools: { enabled: true },

  css: ['~/assets/css/global.css'],

  devServer: {
    host: '0.0.0.0',
  },

  modules: [
    '@pinia/nuxt',
    '@nuxtjs/tailwindcss',
  ],

  // vue3-emoji-picker 为 ESM 包，纳入 Nuxt 编译避免 SSR/依赖解析问题
  build: {
    transpile: ['vue3-emoji-picker'],
  },

  app: {
    head: {
      viewport: 'width=device-width, initial-scale=1, maximum-scale=1',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1, maximum-scale=1' },
      ],
    },
  },

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api',
      // 商家中心（byw-merchant-web）访问地址，商家入驻入口外链使用
      merchantWebUrl: process.env.NUXT_PUBLIC_MERCHANT_WEB_URL || 'http://localhost:5175',
      // 客服 IM WebSocket 网关地址（nitro devProxy 不支持 ws，需直连网关）
      wsBase: process.env.NUXT_PUBLIC_WS_BASE || 'ws://localhost:8080',
    },
  },

  nitro: {
    devProxy: {
      '/api': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
      },
    },
  },
})
