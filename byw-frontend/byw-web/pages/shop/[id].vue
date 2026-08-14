<template>
  <div class="max-w-7xl mx-auto px-4 py-6">
    <!-- 轻提示 -->
    <Transition name="fade">
      <div
        v-if="toast.visible"
        class="fixed top-20 left-1/2 -translate-x-1/2 z-[60] px-4 py-2 rounded-full shadow-lg text-sm text-white"
        :class="toast.type === 'success' ? 'bg-green-500' : 'bg-red-500'"
      >
        {{ toast.message }}
      </div>
    </Transition>

    <!-- 店铺不存在/关店 -->
    <div v-if="closed" class="bg-white rounded-lg p-16 text-center">
      <div class="text-6xl mb-4">🏬</div>
      <p class="text-gray-400">该店铺不存在或已关店</p>
    </div>

    <template v-else>
      <!-- 店铺头部 -->
      <div class="bg-white rounded-lg p-4 sm:p-6 flex items-center gap-4">
        <img
          :src="shop.logo || 'https://via.placeholder.com/160x160?text=店铺'"
          alt="店铺Logo"
          class="w-16 h-16 sm:w-20 sm:h-20 rounded-lg object-cover bg-gray-100 flex-shrink-0"
        />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <h1 class="text-lg sm:text-xl font-bold text-gray-800 truncate">{{ shop.name }}</h1>
            <span v-if="shop.selfOperated === 0" class="text-xs bg-primary text-white px-1.5 py-0.5 rounded flex-shrink-0">自营</span>
          </div>
          <p v-if="shop.description" class="text-sm text-gray-500 mt-1 truncate">{{ shop.description }}</p>
          <div class="text-xs text-gray-400 mt-2 flex gap-4">
            <span>{{ followerCount }} 粉丝</span>
            <span>{{ productTotal }} 在售商品</span>
          </div>
        </div>
        <div class="flex items-center gap-2 flex-shrink-0">
          <button
            class="h-9 px-4 rounded-full border border-gray-200 text-gray-600 text-sm hover:border-green-500 hover:text-green-600 transition-colors"
            title="联系客服"
            @click="handleContact"
          >
            客服
          </button>
          <button
            class="h-9 px-5 rounded-full text-sm text-white transition-colors"
            :class="followed ? 'bg-gray-400 hover:bg-gray-500' : 'bg-primary hover:bg-primary-600'"
            @click="toggleFollow"
          >
            {{ followed ? '已关注' : '+ 关注' }}
          </button>
        </div>
      </div>

      <!-- 店铺优惠券条 -->
      <div v-if="coupons.length" class="bg-white rounded-lg mt-4 p-4">
        <h3 class="text-sm font-medium text-gray-800 mb-3">店铺优惠券</h3>
        <div class="flex gap-3 overflow-x-auto pb-1">
          <div
            v-for="c in coupons"
            :key="c.id"
            class="flex-shrink-0 w-64 flex border border-primary-100 rounded-lg overflow-hidden"
          >
            <div class="w-20 flex-shrink-0 flex flex-col items-center justify-center bg-primary-50 border-r border-dashed border-primary-200 p-2">
              <template v-if="c.type === 2">
                <span class="text-xl font-bold text-primary">{{ c.discountValue }}</span>
                <span class="text-xs text-gray-500">折</span>
              </template>
              <template v-else>
                <span class="text-xl font-bold text-primary">¥{{ c.discountValue }}</span>
              </template>
              <span class="text-[10px] text-gray-500 mt-0.5 text-center">
                {{ c.type === 3 || c.minAmount <= 0 ? '无门槛' : `满¥${c.minAmount}可用` }}
              </span>
            </div>
            <div class="flex-1 p-2 flex flex-col justify-between min-w-0">
              <p class="font-medium text-gray-800 text-xs truncate">{{ c.name }}</p>
              <p class="text-[10px] text-gray-400 mt-0.5">有效期至 {{ (c.endTime || '').substring(0, 10) }}</p>
              <button
                v-if="!c._claimed"
                class="mt-1 self-start text-xs text-primary border border-primary rounded-full px-3 py-0.5 hover:bg-primary-50 transition-colors"
                :disabled="c._claiming"
                @click="claim(c)"
              >
                {{ c._claiming ? '领取中...' : '领取' }}
              </button>
              <span v-else class="mt-1 self-start text-xs text-gray-400">已领取 ✓</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 主体：左侧店内分类 + 右侧商品列表 -->
      <div class="flex gap-6 mt-4">
        <aside class="w-48 flex-shrink-0 hidden lg:block">
          <div class="bg-white rounded-lg p-3 sticky top-40">
            <button
              class="w-full text-left text-sm px-3 py-2 rounded transition-colors"
              :class="activeCategory === '' ? 'bg-primary-50 text-primary font-medium' : 'text-gray-700 hover:bg-gray-50'"
              @click="selectCategory('')"
            >
              全部宝贝
            </button>
            <button
              v-for="cat in shopCategories"
              :key="cat.categoryId"
              class="w-full text-left text-sm px-3 py-2 rounded transition-colors flex items-center justify-between gap-1"
              :class="activeCategory === cat.categoryName ? 'bg-primary-50 text-primary font-medium' : 'text-gray-600 hover:bg-gray-50'"
              @click="selectCategory(cat.categoryName)"
            >
              <span class="truncate">{{ cat.categoryName }}</span>
              <span class="text-xs text-gray-400 flex-shrink-0">{{ cat.count }}</span>
            </button>
          </div>
        </aside>

        <div class="flex-1 min-w-0">
          <!-- 排序栏（店内搜索复用顶部搜索框的「搜本店」） -->
          <div class="bg-white rounded-lg p-3 mb-4 flex items-center gap-2 sm:gap-3 text-sm flex-wrap">
            <button
              v-for="sort in sortOptions"
              :key="sort.value"
              :class="[
                'px-3 py-1.5 rounded transition-colors',
                currentSort === sort.value ? 'bg-primary text-white' : 'text-gray-600 hover:bg-gray-100'
              ]"
              @click="changeSort(sort.value)"
            >
              {{ sort.label }}
            </button>
            <div class="flex-1"></div>
            <span v-if="keyword" class="text-xs text-gray-400">店内搜索：{{ keyword }}</span>
          </div>

          <!-- 商品网格 -->
          <div v-if="products.length > 0" class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <ProductCard v-for="product in products" :key="product.id" :product="product" />
          </div>

          <!-- 空状态 -->
          <div v-else class="bg-white rounded-lg p-16 text-center">
            <div class="text-6xl mb-4">🔍</div>
            <p class="text-gray-400">该店铺暂无相关商品</p>
          </div>

          <!-- 分页 -->
          <div v-if="totalPages > 1" class="mt-6 flex justify-center gap-2">
            <button
              :disabled="currentPage <= 1"
              class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
              @click="goPage(currentPage - 1)"
            >
              上一页
            </button>
            <button
              v-for="page in visiblePages"
              :key="page"
              :class="[
                'px-4 py-2 border rounded text-sm transition-colors',
                page === currentPage ? 'bg-primary text-white border-primary' : 'hover:border-primary hover:text-primary'
              ]"
              @click="goPage(page)"
            >
              {{ page }}
            </button>
            <button
              :disabled="currentPage >= totalPages"
              class="px-4 py-2 border rounded text-sm disabled:opacity-50 hover:border-primary hover:text-primary transition-colors"
              @click="goPage(currentPage + 1)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { get, post, del } from '~/utils/request'
import { useUserStore } from '~/stores/user'
import { useImStore } from '~/stores/im'

const route = useRoute()
const userStore = useUserStore()
const imStore = useImStore()
const { openLoginModal } = useLoginModal()

const shopId = computed(() => Number(route.params.id))

// ========== 店铺信息 ==========
const shop = reactive({ id: 0, name: '', logo: '', description: '', selfOperated: 1, status: 1 })
const closed = ref(false)
const followed = ref(false)
const followerCount = ref(0)

// ========== 优惠券 ==========
const coupons = ref<any[]>([])

// ========== 商品列表 ==========
const shopCategories = ref<{ categoryId: number; categoryName: string; count: number }[]>([])
const activeCategory = ref('')
const sortOptions = [
  { label: '综合', value: 'default' },
  { label: '销量', value: 'sales' },
  { label: '新品', value: 'new' },
  { label: '价格升序', value: 'price_asc' },
  { label: '价格降序', value: 'price_desc' },
]
const currentSort = ref('default')
// 店内搜索关键词：复用顶部搜索框「搜本店」，经 URL query 传入
const keyword = ref((route.query.keyword as string) || '')
const currentPage = ref(1)
const pageSize = 20
const products = ref<any[]>([])
const productTotal = ref(0)

const totalPages = computed(() => Math.ceil(productTotal.value / pageSize))
const visiblePages = computed(() => {
  const pages: number[] = []
  const start = Math.max(1, currentPage.value - 2)
  const end = Math.min(totalPages.value, currentPage.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

// ========== 轻提示 ==========
const toast = reactive({ visible: false, message: '', type: 'success' })
let toastTimer: ReturnType<typeof setTimeout> | null = null
function showToast(message: string, type: 'success' | 'error' = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.visible = true
  toast.message = message
  toast.type = type
  toastTimer = setTimeout(() => { toast.visible = false }, 2500)
}

// ========== 数据加载 ==========
async function loadShop() {
  closed.value = false
  try {
    const data = await get(`/shop/detail/${shopId.value}`)
    if (!data || data.status !== 1) {
      closed.value = true
      return
    }
    shop.id = data.id
    shop.name = data.name || ''
    shop.logo = data.logo || ''
    shop.description = data.description || ''
    shop.selfOperated = data.selfOperated ?? 1
    shop.status = data.status
  } catch (e) {
    console.error('获取店铺信息失败:', e)
    closed.value = true
    return
  }
  loadFollowStatus()
  loadCoupons()
  loadCategories()
  fetchProducts()
}

async function loadFollowStatus() {
  try {
    const data = await get(`/user/shop-follow/status/${shopId.value}`)
    followed.value = !!data?.followed
    followerCount.value = data?.followerCount || 0
  } catch (e) {
    // 关注状态失败不影响主页浏览
  }
}

async function loadCoupons() {
  try {
    coupons.value = (await get(`/coupon/shop/${shopId.value}`)) || []
  } catch (e) {
    coupons.value = []
  }
}

async function loadCategories() {
  try {
    shopCategories.value = (await get(`/product/shop/${shopId.value}/categories`)) || []
  } catch (e) {
    shopCategories.value = []
  }
}

async function fetchProducts() {
  try {
    const data = await get('/product/list', {
      pageNum: currentPage.value,
      pageSize,
      shopId: shopId.value,
      keyword: keyword.value || undefined,
      category: activeCategory.value || undefined,
      sort: currentSort.value,
    })
    products.value = (data?.list || []).map((p: any) => ({
      id: p.id,
      title: p.name,
      image: p.mainImage,
      price: p.price || p.minPrice,
      originalPrice: p.originalPrice,
      salesCount: p.salesCount,
    }))
    productTotal.value = data?.total || 0
  } catch (e) {
    console.error('获取店铺商品失败:', e)
    products.value = []
    productTotal.value = 0
  }
}

// ========== 交互 ==========
function selectCategory(name: string) {
  activeCategory.value = name
  currentPage.value = 1
  fetchProducts()
}

function changeSort(sort: string) {
  currentSort.value = sort
  currentPage.value = 1
  fetchProducts()
}

function goPage(page: number) {
  currentPage.value = page
  fetchProducts()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** 关注/取消关注 */
async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    openLoginModal()
    return
  }
  try {
    if (followed.value) {
      await del(`/user/shop-follow/${shopId.value}`)
      showToast('已取消关注')
    } else {
      await post(`/user/shop-follow/${shopId.value}`)
      showToast('关注成功')
    }
    await loadFollowStatus()
  } catch (e: any) {
    if (!e?.silent) showToast(e?.message || '操作失败', 'error')
  }
}

/** 领取店铺券 */
async function claim(c: any) {
  if (!userStore.isLoggedIn) {
    openLoginModal()
    return
  }
  c._claiming = true
  try {
    await post(`/coupon/claim/${c.id}`)
    c._claimed = true
    showToast('领取成功')
  } catch (e: any) {
    if (!e?.silent) showToast(e?.message || '领取失败', 'error')
  } finally {
    c._claiming = false
  }
}

/** 联系客服：发起与该店铺的 IM 会话 */
function handleContact() {
  if (!userStore.isLoggedIn) {
    openLoginModal()
    return
  }
  imStore.startWithContext({ shopId: shopId.value, shopName: shop.name, entry: 'shop' })
}

// 顶部「搜本店」更新 query 时重新拉取店内商品
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
  currentPage.value = 1
  fetchProducts()
})

// 店铺切换（从一个店铺页跳另一个店铺页）时重置并重载
watch(shopId, () => {
  shopCategories.value = []
  activeCategory.value = ''
  currentSort.value = 'default'
  keyword.value = (route.query.keyword as string) || ''
  currentPage.value = 1
  coupons.value = []
  products.value = []
  productTotal.value = 0
  followed.value = false
  followerCount.value = 0
  loadShop()
})

onMounted(() => {
  loadShop()
})
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
