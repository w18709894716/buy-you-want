# 后端启动

## 前置条件

- 必需中间件已启动（Nacos、MySQL、Redis）
- 可选中间件按需启动（RocketMQ、Elasticsearch、MongoDB、Seata）
- JDK 17+ 已配置
- Maven 3.8+ 已配置

---

## 第一步：编译项目

```bash
cd buy-you-want
mvn clean install -DskipTests
```

---

## 第二步：初始化数据库

执行 10 个建库 SQL 脚本（详见 [中间件安装 - MySQL 部分](./middleware-setup.md#mysql-80关系数据库)）：

```bash
mysql -u root -p < docs/database/byw_user.sql
mysql -u root -p < docs/database/byw_product.sql
mysql -u root -p < docs/database/byw_cart.sql
mysql -u root -p < docs/database/byw_order.sql
mysql -u root -p < docs/database/byw_pay.sql
mysql -u root -p < docs/database/byw_logistics.sql
mysql -u root -p < docs/database/byw_review.sql
mysql -u root -p < docs/database/byw_promotion.sql
mysql -u root -p < docs/database/byw_shop.sql
mysql -u root -p < docs/database/byw_settle.sql
```

> **新部署环境**：直接执行上述 10 个完整建库脚本即可（已包含 shop_id / role / 拆单 / 商品审核等全部字段）。
>
> **存量环境升级**：在已有数据库上按阶段顺序追加执行增量迁移脚本（详见 [数据库设计 - 增量迁移脚本](../database/database-design.md#增量迁移脚本)）：
>
> ```bash
> mysql -u root -p < docs/database/migration_shop_tenancy.sql   # 阶段一：各库补 shop_id、user.role、评价商家回复、优惠券 shop_id
> mysql -u root -p < docs/database/migration_product_audit.sql  # 阶段四：商品审核字段
> mysql -u root -p < docs/database/migration_order_split.sql    # 阶段五：拆单父子订单、购物车 shop_id
> ```

---

## 第三步：配置 Nacos

确认各服务的 `bootstrap.yml` 中 Nacos 地址正确：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
      config:
        server-addr: localhost:8848
```

如需在 Nacos 中配置数据源等参数，可在 Nacos 控制台（http://localhost:8848/nacos）添加各服务的配置文件。

---

## 第四步：按需启动服务

> **不需要启动所有服务**，可以根据开发场景按需启动。

### 服务依赖关系

```
Nacos + MySQL + Redis（必须启动的中间件底座）
    │
    ├── byw-gateway（前端入口，前端联调时必须）
    │
    ├── byw-auth ──→ byw-user（Auth 通过 Feign 调用 User 查用户/验密码）
    │
    ├── byw-user（被 Auth 依赖，独立也可启动）
    │
    └── 以下服务可独立按需启动 ──────
        ├── byw-product（需要 ES + RocketMQ）
        ├── byw-cart
        ├── byw-promotion
        ├── byw-order ──→ Feign 调用 product / cart / promotion（需要 RocketMQ + Seata）
        ├── byw-pay ──→ RocketMQ 通知 order
        ├── byw-logistics（需要 RocketMQ）
        ├── byw-review（需要 MongoDB）
        ├── byw-shop（店铺/商家账号，被 order/product/merchant 依赖）
        ├── byw-file（文件上传，需要 MinIO）
        ├── byw-settle ──→ 消费确认收货事件建结算单，@Scheduled 扫描 T+N 入账（需要 RocketMQ）
        ├── byw-admin（BFF，聚合调用各服务）
        └── byw-merchant（商家端 BFF，聚合商品/订单/评价/结算，受 shop_id 隔离）
```

### 中间件依赖说明

| 中间件 | 必要性 | 说明 |
|--------|--------|------|
| Nacos | **必须** | 服务注册中心，所有微服务依赖它 |
| MySQL | **必须** | 各服务启动时建立数据库连接池 |
| Redis | **必须** | 缓存、Token 存储、分布式锁等，几乎所有服务依赖 |
| RocketMQ | 按需 | byw-order / byw-pay / byw-logistics / byw-product 使用 |
| Elasticsearch | 按需 | 仅 byw-product 商品搜索使用 |
| MongoDB | 按需 | 仅 byw-review 评价详情使用 |
| Seata | 按需 | 仅 byw-order 分布式事务使用 |
| MinIO | 按需 | 仅 byw-file 文件/图片上传使用 |

> byw-settle 结算入账使用 Spring `@Scheduled` 定时扫描（内置于服务，无需额外中间件），并消费 byw-order 的确认收货事件（依赖 RocketMQ）。byw-shop / byw-merchant 无额外中间件依赖。

### 常见开发场景推荐启动组合

**场景 1：只测试登录注册**
> Nacos + MySQL + Redis → gateway → auth → user

**场景 2：测试商品浏览/搜索**
> 场景 1 + product（+ ES + RocketMQ）

**场景 3：测试完整下单流程**
> 场景 2 + cart + promotion + order + pay（+ RocketMQ + Seata）

**场景 4：只开发管理后台某个模块**
> Nacos + MySQL + Redis → gateway → auth → user → admin + 对应业务服务

> **提示**：某个服务未启动时，Feign 调用它会报错，但不影响其他已启动服务的正常运行。

### 完整启动顺序（全量启动时）

| 顺序 | 服务 | 端口 | 说明 |
|------|------|------|------|
| 1 | byw-gateway | 8080 | 网关（所有请求入口） |
| 2 | byw-auth | 8081 | 认证中心（登录注册） |
| 3 | byw-user | 8082 | 用户中心 |
| 4 | byw-product | 8083 | 商品中心 |
| 5 | byw-cart | 8084 | 购物车 |
| 6 | byw-order | 8085 | 订单中心 |
| 7 | byw-pay | 8086 | 支付中心 |
| 8 | byw-logistics | 8087 | 物流中心 |
| 9 | byw-review | 8088 | 评价系统 |
| 10 | byw-promotion | 8089 | 营销中心 |
| 11 | byw-admin | 8090 | 管理后台 BFF |
| 12 | byw-file | 8091 | 文件服务（MinIO） |
| 13 | byw-shop | 8092 | 店铺中心（店铺/商家账号/入驻审核） |
| 14 | byw-merchant | 8093 | 商家端 BFF |
| 15 | byw-settle | 8094 | 结算分账（@Scheduled T+N 入账） |
| 16 | byw-im | 8095 | 客服聊天 IM（WebSocket 长连接） |

### 启动方式

- **IDEA**：直接运行各模块的 `BywXxxApplication.java` 启动类
- **命令行**：
  ```bash
  mvn spring-boot:run -pl byw-xxx
  ```

---

## 第五步：验证服务注册

访问 Nacos 控制台：http://localhost:8848/nacos

在 **服务管理 → 服务列表** 中应能看到所有已启动的服务。

---

## 第六步：测试 API

访问 API 文档：
- **Gateway 聚合文档**: http://localhost:8080/swagger-ui.html（下拉切换各服务）
- **各服务独立文档 (Knife4j)**: http://localhost:{port}/doc.html（直接访问服务端口）

### 测试注册接口

```bash
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "test",
  "password": "123456",
  "phone": "13800138000"
}
```

### 测试登录接口

```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "test",
  "password": "123456"
}
```
