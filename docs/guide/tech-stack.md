# 技术栈

## 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | JDK LTS 版本，支持 Records、Pattern Matching 等新特性 |
| Spring Boot | 3.2.5 | 应用框架基座，Jakarta EE 迁移 |
| Spring Cloud | 2023.0.1 | 微服务治理框架（Gateway、OpenFeign、LoadBalancer） |
| Spring Cloud Alibaba | 2023.0.1.0 | 阿里微服务组件（Nacos、Sentinel、Seata） |
| MyBatis-Plus | 3.5.6 | ORM 增强，内置分页插件、自动填充、代码生成 |
| MySQL Connector/J | 8.3.0 | MySQL JDBC 驱动 |
| Redisson | 3.28.0 | Redis 高级客户端，分布式锁、布隆过滤器、限流器 |
| Spring Kafka | — | Kafka 客户端封装，生产者/消费者配置 |
| Spring Data Elasticsearch | — | Elasticsearch 客户端，索引与查询操作 |
| Spring Data MongoDB | — | MongoDB 客户端，文档 CRUD 操作 |
| Seata | 2.0.0 | 分布式事务框架，AT/TCC/Saga 模式 |
| Sa-Token | 1.38.0 | 轻量级权限认证框架，单点登录 |
| Hutool | 5.8.27 | Java 工具集（字符串、日期、加密、HTTP 等） |
| Knife4j | 4.4.0 | 各微服务 OpenAPI 3.0 接口文档 UI，增强 Swagger |
| SpringDoc OpenAPI | 2.3.0 | Gateway 端 WebFlux API 文档聚合（springdoc-openapi-starter-webflux-ui） |
| JJWT | 0.12.5 | JWT 令牌签发、解析与验证 |
| Nacos | 2.3.2 | 服务注册发现 + 配置管理中心 |
| Sentinel | — | 流量控制、熔断降级、系统负载保护 |

> 注：Spring Kafka、Spring Data Elasticsearch、Spring Data MongoDB 版本由 Spring Boot 3.2.5 BOM 统一管理，Sentinel 版本由 Spring Cloud Alibaba 2023.0.1.0 BOM 管理。

## 前端技术栈

### 用户端（byw-web）

| 技术 | 版本 | 说明 |
|------|------|------|
| Nuxt.js | ^3.12.0 | Vue3 全栈 SSR 框架，服务端渲染 + SEO 优化 |
| Vue | ^3.4.0 | 渐进式前端框架（Composition API） |
| Pinia | ^2.1.7 | Vue3 官方状态管理（替代 Vuex） |
| TailwindCSS | ^6.12.0 | 原子化 CSS 框架，快速构建响应式页面 |
| VueUse | ^10.9.0 | Vue Composition API 工具集 |
| TypeScript | ^5.4.0 | 类型安全支持 |

### 管理端（byw-admin-web）

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | ^3.4.21 | 渐进式前端框架 |
| Element Plus | ^2.7.0 | Vue3 企业级 UI 组件库 |
| Vite | ^5.2.8 | 下一代前端构建工具，极速 HMR |
| Pinia | ^2.1.7 | 状态管理 |
| ECharts | ^5.5.0 | 数据可视化图表库（Dashboard 看板） |
| vue-echarts | ^6.7.2 | ECharts Vue3 封装组件 |
| Axios | ^1.6.8 | HTTP 请求客户端 |
| Vue Router | ^4.3.0 | 官方路由管理 |
| TypeScript | ^5.4.5 | 类型安全支持 |

## 中间件

| 中间件 | 版本 | 用途 |
|--------|------|------|
| Nacos | 2.3.2 | 服务注册发现、动态配置管理、命名空间隔离 |
| MySQL | 8.0 | 关系型数据库，各微服务独立数据库 |
| Redis | 7.x | 缓存、分布式锁、购物车存储、秒杀库存预扣（Lua 脚本） |
| Kafka | 3.7.0 (KRaft) | 消息队列，事件驱动，无 ZooKeeper 模式 |
| Elasticsearch | 8.13.4 | 商品全文搜索，IK 中文分词 |
| MongoDB | 7.0.9 | 评价详情、图片等非结构化文档存储 |
| Seata | 2.0 | 分布式事务协调（AT 模式） |
| Sentinel | — | 限流、熔断、降级、热点参数流控 |

## 技术选型说明

### Spring Cloud Alibaba vs Spring Cloud Netflix
- **国产生态成熟**：Nacos 一站式提供注册中心 + 配置中心，无需 Eureka + Config Server 双组件
- **社区活跃**：Spring Cloud Netflix 已进入维护模式（Ribbon/Hystrix 停止更新），Spring Cloud Alibaba 持续迭代
- **功能全面**：Sentinel 限流 + Seata 事务 + RocketMQ 消息，开箱即用

### Redisson vs Lettuce / Jedis
- **高级数据结构**：内置分布式锁（可重入/公平锁）、布隆过滤器、限流器、延迟队列
- **开发效率**：屏蔽底层 Redis 协议细节，提供 Java 对象级 API
- **高可用**：支持主从、哨兵、集群多种部署模式，自动故障转移

### Kafka vs RabbitMQ
- **高吞吐量**：Kafka 单 Broker 可达百万级 TPS，适合秒杀等高并发场景
- **事件溯源**：天然支持事件驱动架构，消息持久化、可回溯消费
- **KRaft 模式**：Kafka 3.7.0 已移除 ZooKeeper 依赖，简化部署运维

### Seata AT vs TCC
- **开发效率优先**：AT 模式基于数据源代理自动生成回滚 SQL，业务代码零侵入
- **TCC 复杂度高**：需要手动编写 Try / Confirm / Cancel 三个接口，适合对一致性要求极高的金融场景
- **本项目选型**：电商下单链路采用 AT 模式，兼顾开发效率与数据一致性；秒杀场景结合 Kafka 最终一致性方案
