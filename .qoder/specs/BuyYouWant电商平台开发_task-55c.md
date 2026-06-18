# BuyYouWant 电商平台全量开发计划

## Context
用户需要从零搭建一个类淘宝电商平台（buy-you-want），采用Java微服务架构，全量开发所有核心模块。项目目录 `e:\IdeaProjects\buy-you-want` 当前为空。

**注意**: 当前系统Java版本为1.8，需先安装JDK 17才能使用Spring Boot 3.x。

## 技术栈
- **后端**: Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, Spring Cloud Alibaba 2023.0.1.0
- **数据**: MySQL 8.0, Redis 7.x, Elasticsearch 8.x (+IK分词), MongoDB 7.x
- **消息**: Kafka 3.x (KRaft模式)
- **事务**: Seata 2.0 AT模式
- **ORM**: MyBatis-Plus 3.5.6
- **注册/配置**: Nacos 2.3.x
- **前端**: Vue3 (Nuxt.js用户端 + Element Plus管理端)
- **存储**: MinIO (对象存储)

## 项目结构 (Maven多模块)
```
buy-you-want/
├── pom.xml                    # 父POM (dependencyManagement)
├── byw-common/                # 公共模块 (core/redis/mybatis/security/kafka/es/swagger/log)
├── byw-api/                   # Feign接口定义 (user/product/order/cart/pay/logistics/review/promotion)
├── byw-gateway/               # Spring Cloud Gateway 网关 :8080
├── byw-auth/                  # 认证中心 :8081
├── byw-user/                  # 用户中心 :8082
├── byw-product/               # 商品中心 :8083
├── byw-cart/                  # 购物车 :8084
├── byw-order/                 # 订单中心 :8085
├── byw-pay/                   # 支付中心 :8086
├── byw-logistics/             # 物流中心 :8087
├── byw-review/                # 评价系统 :8088
├── byw-promotion/             # 营销中心 :8089
├── byw-admin/                 # 管理后台BFF层 :8090
├── byw-frontend/
│   ├── byw-web/               # Nuxt.js 用户端
│   └── byw-admin-web/         # Element Plus 管理端
└── docs/database/             # SQL脚本
```

## 开发任务 (按依赖顺序)

### Task 1: 基础骨架搭建
- 创建父工程POM (统一版本管理)
- 创建8个common子模块:
  - `byw-common-core`: R\<T\>统一响应, 全局异常处理, BaseEntity, 雪花ID, 工具类
  - `byw-common-redis`: Redisson配置, 分布式锁, RedisUtil
  - `byw-common-mybatis`: MyBatis-Plus配置, 分页插件, 自动填充
  - `byw-common-security`: JWT工具, UserContext (ThreadLocal), 鉴权拦截器
  - `byw-common-kafka`: Kafka生产者/消费者配置, Topic常量
  - `byw-common-elasticsearch`: ES客户端配置
  - `byw-common-swagger`: Knife4j统一配置
  - `byw-common-log`: 操作日志AOP
- 创建 `.gitignore`, `docs/` 目录

### Task 2: API模块定义
- 创建8个byw-api子模块，定义Feign接口和DTO:
  - `byw-api-user`: UserFeignClient, UserDTO
  - `byw-api-product`: ProductFeignClient, ProductDTO, SkuDTO
  - `byw-api-cart`: CartFeignClient, CartDTO
  - `byw-api-order`: OrderFeignClient, OrderCreateDTO, OrderDetailDTO
  - `byw-api-pay`: PayFeignClient, PayDTO
  - `byw-api-logistics`: LogisticsFeignClient
  - `byw-api-review`: ReviewFeignClient
  - `byw-api-promotion`: PromotionFeignClient, CouponDTO

### Task 3: Gateway网关 + Auth认证中心
- **byw-gateway**: 路由配置(lb://), AuthGlobalFilter(JWT校验+白名单), Sentinel限流, 跨域
- **byw-auth**: 密码登录/注册/Token刷新, 策略模式(密码/短信), 对接byw-api-user获取用户信息

### Task 4: 用户中心 (byw-user)
- 建库建表: `byw_user.sql` (t_user, t_user_address, t_user_level)
- 用户CRUD, 收货地址CRUD, 用户等级
- 实现byw-api-user Feign接口, Redis缓存用户信息

### Task 5: 商品中心 (byw-product)
- 建库建表: `byw_product.sql` (t_category, t_brand, t_product, t_sku)
- 三级分类CRUD + Redis缓存, 品牌CRUD
- SPU/SKU管理 (多规格笛卡尔积生成)
- ES索引创建 + Kafka同步商品到ES + ES搜索(关键词/分类/价格/排序)
- 库存扣减(Redis预扣Lua + Kafka异步落库)

### Task 6: 购物车 (byw-cart)
- 建库建表: `byw_cart.sql` (t_cart_item)
- Redis Hash + MySQL双写
- 加购/修改/删除/全选/结算

### Task 7: 订单中心 (byw-order)
- 建库建表: `byw_order.sql` (t_order, t_order_item, t_order_status_log)
- 雪花算法订单号生成
- 创建订单(Seata @GlobalTransactional: 订单+扣库存+核销券+清购物车)
- 订单状态机, 超时自动取消(Kafka延迟/定时任务)
- Kafka: 订单事件生产, 支付结果消费

### Task 8: 支付中心 (byw-pay)
- 建库建表: `byw_pay.sql` (t_pay_order, t_refund_record)
- 策略模式(AlipayStrategy/WechatPayStrategy), 模拟支付+回调
- Kafka通知订单支付结果, 退款处理

### Task 9: 物流中心 (byw-logistics)
- 建库建表: `byw_logistics.sql` (t_logistics_order, t_logistics_trace)
- 发货/物流跟踪(模拟)/状态更新 + Kafka通知

### Task 10: 评价系统 (byw-review)
- MySQL基础表 + MongoDB详情集合
- 发布评价/追加评价/评价列表(筛选)/评分统计

### Task 11: 营销中心 (byw-promotion)
- 建库建表: `byw_promotion.sql` (t_coupon, t_coupon_record, t_seckill_activity, t_seckill_order, t_group_buy_activity)
- 优惠券(模板CRUD + Redis防超发 + 满减计算)
- 秒杀(Redis Lua预扣 + Sentinel限流 + Kafka异步创单)
- 拼团(成团逻辑)

### Task 12: 管理后台BFF (byw-admin)
- 聚合各微服务数据，提供管理端专用API
- 管理端Dashboard统计接口

### Task 13: 前端 - 用户端 (byw-frontend/byw-web)
- Nuxt.js SSR项目初始化
- 页面: 首页/登录/注册/搜索/商品详情/购物车/结算/支付/个人中心(订单/地址/优惠券/评价)
- Axios封装 + Token管理 + Pinia状态管理

### Task 14: 前端 - 管理端 (byw-frontend/byw-admin-web)
- Vue3 + Element Plus + Vite项目初始化
- 页面: 登录/Dashboard/用户管理/商品管理(发布+分类+品牌)/订单管理/营销管理/评价审核/物流管理
- 通用组件: 图片上传, 富文本编辑器, SKU表单

## 关键设计决策
| 场景 | 方案 |
|------|------|
| 购物车存储 | Redis Hash + MySQL双写 |
| 库存扣减 | Redis Lua预扣 → Kafka异步落库 |
| 分布式事务 | Seata AT模式 (下单核心链路) |
| 最终一致 | Kafka事件驱动 (销量/物流/积分等非核心链路) |
| 订单超时取消 | Kafka延迟消息 + 定时任务兜底 |
| 秒杀防刷 | Redis标记位 + Lua库存扣减 + Sentinel限流 |
| 搜索 | ES + IK分词 |
| 评价存储 | MySQL(统计索引) + MongoDB(灵活详情) |

## 验证方式
1. 每完成一个微服务Task，`mvn clean compile` 验证编译通过
2. 每完成一个common/api模块，`mvn clean install` 安装到本地仓库
3. 全部后端完成后，启动Nacos + 各中间件，逐个启动微服务验证注册
4. 通过Knife4j Swagger文档测试各服务API
5. 通过Gateway统一入口测试端到端链路 (注册→登录→浏览→加购→下单→支付→评价)
6. 前端联调验证完整用户流程
