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
        path: 'user/list',
        name: 'UserList',
        component: () => import('../views/user/list.vue'),
        meta: { title: '会员列表', perm: 'member:list' }
      },
      {
        path: 'shop/merchant',
        name: 'MerchantAudit',
        component: () => import('../views/shop/merchant.vue'),
        meta: { title: '入驻审核', perm: 'shop:audit' }
      },
      {
        path: 'shop/list',
        name: 'ShopList',
        component: () => import('../views/shop/list.vue'),
        meta: { title: '店铺管理', perm: 'shop:list' }
      },
      {
        path: 'product/list',
        name: 'ProductList',
        component: () => import('../views/product/list.vue'),
        meta: { title: '商品列表', perm: 'product:list' }
      },
      {
        path: 'product/audit',
        name: 'ProductAudit',
        component: () => import('../views/product/audit.vue'),
        meta: { title: '商品审核', perm: 'product:audit' }
      },
      {
        path: 'product/category',
        name: 'CategoryManage',
        component: () => import('../views/product/category.vue'),
        meta: { title: '分类管理', perm: 'category:manage' }
      },
      {
        path: 'product/brand',
        name: 'BrandManage',
        component: () => import('../views/product/brand.vue'),
        meta: { title: '品牌管理', perm: 'brand:manage' }
      },
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('../views/order/list.vue'),
        meta: { title: '订单列表', perm: 'order:list' }
      },
      {
        path: 'promotion/coupon',
        name: 'CouponManage',
        component: () => import('../views/promotion/coupon.vue'),
        meta: { title: '优惠券管理', perm: 'coupon:manage' }
      },
      {
        path: 'promotion/seckill',
        name: 'SeckillManage',
        component: () => import('../views/promotion/seckill.vue'),
        meta: { title: '秒杀管理', perm: 'seckill:manage' }
      },
      {
        path: 'promotion/banner',
        name: 'BannerManage',
        component: () => import('../views/promotion/banner.vue'),
        meta: { title: '轮播图管理', perm: 'banner:manage' }
      },
      {
        path: 'review/list',
        name: 'ReviewList',
        component: () => import('../views/review/list.vue'),
        meta: { title: '评论管理', perm: 'review:manage' }
      },
      {
        path: 'logistics/list',
        name: 'LogisticsList',
        component: () => import('../views/logistics/list.vue'),
        meta: { title: '物流管理', perm: 'logistics:list' }
      },
      {
        path: 'settle/commission',
        name: 'CommissionRule',
        component: () => import('../views/settle/commission.vue'),
        meta: { title: '佣金规则', perm: 'settle:commission' }
      },
      {
        path: 'settle/withdraw',
        name: 'WithdrawAudit',
        component: () => import('../views/settle/withdraw.vue'),
        meta: { title: '提现审批', perm: 'settle:withdraw' }
      },
      {
        path: 'system/user',
        name: 'SystemUser',
        component: () => import('../views/system/user.vue'),
        meta: { title: '员工管理', perm: 'sys:user' }
      },
      {
        path: 'system/role',
        name: 'SystemRole',
        component: () => import('../views/system/role.vue'),
        meta: { title: '角色管理', perm: 'sys:role' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：登录校验 + 页面级权限校验
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/dashboard')
    return
  }
  // 已登录访问业务页：确保菜单/权限已加载，再做 perm 校验
  if (token && to.path !== '/login' && to.path !== '/403') {
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
