import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue')
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
        path: 'user/list',
        name: 'UserList',
        component: () => import('../views/user/list.vue'),
        meta: { title: '用户列表' }
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
        meta: { title: '添加商品' }
      },
      {
        path: 'product/add/:id',
        name: 'ProductEdit',
        component: () => import('../views/product/add.vue'),
        meta: { title: '编辑商品' }
      },
      {
        path: 'product/category',
        name: 'CategoryManage',
        component: () => import('../views/product/category.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: 'product/brand',
        name: 'BrandManage',
        component: () => import('../views/product/brand.vue'),
        meta: { title: '品牌管理' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('../views/order/list.vue'),
        meta: { title: '订单列表' }
      },
      {
        path: 'promotion/coupon',
        name: 'CouponManage',
        component: () => import('../views/promotion/coupon.vue'),
        meta: { title: '优惠券管理' }
      },
      {
        path: 'promotion/seckill',
        name: 'SeckillManage',
        component: () => import('../views/promotion/seckill.vue'),
        meta: { title: '秒杀管理' }
      },
      {
        path: 'review/list',
        name: 'ReviewList',
        component: () => import('../views/review/list.vue'),
        meta: { title: '评论管理' }
      },
      {
        path: 'logistics/list',
        name: 'LogisticsList',
        component: () => import('../views/logistics/list.vue'),
        meta: { title: '物流管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：检查登录状态
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router
