<template>
  <div>
    <!-- 轮播 Banner -->
    <section class="max-w-7xl mx-auto px-4 mt-4">
      <div class="relative rounded-lg overflow-hidden bg-gradient-to-r from-primary-500 to-primary-700 h-64 flex items-center">
        <div class="text-white px-12">
          <h2 class="text-4xl font-bold mb-4">买你所想，尽在此刻</h2>
          <p class="text-lg opacity-90 mb-6">全场满减优惠，新品上市特惠</p>
          <button class="bg-white text-primary px-8 py-2 rounded-full font-medium hover:bg-gray-100 transition-colors">
            立即抢购
          </button>
        </div>
        <!-- Banner 指示器 -->
        <div class="absolute bottom-4 left-1/2 -translate-x-1/2 flex gap-2">
          <span class="w-8 h-1.5 bg-white rounded-full"></span>
          <span class="w-8 h-1.5 bg-white/50 rounded-full"></span>
          <span class="w-8 h-1.5 bg-white/50 rounded-full"></span>
        </div>
      </div>
    </section>

    <!-- 三级分类导航 -->
    <section class="max-w-7xl mx-auto px-4 mt-6">
      <div class="bg-white rounded-lg p-6">
        <div class="grid grid-cols-5 gap-6">
          <div
            v-for="category in categoryTree"
            :key="category.name"
            class="group relative"
            @mouseenter="hoveredCategory = category.name"
            @mouseleave="hoveredCategory = ''"
          >
            <div class="flex items-center gap-2 cursor-pointer text-gray-700 hover:text-primary transition-colors">
              <span class="text-2xl">{{ category.icon }}</span>
              <span class="font-medium">{{ category.name }}</span>
            </div>
            <!-- 二级分类 -->
            <div class="mt-2 flex flex-wrap gap-2">
              <NuxtLink
                v-for="sub in category.children"
                :key="sub.name"
                :to="`/search?category=${sub.name}`"
                class="text-xs text-gray-500 hover:text-primary"
              >
                {{ sub.name }}
              </NuxtLink>
            </div>
            <!-- 三级分类弹出 -->
            <div
              v-if="hoveredCategory === category.name"
              class="absolute left-0 top-full mt-1 z-40 bg-white rounded-lg shadow-xl border p-4 w-64 hidden group-hover:block"
            >
              <div v-for="sub in category.children" :key="sub.name" class="mb-3">
                <div class="text-sm font-medium text-gray-800 mb-1">{{ sub.name }}</div>
                <div class="flex flex-wrap gap-1">
                  <NuxtLink
                    v-for="third in sub.children"
                    :key="third"
                    :to="`/search?category=${third}`"
                    class="text-xs text-gray-500 hover:text-primary bg-gray-50 px-2 py-0.5 rounded"
                  >
                    {{ third }}
                  </NuxtLink>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 快捷入口 -->
    <section class="max-w-7xl mx-auto px-4 mt-6">
      <div class="grid grid-cols-10 gap-3">
        <div
          v-for="shortcut in shortcuts"
          :key="shortcut.label"
          class="flex flex-col items-center gap-1 py-3 bg-white rounded-lg hover:shadow-md transition-shadow cursor-pointer"
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
  </div>
</template>

<script setup lang="ts">
const hoveredCategory = ref('')

// 三级分类数据
const categoryTree = [
  {
    name: '数码电器',
    icon: '📱',
    children: [
      { name: '手机通讯', children: ['智能手机', '功能手机', '手机配件'] },
      { name: '电脑办公', children: ['笔记本', '台式机', '平板电脑', '显示器'] },
      { name: '影音设备', children: ['耳机', '音箱', '投影仪'] },
    ],
  },
  {
    name: '服装鞋包',
    icon: '👕',
    children: [
      { name: '男装', children: ['T恤', '衬衫', '夹克', '裤子'] },
      { name: '女装', children: ['连衣裙', '半身裙', '卫衣', '外套'] },
      { name: '鞋靴', children: ['运动鞋', '休闲鞋', '皮鞋', '靴子'] },
    ],
  },
  {
    name: '食品生鲜',
    icon: '🍎',
    children: [
      { name: '水果蔬菜', children: ['新鲜水果', '蔬菜', '干果'] },
      { name: '肉禽蛋奶', children: ['猪肉', '牛肉', '鸡肉', '牛奶'] },
      { name: '零食饮料', children: ['膨化食品', '坚果', '饮料', '茶'] },
    ],
  },
  {
    name: '美妆护肤',
    icon: '💄',
    children: [
      { name: '面部护肤', children: ['洁面', '精华', '面霜', '面膜'] },
      { name: '彩妆', children: ['口红', '粉底', '眼影', '腮红'] },
      { name: '个护清洁', children: ['洗发水', '沐浴露', '牙膏'] },
    ],
  },
  {
    name: '家居家装',
    icon: '🏠',
    children: [
      { name: '家具', children: ['沙发', '床', '书桌', '衣柜'] },
      { name: '家纺', children: ['床上用品', '毛巾', '窗帘'] },
      { name: '厨具', children: ['锅具', '餐具', '刀具'] },
    ],
  },
]

// 快捷入口
const shortcuts = [
  { icon: '⚡', label: '限时秒杀' },
  { icon: '🎁', label: '新人专享' },
  { icon: '🏷️', label: '品牌特卖' },
  { icon: '💰', label: '领券中心' },
  { icon: '🔥', label: '热卖榜' },
  { icon: '✨', label: '新品首发' },
  { icon: '🌍', label: '海外精选' },
  { icon: '🎮', label: '充值中心' },
  { icon: '📦', label: '物流查询' },
  { icon: '💬', label: '在线客服' },
]

// 热门商品占位数据
const hotProducts = [
  { id: 1, title: 'Apple iPhone 15 Pro Max 256GB 原色钛金属', image: 'https://via.placeholder.com/300x300?text=iPhone+15', price: 9999, originalPrice: 10999, salesCount: 58000, promotion: '热卖' },
  { id: 2, title: '华为 Mate 60 Pro 512GB 雅丹黑', image: 'https://via.placeholder.com/300x300?text=Mate+60', price: 7999, originalPrice: 8999, salesCount: 42000, promotion: '新品' },
  { id: 3, title: '小米14 Ultra 影像旗舰 16+512GB', image: 'https://via.placeholder.com/300x300?text=Mi+14', price: 6499, salesCount: 31000 },
  { id: 4, title: 'Sony WH-1000XM5 无线降噪头戴式耳机', image: 'https://via.placeholder.com/300x300?text=Sony+XM5', price: 2299, originalPrice: 2999, salesCount: 18000, promotion: '特价' },
  { id: 5, title: 'Nintendo Switch OLED 马力欧限定版', image: 'https://via.placeholder.com/300x300?text=Switch', price: 2349, salesCount: 25000 },
  { id: 6, title: '戴森 V15 Detect 无线吸尘器', image: 'https://via.placeholder.com/300x300?text=Dyson+V15', price: 4990, originalPrice: 5990, salesCount: 12000, promotion: '满减' },
  { id: 7, title: 'LEGO 乐高 42151 布加迪 Bolide', image: 'https://via.placeholder.com/300x300?text=LEGO', price: 399, salesCount: 8900 },
  { id: 8, title: 'Apple MacBook Air M3 15英寸 16+512GB', image: 'https://via.placeholder.com/300x300?text=MacBook', price: 12499, originalPrice: 13499, salesCount: 9800, promotion: '教育优惠' },
  { id: 9, title: '三星 Galaxy S24 Ultra 12+256GB 钛灰', image: 'https://via.placeholder.com/300x300?text=Galaxy+S24', price: 8999, salesCount: 15600 },
  { id: 10, title: 'Bose QuietComfort 消噪耳塞 II', image: 'https://via.placeholder.com/300x300?text=Bose+QC', price: 1699, originalPrice: 2299, salesCount: 22000, promotion: '限时折扣' },
]

// 新品推荐占位数据
const newProducts = [
  { id: 11, title: 'Apple Vision Pro 空间计算设备', image: 'https://via.placeholder.com/300x300?text=Vision+Pro', price: 29999, salesCount: 3200, promotion: '新品' },
  { id: 12, title: 'OPPO Find X7 Ultra 卫星通信版', image: 'https://via.placeholder.com/300x300?text=OPPO+X7', price: 6999, salesCount: 5600, promotion: '新品' },
  { id: 13, title: '大疆 DJI Mini 4 Pro 航拍无人机', image: 'https://via.placeholder.com/300x300?text=DJI+Mini4', price: 5788, originalPrice: 6388, salesCount: 7800 },
  { id: 14, title: 'Kindle Scribe 电子墨水阅读器', image: 'https://via.placeholder.com/300x300?text=Kindle', price: 2399, salesCount: 4100 },
  { id: 15, title: 'Marshall Stanmore III 蓝牙音箱', image: 'https://via.placeholder.com/300x300?text=Marshall', price: 3999, originalPrice: 4499, salesCount: 6300, promotion: '新品' },
]
</script>
