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

## 认证方式
- 登录成功后返回 JWT Token
- 请求时携带：`Authorization: Bearer {token}`
- Gateway 的 AuthGlobalFilter 统一校验 Token
- 白名单路径（如 `/auth/login`, `/auth/register`）无需 Token

## Gateway 路由规则

| 路径匹配 | 目标服务 | 说明 |
|---------|---------|------|
| /api/auth/** | byw-auth | 认证相关 |
| /api/user/** | byw-user | 用户相关 |
| /api/product/** | byw-product | 商品相关 |
| /api/category/** | byw-product | 分类相关 |
| /api/search/** | byw-product | 搜索相关 |
| /api/brand/** | byw-product | 品牌相关 |
| /api/cart/** | byw-cart | 购物车 |
| /api/order/** | byw-order | 订单相关 |
| /api/pay/** | byw-pay | 支付相关 |
| /api/logistics/** | byw-logistics | 物流相关 |
| /api/review/** | byw-review | 评价相关 |
| /api/promotion/** | byw-promotion | 营销相关 |
| /api/coupon/** | byw-promotion | 优惠券 |
| /api/seckill/** | byw-promotion | 秒杀 |

## API 文档
项目集成 Knife4j，启动后可通过以下地址访问 API 文档：
- Gateway 聚合文档: http://localhost:8080/doc.html
- 各服务独立文档: http://localhost:{port}/doc.html
