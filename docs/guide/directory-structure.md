# 目录结构

## 项目整体结构

```
buy-you-want/
├── pom.xml                                # 父 POM（统一版本管理、依赖锁定）
│
├── byw-common/                            # 公共模块（8 个子模块）
│   ├── pom.xml
│   ├── byw-common-core/                   # 核心工具：R<T> 统一响应、全局异常处理、BaseEntity、雪花ID生成器
│   ├── byw-common-redis/                  # Redis 工具：Redisson 配置、分布式锁工具类、RedisUtil
│   ├── byw-common-mybatis/                # 持久层：MyBatis-Plus 配置、分页插件、自动填充 MetaObjectHandler
│   ├── byw-common-security/               # 安全模块：JWT 工具类、UserContext 线程上下文、鉴权拦截器
│   ├── byw-common-kafka/                  # 消息队列：Kafka 生产者/消费者配置、Topic 常量定义
│   ├── byw-common-elasticsearch/          # 搜索引擎：ES RestHighLevelClient / ElasticsearchClient 配置
│   ├── byw-common-swagger/                # API 文档：Knife4j OpenAPI3 统一配置、分组管理（仅 Servlet 服务）
│   └── byw-common-log/                    # 日志模块：@OperLog 操作日志 AOP 切面
│
├── byw-api/                               # Feign 远程调用接口定义（接口 + DTO）
│   ├── pom.xml
│   ├── byw-api-user/                      # 用户服务接口：UserFeignClient + UserDTO
│   ├── byw-api-product/                   # 商品服务接口：ProductFeignClient + SkuDTO/ProductDTO
│   ├── byw-api-cart/                      # 购物车服务接口：CartFeignClient + CartItemDTO
│   ├── byw-api-order/                     # 订单服务接口：OrderFeignClient + OrderDTO/OrderItemDTO
│   ├── byw-api-pay/                       # 支付服务接口：PayFeignClient + PayOrderDTO
│   ├── byw-api-logistics/                 # 物流服务接口：LogisticsFeignClient + LogisticsDTO/LogisticsTrackDTO
│   ├── byw-api-review/                    # 评价服务接口：ReviewFeignClient + ReviewDTO
│   └── byw-api-promotion/                 # 营销服务接口：PromotionFeignClient + CouponDTO
│
├── byw-gateway/                           # Spring Cloud Gateway 网关 (:8080)
│   └── ...                                #   路由配置、JWT 全局过滤器、跨域配置、Sentinel 限流
│
├── byw-auth/                              # 认证中心 (:8081)
│   └── ...                                #   登录、注册、Token 刷新、验证码
│
├── byw-user/                              # 用户中心 (:8082)
│   └── ...                                #   用户 CRUD、收货地址管理、会员等级
│
├── byw-product/                           # 商品中心 (:8083)
│   └── ...                                #   三级分类、品牌管理、SPU/SKU 管理、ES 搜索同步、库存管理
│
├── byw-cart/                              # 购物车 (:8084)
│   └── ...                                #   Redis Hash + MySQL 双写、加购/结算/全选
│
├── byw-order/                             # 订单中心 (:8085)
│   └── ...                                #   雪花算法订单号、Seata 分布式事务、状态机、超时自动取消
│
├── byw-pay/                               # 支付中心 (:8086)
│   └── ...                                #   策略模式（支付宝/微信）、模拟支付回调、Kafka 异步通知
│
├── byw-logistics/                         # 物流中心 (:8087)
│   └── ...                                #   发货管理、物流跟踪（模拟）、状态更新
│
├── byw-review/                            # 评价系统 (:8088)
│   └── ...                                #   MySQL 基础表 + MongoDB 详情存储、图片评价、评分统计
│
├── byw-promotion/                         # 营销中心 (:8089)
│   └── ...                                #   优惠券（满减/折扣/无门槛）、秒杀（Redis Lua + Sentinel）、拼团
│
├── byw-admin/                             # 管理后台 BFF 层 (:8090)
│   └── ...                                #   聚合各微服务接口，为管理端提供统一 API
│
├── byw-frontend/
│   ├── byw-web/                           # Nuxt.js 3 用户端（SSR 服务端渲染）
│   │   ├── pages/                         #   页面：首页、商品详情、购物车、订单、个人中心
│   │   ├── components/                    #   通用组件
│   │   ├── layouts/                       #   布局模板
│   │   ├── stores/                        #   Pinia 状态管理
│   │   ├── plugins/                       #   插件配置
│   │   ├── middleware/                    #   路由中间件（鉴权拦截）
│   │   └── utils/                         #   工具函数
│   └── byw-admin-web/                     # Vue3 + Element Plus 管理端（Vite）
│       └── src/
│           ├── views/                     #   页面：Dashboard、商品管理、订单管理、用户管理
│           ├── components/                #   通用组件
│           ├── router/                    #   路由配置
│           ├── stores/                    #   Pinia 状态管理
│           ├── api/                       #   Axios 接口封装
│           └── utils/                     #   工具函数
│
└── docs/
    ├── database/                          # SQL 建库建表脚本
    │   ├── byw_user.sql                   #   用户库：user、address、member_level
    │   ├── byw_product.sql                #   商品库：category、brand、spu、sku、sku_stock
    │   ├── byw_cart.sql                   #   购物车库：cart_item
    │   ├── byw_order.sql                  #   订单库：order_info、order_item
    │   ├── byw_pay.sql                    #   支付库：pay_order、pay_record
    │   ├── byw_logistics.sql              #   物流库：logistics_info、logistics_track
    │   ├── byw_review.sql                 #   评价库：review
    │   └── byw_promotion.sql              #   营销库：coupon、seckill_activity、seckill_order
    ├── guide/                             # 项目指南文档
    └── ...
```

## 微服务标准包结构

每个业务微服务（`byw-xxx`）遵循统一的包结构规范：

```
byw-xxx/
├── pom.xml                                # 模块 POM（引入所需 common/api 依赖）
└── src/
    └── main/
        ├── java/com/byw/xxx/
        │   ├── BywXxxApplication.java     # Spring Boot 启动类（@SpringBootApplication + @EnableDiscoveryClient）
        │   │
        │   ├── controller/                # REST 控制器层
        │   │   └── XxxController.java     #   接收 HTTP 请求，参数校验，调用 Service
        │   │
        │   ├── service/                   # 业务逻辑接口层
        │   │   ├── XxxService.java        #   业务接口定义
        │   │   └── impl/                  #   业务实现
        │   │       └── XxxServiceImpl.java
        │   │
        │   ├── mapper/                    # MyBatis-Plus Mapper 接口层
        │   │   └── XxxMapper.java         #   继承 BaseMapper<T>，自定义 SQL
        │   │
        │   ├── entity/                    # 数据库实体（DO）
        │   │   └── Xxx.java              #   @TableName 映射表，继承 BaseEntity
        │   │
        │   ├── dto/                       # 数据传输对象
        │   │   ├── XxxCreateDTO.java      #   创建请求 DTO
        │   │   ├── XxxUpdateDTO.java      #   更新请求 DTO
        │   │   ├── XxxQueryDTO.java       #   查询条件 DTO
        │   │   └── XxxVO.java             #   视图对象（返回给前端）
        │   │
        │   ├── feign/                     # Feign 降级实现（可选）
        │   │   └── XxxFeignFallback.java  #   @FeignClient fallback 降级逻辑
        │   │
        │   ├── config/                    # 模块级配置
        │   │   └── XxxConfig.java         #   本地 Bean 配置
        │   │
        │   └── listener/                  # Kafka 消息监听器（可选）
        │       └── XxxKafkaListener.java  #   @KafkaListener 消费事件
        │
        └── resources/
            ├── bootstrap.yml              # 启动配置（Nacos 连接信息、服务名、端口）
            └── mapper/                    # MyBatis XML 映射文件（可选）
                └── XxxMapper.xml          #   复杂 SQL 映射
```

## 公共模块说明

### byw-common-core
- `R<T>` 统一响应包装类（code + message + data）
- `GlobalExceptionHandler` 全局异常处理（@RestControllerAdvice）
- `BaseEntity` 基础实体（id、createTime、updateTime、isDeleted）
- `SnowflakeIdGenerator` 雪花算法 ID 生成器
- `BusinessException` 自定义业务异常

### byw-common-redis
- Redisson 客户端自动配置
- `RedisUtil` Redis 操作工具类封装
- `DistributedLock` 分布式锁工具类
- Redis 序列化配置（Jackson2JsonRedisSerializer）

### byw-common-mybatis
- MyBatis-Plus 分页插件配置（MybatisPlusInterceptor）
- `MetaObjectHandler` 自动填充（createTime、updateTime、createBy）
- `BaseMapperX` 扩展 Mapper 接口

### byw-common-security
- `JwtUtil` JWT 签发/解析/验证工具
- `UserContext` 用户上下文（ThreadLocal 存储当前登录用户信息）
- `AuthInterceptor` 鉴权拦截器（从 Header 解析 Token 并注入 UserContext）

### byw-common-kafka
- Kafka 生产者配置（KafkaTemplate）
- Kafka 消费者配置（ConcurrentKafkaListenerContainerFactory）
- `KafkaTopicConstants` Topic 名称常量

### byw-common-elasticsearch
- ElasticsearchClient 自动配置
- ES 索引操作工具类

### byw-common-swagger
- Knife4j OpenAPI3 统一配置（仅用于 Servlet 微服务，不含 Gateway）
- 分组配置（按微服务划分 API 分组）
- 全局 Token 请求头配置

> **注**：Gateway 为 WebFlux 架构，单独引入 `springdoc-openapi-starter-webflux-ui` 实现 API 文档聚合，访问地址为 `/swagger-ui.html`。

### byw-common-log
- `@OperLog` 自定义注解
- `OperLogAspect` AOP 切面（记录操作人、操作类型、请求参数、响应结果、耗时）
