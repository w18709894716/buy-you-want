# 系统架构

## 整体架构图

```mermaid
graph TB
    subgraph 客户端层
        A1[用户端 Nuxt.js SSR :3000]
        A2[管理端 Vue3 + Element Plus :5174]
    end

    subgraph 接入层
        B1[Spring Cloud Gateway :8080]
        B2[JWT 全局过滤器鉴权]
        B3[Sentinel 限流熔断]
    end

    subgraph 微服务层
        C1[byw-auth 认证中心 :8081]
        C2[byw-user 用户中心 :8082]
        C3[byw-product 商品中心 :8083]
        C4[byw-cart 购物车 :8084]
        C5[byw-order 订单中心 :8085]
        C6[byw-pay 支付中心 :8086]
        C7[byw-logistics 物流中心 :8087]
        C8[byw-review 评价系统 :8088]
        C9[byw-promotion 营销中心 :8089]
        C10[byw-admin 管理BFF :8090]
    end

    subgraph 基础设施层
        D1[Nacos 注册/配置中心]
        D2[Redis 7.x]
        D3[MySQL 8.0]
        D4[Kafka 3.7.0]
        D5[Elasticsearch 8.13]
        D6[MongoDB 7.0]
        D7[Seata 分布式事务]
    end

    A1 --> B1
    A2 --> B1
    B1 --> B2
    B1 --> B3
    B1 --> C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C10

    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C10 --> D1
    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 --> D2
    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 --> D3
    C5 & C6 & C7 & C9 --> D4
    C3 --> D5
    C8 --> D6
    C5 --> D7
```

## 微服务划分

| 服务名 | 端口 | 职责 | 依赖中间件 |
|--------|------|------|-----------|
| byw-gateway | 8080 | API 网关路由、JWT 鉴权、限流 | Nacos, Redis, Sentinel |
| byw-auth | 8081 | 注册、登录、Token 签发与刷新 | MySQL, Redis, Nacos |
| byw-user | 8082 | 用户 CRUD、收货地址、会员等级 | MySQL, Redis, Nacos |
| byw-product | 8083 | 分类/品牌/SPU-SKU 管理、库存、ES 搜索 | MySQL, Redis, ES, Nacos |
| byw-cart | 8084 | 购物车增删改查、结算 | MySQL, Redis, Nacos |
| byw-order | 8085 | 订单创建、状态机、超时取消、Seata 事务 | MySQL, Redis, Kafka, Seata, Nacos |
| byw-pay | 8086 | 支付策略、模拟回调、支付流水 | MySQL, Redis, Kafka, Nacos |
| byw-logistics | 8087 | 发货管理、物流跟踪、状态更新 | MySQL, Kafka, Nacos |
| byw-review | 8088 | 评价管理、评分统计 | MySQL, MongoDB, Redis, Nacos |
| byw-promotion | 8089 | 优惠券、秒杀（Lua预扣+限流）、拼团 | MySQL, Redis, Kafka, Nacos |
| byw-admin | 8090 | 管理后台 BFF 聚合层 | Nacos |
| byw-common | — | 公共工具模块（8 个子模块） | — |

## 服务间通信

### 同步调用（OpenFeign）
通过 `byw-api` 模块定义 Feign 接口和 DTO，各服务声明式调用其他服务接口，适合需要实时响应的场景：
- Order → Product（校验商品、扣减库存）
- Order → Cart（清空已结算商品）
- Order → Promotion（核销优惠券）
- Admin → 各业务服务（聚合查询）

### 异步调用（Kafka）
通过 Kafka 发布/订阅事件，实现跨服务最终一致性和削峰填谷：
- **支付成功** → Pay 发送事件 → Order 更新状态为待发货
- **库存扣减** → Promotion 秒杀预扣 → Order 异步创建订单
- **物流状态变更** → Logistics 发送事件 → Order 更新物流状态
- **订单完成** → Order 发送事件 → Review 开放评价入口、Promotion 结算优惠券

## 核心业务流程

### 1. 下单流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant GW as Gateway
    participant O as Order服务
    participant P as Product服务
    participant PR as Promotion服务
    participant C as Cart服务

    U->>GW: POST /api/order/create
    GW->>O: 转发请求（携带JWT用户信息）

    O->>P: Feign 校验商品有效性
    P-->>O: 返回商品信息和价格

    O->>P: Feign 扣减库存
    P-->>O: 库存扣减成功

    O->>PR: Feign 核销优惠券（如有）
    PR-->>O: 优惠券核销成功

    O->>C: Feign 清空购物车已结算商品
    C-->>O: 清空成功

    Note over O: Seata AT 分布式事务保障
    O->>O: 生成雪花算法订单号，创建订单
    O-->>GW: 返回订单信息
    GW-->>U: 返回订单ID和支付信息
```

### 2. 支付流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant GW as Gateway
    participant PAY as Pay服务
    participant KF as Kafka
    participant O as Order服务

    U->>GW: POST /api/pay/create
    GW->>PAY: 创建支付单

    PAY->>PAY: 策略模式选择支付方式（支付宝/微信）
    PAY-->>GW: 返回支付页面/二维码
    GW-->>U: 展示支付页面

    Note over U,PAY: 用户完成支付（模拟）

    PAY->>PAY: 接收支付回调，更新支付流水状态
    PAY->>KF: 发布 PAYMENT_SUCCESS 事件
    KF->>O: 消费支付成功事件
    O->>O: 更新订单状态为「待发货」
    O-->>U: 推送订单状态变更通知
```

### 3. 秒杀流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant GW as Gateway
    participant PR as Promotion服务
    participant RD as Redis
    participant KF as Kafka
    participant O as Order服务

    U->>GW: POST /api/seckill/buy
    GW->>PR: 转发秒杀请求

    PR->>RD: Sentinel 限流检查
    Note over RD: 限流通过后执行 Lua 脚本
    PR->>RD: Lua 原子预扣库存（DECR + 判断）

    alt 库存不足
        RD-->>PR: 返回库存不足
        PR-->>GW: 秒杀失败（已售罄）
        GW-->>U: 提示已售罄
    else 库存充足
        RD-->>PR: 预扣成功
        PR->>KF: 发布 SECKILL_ORDER 事件
        KF->>O: 消费秒杀事件
        O->>O: 异步创建秒杀订单（Seata事务）
        O-->>U: 推送秒杀结果通知
    end
```

## Gateway 路由规则

| 路径前缀 | 目标服务 | 备注 |
|---------|---------|------|
| `/api/admin/auth/**` | byw-auth | StripPrefix=2，`/api/admin/auth/login` → `/auth/login` |
| `/api/admin/**` | byw-admin | 管理端 BFF，StripPrefix=1，`/api/admin/product/list` → `/admin/product/list` |
| `/api/auth/**` | byw-auth | StripPrefix=1 |
| `/api/user/**` | byw-user | StripPrefix=1 |
| `/api/product/**` | byw-product | StripPrefix=1 |
| `/api/category/**` | byw-product | StripPrefix=1 |
| `/api/search/**` | byw-product | StripPrefix=1 |
| `/api/brand/**` | byw-product | StripPrefix=1 |
| `/api/cart/**` | byw-cart | StripPrefix=1 |
| `/api/order/**` | byw-order | StripPrefix=1 |
| `/api/pay/**` | byw-pay | StripPrefix=1 |
| `/api/logistics/**` | byw-logistics | StripPrefix=1 |
| `/api/review/**` | byw-review | StripPrefix=1 |
| `/api/promotion/**` | byw-promotion | StripPrefix=1 |
| `/api/coupon/**` | byw-promotion | StripPrefix=1 |
| `/api/seckill/**` | byw-promotion | StripPrefix=1 |
