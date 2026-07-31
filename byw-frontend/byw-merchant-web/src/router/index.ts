import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue')
  },
  {
    path: '/apply',
    name: 'MerchantApply',
    component: () => import('../views/apply/index.vue'),
    meta: { title: '商家入驻' }
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: '控制台' }
      },
      {
        path: 'product/list',
        name: 'ProductList',
        component: () => import('../views/product/list.vue'),
        meta: { title: '商品列表' }
      },
      {
        path: 'product/add',
        name: 'ProductAdd',
        component: () => import('../views/product/add.vue'),
        meta: { title: '发布商品' }
      },
      {
        path: 'product/add/:id',
        name: 'ProductEdit',
        component: () => import('../views/product/add.vue'),
        meta: { title: '编辑商品' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('../views/order/list.vue'),
        meta: { title: '订单列表' }
      },
      {
        path: 'order/after-sale',
        name: 'AfterSaleList',
        component: () => import('../views/order/after-sale.vue'),
        meta: { title: '售后管理' }
      },
      {
        path: 'im',
        name: 'ImWorkbench',
        component: () => import('../views/im/index.vue'),
        meta: { title: '客服工作台' }
      },
      {
        path: 'promotion/coupon',
        name: 'CouponManage',
        component: () => import('../views/promotion/coupon.vue'),
        meta: { title: '店铺优惠券' }
      },
      {
        path: 'review/list',
        name: 'ReviewList',
        component: () => import('../views/review/list.vue'),
        meta: { title: '评价管理' }
      },
      {
        path: 'shop/info',
        name: 'ShopInfo',
        component: () => import('../views/shop/info.vue'),
        meta: { title: '店铺设置' }
      },
      {
        path: 'settle/index',
        name: 'SettleIndex',
        component: () => import('../views/settle/index.vue'),
        meta: { title: '结算与提现' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：检查登录状态（/login、/apply 为免登录页）
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('merchant_token')
  const publicPaths = ['/login', '/apply']
  if (!publicPaths.includes(to.path) && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
