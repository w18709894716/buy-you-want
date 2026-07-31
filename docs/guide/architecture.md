# 系统架构

## 整体架构图

```mermaid
graph TB
    subgraph 客户端层
        A1[用户端 Nuxt.js SSR :3000]
        A2[管理端 Vue3 + Element Plus :5174]
        A3[商家端 Vue3 + Element Plus :5175]
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
        C11[byw-file 文件服务 :8091]
        C12[byw-shop 店铺中心 :8092]
        C13[byw-merchant 商家BFF :8093]
        C14[byw-settle 结算分账 :8094]
        C15[byw-im 客服IM :8095]
    end

    subgraph 基础设施层
        D1[Nacos 注册/配置中心]
        D2[Redis 7.x]
        D3[MySQL 8.0]
        D4[RocketMQ 5.x]
        D5[Elasticsearch 8.13]
        D6[MongoDB 7.0]
        D7[Seata 分布式事务]
    end

    A1 --> B1
    A2 --> B1
    A3 --> B1
    B1 --> B2
    B1 --> B3
    B1 --> C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C10 & C11 & C12 & C13

    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C10 & C11 & C12 & C13 & C14 & C15 --> D1
    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C12 & C15 --> D2
    C1 & C2 & C3 & C4 & C5 & C6 & C7 & C8 & C9 & C12 & C14 --> D3
    C5 & C6 & C7 & C9 & C14 & C15 --> D4
    C3 --> D5
    C8 & C15 --> D6
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
| byw-order | 8085 | 订单创建、状态机、超时取消、Seata 事务 | MySQL, Redis, RocketMQ, Seata, Nacos |
| byw-pay | 8086 | 支付策略、模拟回调、支付流水 | MySQL, Redis, RocketMQ, Nacos |
| byw-logistics | 8087 | 发货管理、物流跟踪、状态更新 | MySQL, RocketMQ, Nacos |
| byw-review | 8088 | 评价管理、评分统计、商家回复 | MySQL, MongoDB, Redis, Nacos |
| byw-promotion | 8089 | 优惠券、秒杀（Lua预扣+限流）、拼团 | MySQL, Redis, RocketMQ, Nacos |
| byw-admin | 8090 | 管理后台 BFF 聚合层 | Nacos |
| byw-file | 8091 | 文件/图片上传（MinIO） | MinIO, Nacos |
| byw-shop | 8092 | 店铺管理、商家账号与入驻审核 | MySQL, Redis, Nacos |
| byw-merchant | 8093 | 商家端 BFF 聚合层 | Nacos |
| byw-settle | 8094 | 结算分账、佣金规则、余额与提现（@Scheduled T+N） | MySQL, RocketMQ, Nacos |
| byw-im | 8095 | 客服聊天 IM（WebSocket 长连接、会话与消息、在线状态） | MySQL, MongoDB, Redis, RocketMQ, Nacos |
| byw-common | — | 公共工具模块（8 个子模块） | — |

## 服务间通信

### 同步调用（OpenFeign）
通过 `byw-api` 模块定义 Feign 接口和 DTO，各服务声明式调用其他服务接口，适合需要实时响应的场景：
- Order → Product（校验商品、扣减库存）
- Order → Cart（清空已结算商品）
- Order → Promotion（核销优惠券）
- Admin / Merchant（BFF）→ 各业务服务（聚合查询，商家端受 shop_id 隔离）
- Merchant BFF / Admin BFF → Settle（结算余额、佣金规则、提现与审批，仅 Feign 内部调用）
- Order / Product → Shop（校验店铺、回填 shop_id）

### 异步调用（RocketMQ）
通过 RocketMQ 发布/订阅事件，实现跨服务最终一致性和削峰填谷：
- **支付成功** → Pay 发送事件 → Order 更新状态为待发货
- **库存扣减** → Promotion 秒杀预扣 → Order 异步创建订单
- **物流状态变更** → Logistics 发送事件 → Order 更新物流状态
- **订单完成** → Order 发送事件 → Review 开放评价入口、Promotion 结算优惠券
- **确认收货** → Order 发送事件 → Settle 消费并按分类佣金率创建结算单（T+N 冷静期后入账）
- **确认收货** → Order 发送事件 → Settle 消费并按分类佣金率创建结算单（T+N 冷静期后入账）

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
    participant MQ as RocketMQ
    participant O as Order服务

    U->>GW: POST /api/pay/create
    GW->>PAY: 创建支付单

    PAY->>PAY: 策略模式选择支付方式（支付宝/微信）
    PAY-->>GW: 返回支付页面/二维码
    GW-->>U: 展示支付页面

    Note over U,PAY: 用户完成支付（模拟）

    PAY->>PAY: 接收支付回调，更新支付流水状态
    PAY->>MQ: 发布 PAYMENT_SUCCESS 事件
    MQ->>O: 消费支付成功事件
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
    participant MQ as RocketMQ
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
        PR->>MQ: 发布 SECKILL_ORDER 事件
        MQ->>O: 消费秒杀事件
        O->>O: 异步创建秒杀订单（Seata事务）
        O-->>U: 推送秒杀结果通知
    end
```

### 4. 多商家拆单下单流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant GW as Gateway
    participant O as Order服务
    participant P as Product服务

    U->>GW: POST /api/order/create（多店铺购物车）
    GW->>O: 转发请求
    Note over O: 购物车项按 shop_id 分组
    O->>O: 创建父订单（is_parent=1，聚合支付总额/收货地址/优惠券）
    loop 每个店铺
        O->>P: Feign 校验商品与扣减库存
        O->>O: 创建子订单（is_parent=0，归属单一 shop_id，含明细）
    end
    Note over O: Seata AT 事务保障父子订单一致性
    O-->>U: 返回父订单号与支付信息（支付以父订单聚合，履约按子订单独立进行）
```

### 5. 结算分账流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant O as Order服务
    participant MQ as RocketMQ
    participant S as Settle服务
    participant SC as 定时扫描 @Scheduled
    participant M as 商家
    participant A as 平台审批

    U->>O: 确认收货（子订单）
    O->>MQ: 发布确认收货事件
    MQ->>S: 消费事件
    S->>S: 按分类佣金率算佣，创建结算单（待结算/冷静期冻结）
    Note over SC,S: 收货 + T+N 到期
    SC->>S: 扫描到期结算单
    S->>S: 转入可用余额（pending → available，记余额流水）
    M->>S: 发起提现
    S->>S: 冻结提现金额（available → frozen）
    A->>S: 审批提现
    alt 通过
        S->>S: 打款（frozen → withdrawn）
    else 驳回
        S->>S: 解冻（frozen → available）
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
| `/api/banner/**` | byw-product | StripPrefix=1 |
| `/api/file/**` | byw-file | StripPrefix=1 |
| `/api/shop/**` | byw-shop | StripPrefix=1 |
| `/api/merchant/**` | byw-merchant | 商家端 BFF，StripPrefix=1 |

> 结算服务 byw-settle 无对外网关路由，仅由 byw-merchant / byw-admin 通过 Feign（`/feign/settle/**`）内部调用。
