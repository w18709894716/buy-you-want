import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { hasPerm } from '../utils/permission'
import { useUserStore } from '../stores/user'

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
    path: '/403',
    name: 'Forbidden',
    component: () => import('../views/error/403.vue')
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
        meta: { title: '商品列表', perm: 'm:product:list' }
      },
      {
        path: 'product/add',
        name: 'ProductAdd',
        component: () => import('../views/product/add.vue'),
        meta: { title: '发布商品', perm: 'm:product:publish' }
      },
      {
        path: 'product/add/:id',
        name: 'ProductEdit',
        component: () => import('../views/product/add.vue'),
        meta: { title: '编辑商品', perm: 'm:product:publish' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('../views/order/list.vue'),
        meta: { title: '订单列表', perm: 'm:order:list' }
      },
      {
        path: 'order/after-sale',
        name: 'AfterSaleList',
        component: () => import('../views/order/after-sale.vue'),
        meta: { title: '售后管理', perm: 'm:aftersale:manage' }
      },
      {
        path: 'im',
        name: 'ImWorkbench',
        component: () => import('../views/im/index.vue'),
        meta: { title: '客服工作台', perm: 'm:im:workbench' }
      },
      {
        path: 'promotion/coupon',
        name: 'CouponManage',
        component: () => import('../views/promotion/coupon.vue'),
        meta: { title: '店铺优惠券', perm: 'm:coupon:manage' }
      },
      {
        path: 'review/list',
        name: 'ReviewList',
        component: () => import('../views/review/list.vue'),
        meta: { title: '评价管理', perm: 'm:review:manage' }
      },
      {
        path: 'shop/info',
        name: 'ShopInfo',
        component: () => import('../views/shop/info.vue'),
        meta: { title: '店铺设置', perm: 'm:shop:info' }
      },
      {
        path: 'settle/index',
        name: 'SettleIndex',
        component: () => import('../views/settle/index.vue'),
        meta: { title: '结算与提现', perm: 'm:settle:manage' }
      },
      {
        path: 'staff/index',
        name: 'StaffManage',
        component: () => import('../views/staff/index.vue'),
        meta: { title: '员工管理', perm: 'm:staff:manage' }
      },
      {
        path: 'role/index',
        name: 'RoleManage',
        component: () => import('../views/role/index.vue'),
        meta: { title: '角色管理', perm: 'm:role:manage' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录校验（/login、/apply 免登录）+ 页面级权限校验
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('merchant_token')
  const publicPaths = ['/login', '/apply']
  if (!publicPaths.includes(to.path) && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }
  // 已登录访问业务页：确保菜单/权限已加载，再做 perm 校验
  if (token && !publicPaths.includes(to.path) && to.path !== '/403') {
    const store = useUserStore()
    if (store.perms.length === 0 && store.menus.length === 0) {
      try {
        await store.fetchMenus()
      } catch {
        // 拉取失败不阻断跳转，交由后端接口鉴权兜底
      }
    }
    const perm = to.meta?.perm as string | undefined
    if (perm && !hasPerm(perm)) {
      next('/403')
      return
    }
  }
  next()
})

export default router
