import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'
import { getToken } from '../utils/token'
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
      },
      {
        path: 'system/menu',
        name: 'SystemMenu',
        component: () => import('../views/system/menu.vue'),
        meta: { title: '菜单管理', perm: 'sys:menu' }
      },
      {
        path: 'system/operation-log',
        name: 'SystemOperationLog',
        component: () => import('../views/system/operation-log.vue'),
        meta: { title: '操作日志', perm: 'sys:log' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 权限集本地缓存的有效期。超过后守卫会重新拉取：后台改授权后，最迟这么久就会在界面上生效，
 * 而不是一直沿用 localStorage 里那份永不更新的旧权限。
 */
const PERMS_CACHE_TTL_MS = 10 * 60 * 1000

// 路由守卫：登录校验 + 页面级权限校验
router.beforeEach(async (to, from, next) => {
  const token = getToken()
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
    // 只看时间戳，不看 perms 是否为空：确实没有任何权限的账号也是"已加载"，
    // 若把它当作未加载，每次路由切换都会重打一次 /admin/me/menus。
    const cacheFresh = store.permsCachedAt > 0
      && Date.now() - store.permsCachedAt < PERMS_CACHE_TTL_MS
    // permsKnown 表示"这次确实拿到了权限集"。拉取失败时它是 false：
    // 此时**不做**前端权限判断、直接放行，由后端接口鉴权兜底。
    // 原实现拉取失败也继续往下走 hasPerm，而权限集为空时 hasPerm 恒为 false，
    // 结果是后端一抖动所有人（含超管）都被丢到 /403 —— 注释写的"不阻断跳转"与行为正好相反。
    let permsKnown = cacheFresh
    if (!cacheFresh) {
      try {
        await store.fetchMenus()
        permsKnown = true
      } catch {
        // 保留已有缓存（可能为空），但不据此拒绝导航
        console.warn('[router] 拉取菜单/权限失败，本次跳过前端权限校验，交由后端鉴权兜底')
      }
    }
    const perm = to.meta?.perm as string | undefined
    if (permsKnown && perm && !hasPerm(perm)) {
      next('/403')
      return
    }
  }
  next()
})

export default router
