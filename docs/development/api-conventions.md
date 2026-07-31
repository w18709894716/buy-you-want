# API 规范

## 统一响应格式
所有 API 返回统一的 `R<T>` 格式：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1718524800000
}
```

### 常用方法
```java
R.ok()                    // 成功，无数据
R.ok(data)                // 成功，带数据
R.ok(message, data)       // 成功，自定义消息+数据
R.fail()                  // 失败
R.fail(message)           // 失败，自定义消息
R.fail(code, message)     // 失败，自定义code+消息
R.fail(ResultCode)        // 失败，使用枚举
```

## 错误码一览

| 枚举名 | Code | Message | 模块 |
|--------|------|---------|------|
| SUCCESS | 200 | 操作成功 | 通用 |
| FAIL | 500 | 操作失败 | 通用 |
| UNAUTHORIZED | 401 | 未登录或Token已过期 | 认证 |
| FORBIDDEN | 403 | 无权限访问 | 认证 |
| NOT_FOUND | 404 | 资源不存在 | 通用 |
| PARAM_ERROR | 400 | 参数错误 | 校验 |
| PARAM_MISSING | 400 | 缺少必要参数 | 校验 |
| USER_NOT_FOUND | 1001 | 用户不存在 | 用户 |
| USER_DISABLED | 1002 | 用户已被禁用 | 用户 |
| USERNAME_EXISTS | 1003 | 用户名已存在 | 用户 |
| PHONE_EXISTS | 1004 | 手机号已存在 | 用户 |
| PASSWORD_ERROR | 1005 | 密码错误 | 用户 |
| SMS_CODE_ERROR | 1006 | 验证码错误 | 用户 |
| SMS_CODE_EXPIRED | 1007 | 验证码已过期 | 用户 |
| PRODUCT_NOT_FOUND | 2001 | 商品不存在 | 商品 |
| PRODUCT_OFF_SHELF | 2002 | 商品已下架 | 商品 |
| SKU_NOT_FOUND | 2003 | SKU不存在 | 商品 |
| STOCK_NOT_ENOUGH | 2004 | 库存不足 | 商品 |
| CART_ITEM_NOT_FOUND | 3001 | 购物车商品不存在 | 购物车 |
| ORDER_NOT_FOUND | 4001 | 订单不存在 | 订单 |
| ORDER_STATUS_ERROR | 4002 | 订单状态异常 | 订单 |
| ORDER_ALREADY_PAID | 4003 | 订单已支付 | 订单 |
| ORDER_TIMEOUT | 4004 | 订单已超时 | 订单 |
| PAY_FAILED | 5001 | 支付失败 | 支付 |
| PAY_CHANNEL_ERROR | 5002 | 支付渠道异常 | 支付 |
| REFUND_FAILED | 5003 | 退款失败 | 支付 |
| COUPON_NOT_FOUND | 6001 | 优惠券不存在 | 营销 |
| COUPON_EXPIRED | 6002 | 优惠券已过期 | 营销 |
| COUPON_ALREADY_CLAIMED | 6003 | 已领取过该优惠券 | 营销 |
| COUPON_NOT_ENOUGH | 6004 | 优惠券已领完 | 营销 |
| SECKILL_NOT_START | 6005 | 秒杀未开始 | 营销 |
| SECKILL_ENDED | 6006 | 秒杀已结束 | 营销 |
| SECKILL_SOLD_OUT | 6007 | 秒杀已售罄 | 营销 |
| SECKILL_REPEAT | 6008 | 不能重复秒杀 | 营销 |
| REVIEW_ALREADY_EXISTS | 7001 | 已评价过该订单 | 评价 |
| ORDER_NOT_COMPLETED | 7002 | 订单未完成，无法评价 | 评价 |
| SYSTEM_ERROR | 9999 | 系统内部错误 | 系统 |
| RATE_LIMIT | 9001 | 请求过于频繁，请稍后再试 | 系统 |
| IDEMPOTENT_ERROR | 9002 | 请勿重复提交 | 系统 |

### 错误码规则
- **1xxx**: 用户模块
- **2xxx**: 商品模块
- **3xxx**: 购物车模块
- **4xxx**: 订单模块
- **5xxx**: 支付模块
- **6xxx**: 营销模块
- **7xxx**: 评价模块
- **9xxx**: 系统级

## 认证与权限（RBAC）
- 登录成功后返回 JWT Token，请求时携带：`Authorization: Bearer {token}`
- Gateway 的 AuthGlobalFilter 统一软认证：解析 Token 后将用户身份（用户ID / 角色 / shop_id / **用户类型 X-User-Type**）通过请求头透传给下游服务；剥离外部伪造的身份头
- 白名单路径（如 `/auth/login`、`/auth/register`）无需 Token

### 用户类型 X-User-Type
三端账号体系彻底分离，`X-User-Type` 头标识主体类型：

| userType | 说明 | 登录主体表 |
|----------|------|-----------|
| c | C 端会员 | `t_user`（纯会员，已移除 role 字段） |
| sys | 平台员工 | `t_sys_user`（管理端登录，role 统一 platform_admin） |
| merchant | 商家账号 | `t_merchant_account`（主账号 parent_id=NULL，子账号绑角色） |

### 角色（登录态粗粒度）
| 角色 | 说明 |
|------|------|
| user | 普通用户（C 端） |
| platform_admin | 平台员工（管理端，细粒度权限由 RBAC 决定） |
| merchant_owner | 商家主账号（拥有本店全部权限，权限集为通配 `*`） |
| merchant_staff | 商家子账号（按预设角色绑定的权限集受限） |

### RBAC 五表模型（byw_user 库，byw-user 服务持有）
| 表 | 说明 |
|----|------|
| t_sys_user | 平台员工账号 |
| t_sys_role | 角色（role_code 唯一，scope=platform/merchant，is_preset 内置不可删） |
| t_sys_menu | 菜单/权限（menu_type：1目录 2菜单 3按钮；perm_code 即权限标识） |
| t_sys_user_role | 用户-角色关联（user_type：1平台员工 2商家账号） |
| t_sys_role_menu | 角色-菜单关联 |

byw-admin / byw-merchant 为无库 BFF，经 `RbacFeignClient` 转发 byw-user 的 `/feign/rbac/**` 契约。

### 权限校验：@RequirePerm + Redis
- 权限**不进 JWT**。登录时聚合用户的 perm_code 写入 Redis Set：`auth:perms:{userType}:{userId}`，与 Token 同 24h TTL
- 通配 `*` 表示全部权限（超级管理员角色、商家主账号）
- `@RequirePerm("xxx:yyy")` 拦截器从 Redis 校验（`PermissionChecker` 直读 Redis，未命中抛 FORBIDDEN）；方法级注解覆盖类级
- 调整授权后删除对应 `auth:perms:*` key 即时生效，无需用户重登
- `refreshToken` 续期 perms key，`logout` 删除 perms key
- 旧注解 `@RequireAdmin` / `@RequireRole` 保留兼容
- 商家端业务统一按 `shop_id` 隔离，确保商家仅访问本店数据

### 权限标识清单
格式 `模块:操作`，与菜单/按钮一一对应。

**平台端（scope=platform）**：`member:list`、`shop:audit`、`shop:list`、`product:list`、`product:audit`、`category:manage`、`brand:manage`、`order:list`、`coupon:manage`、`seckill:manage`、`banner:manage`、`review:manage`、`logistics:list`、`settle:commission`、`settle:withdraw`、`sys:user`、`sys:role`

**商家端（scope=merchant）**：`m:product:list`、`m:product:publish`、`m:order:list`、`m:order:ship`、`m:aftersale:manage`、`m:im:workbench`、`m:coupon:manage`、`m:review:manage`、`m:shop:info`、`m:settle:manage`、`m:staff:manage`

> 说明：IM 端点（`/im/**`）由买家与商家共享，仍用 `@RequireLogin`，运行时按 `UserContext` 区分数据范围；商家侧 `m:im:workbench` 仅用于前端菜单/按钮可见性控制。

### 前端权限渲染
- 登录后调用 `/admin/me/menus`（或 `/merchant/me/menus`）获取菜单树 + 权限集，存 Pinia + localStorage
- 静态路由表 + `meta.perm`：路由守卫校验失败跳 `/403`
- 菜单按后端下发的菜单树动态渲染；按钮级用 `v-perm="'xxx:yyy'"` 指令控制

## Gateway 路由规则

| 路径匹配 | 目标服务 | 说明 |
|---------|---------|------|
| /api/admin/auth/** | byw-auth | 管理端登录/注册（StripPrefix=2，免 Token） |
| /api/admin/** | byw-admin | 管理端 BFF，StripPrefix=1 |
| /api/auth/** | byw-auth | 认证相关（免 Token） |
| /api/user/** | byw-user | 用户相关 |
| /api/product/** | byw-product | 商品相关 |
| /api/category/** | byw-product | 分类相关 |
| /api/search/** | byw-product | 搜索相关 |
| /api/brand/** | byw-product | 品牌相关 |
| /api/banner/** | byw-product | 首页 Banner |
| /api/cart/** | byw-cart | 购物车 |
| /api/order/** | byw-order | 订单相关 |
| /api/pay/** | byw-pay | 支付相关 |
| /api/logistics/** | byw-logistics | 物流相关 |
| /api/review/** | byw-review | 评价相关 |
| /api/promotion/** | byw-promotion | 营销相关 |
| /api/coupon/** | byw-promotion | 优惠券 |
| /api/seckill/** | byw-promotion | 秒杀 |
| /api/file/** | byw-file | 文件/图片上传 |
| /api/shop/** | byw-shop | 店铺与商家账号 |
| /api/merchant/** | byw-merchant | 商家端 BFF |

> **结算服务（byw-settle）无对外网关路由**，仅由 byw-merchant / byw-admin BFF 通过 Feign 内部调用（余额、佣金规则、提现与审批）。

## API 文档

各微服务集成 Knife4j，Gateway 通过 SpringDoc 聚合所有服务的 API 文档：

- **Gateway 聚合文档**: http://localhost:8080/swagger-ui.html（下拉切换各服务）
- **各服务独立文档 (Knife4j)**: http://localhost:{port}/doc.html（直接访问服务端口）
