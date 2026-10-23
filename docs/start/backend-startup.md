# 后端启动

## 前置条件

- 必需中间件已启动（Nacos、MySQL、Redis）
- 可选中间件按需启动（RocketMQ、Elasticsearch、MongoDB）
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

执行 11 个建库 SQL 脚本（详见 [中间件安装 - MySQL 部分](middleware-setup.md#mysql-setup)）：

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
mysql -u root -p < docs/database/byw_im.sql
```

> **新部署环境**：直接执行上述 11 个完整建库脚本即可。
> 单个服务对应的库：byw-auth/byw-user → `byw_user`，byw-product → `byw_product`，byw-cart → `byw_cart`，
> byw-order → `byw_order`，byw-pay → `byw_pay`，byw-logistics → `byw_logistics`，byw-review → `byw_review`，
> byw-promotion → `byw_promotion`，byw-shop/byw-merchant → `byw_shop`，byw-settle → `byw_settle`，
> byw-im → `byw_im`。`byw-file` 不落库（文件存 MinIO），`byw-admin` 是 BFF 不落库。

> **存量环境升级**：全量脚本只用于新库。已有库请按顺序执行
> `docs/database/migration/` 下的增量脚本（文件名前缀即执行日期），不要用全量脚本覆盖。

---

## 第三步：配置 Nacos（配置全量托管）

本项目**所有业务配置都由 Nacos 托管**，`src/main/resources/` 下只有 `bootstrap.yml`（本地地址与
`shared-configs` 声明）。仓库里的 [docs/deploy/nacos/](../deploy/nacos/README.md) 只是**镜像**，
运行时各服务读的是 Nacos 服务端上的同名配置 —— 改仓库文件不会影响运行中的服务。

### 3.1 需要存在的配置

| dataId | group | 说明 |
|--------|-------|------|
| `byw-common.yml` | `DEFAULT_GROUP` | 共享配置：Redis、MyBatis-Plus、内部签名密钥、Sentinel、Seata 等 |
| `byw-<service>.yml` | `DEFAULT_GROUP` | 每个服务的端口、数据源（`spring.datasource.url`）等，共 16 份 |

所有服务通过 `bootstrap.yml` 的 `spring.cloud.nacos.config.shared-configs` 引入 `byw-common.yml`。

### 3.2 首次导入 / 修改配置

```powershell
# 把 docs/deploy/nacos/**/*.yml 全量推送到 Nacos（首次导入或批量改动后用）
pwsh docs/deploy/nacos/publish.ps1 -Server http://localhost:8848
```

只改一两项时也可以直接在 Nacos 控制台（http://localhost:8848/nacos）编辑对应 dataId，
**但不要只改仓库里的文件** —— 那样重启服务也读不到新值。

> ⚠️ **配置文件必须保持纯 ASCII**。Nacos 的 properties/yaml 解析遇到非 ASCII 字符时，
> 整份配置会静默解析成空（服务不会报错，只是全部用默认值），排查成本极高。
> 因此 `docs/deploy/nacos/` 下的 yml **不写中文注释**，`publish.ps1` 会上传前校验。

### 3.3 两个必须由环境提供的密钥

| 环境变量 | 缺失后果 | 说明 |
|----------|----------|------|
| `JASYPT_ENCRYPTOR_PASSWORD` | **服务启动失败** | 配置里所有 `ENC(...)` 密文的解密主密钥 |
| `byw.security.internal-secret`（配置项） | **服务启动失败** | 网关/服务间身份头 HMAC 签名密钥；源码里没有可用默认值，必须是 `ENC(...)` 或 64 位十六进制明文 |

生成新密文的步骤见 [docs/deploy/jasypt/](../deploy/jasypt/)。

### 3.4 `byw-common.yml` 里可以覆盖的常用项

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `spring.data.redis.host/port` | `localhost:6379` | 对应 `REDIS_HOST` / `REDIS_PORT` |
| `spring.data.redis.password` | 不配置（无认证） | 生产开启 Redis 认证时加 `password: ${REDIS_PASSWORD}`，**不要**写成 `${REDIS_PASSWORD:}`（空串会被当成空密码去 `AUTH`，连无密码的 Redis 都会连不上）。Redis 里存着全部会话与权限集 |
| `byw.pay.mock-enabled` | **false**（代码默认） | 模拟支付开关；本地/演示由 `byw-pay.yml` 显式设为 true |
| `byw.gateway.api-docs.enabled` | true | 置 false 后网关直接 404 掉所有文档路径，生产应关闭 |
| `byw.security.cors.allowed-origins` | localhost 各端 | 生产改成明确的前端域名 |
| `byw.gateway.rate-limit.trust-forwarded-for` | false | 只有网关前确有可信代理（Nginx/SLB）时才置 true |

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
        ├── byw-order ──→ Feign 调用 product / cart / promotion（需要 RocketMQ）
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
| Elasticsearch | 按需 | 仅 byw-product 商品搜索使用。**服务端必须是 8.x**（Spring Boot 3.2 管理的是 elasticsearch-java 8.11.x，8.x 客户端连 7.x 服务端属不支持的组合）；未装 IK 分词插件时会自动回退 `standard` 分词，索引仍可建立，只是中文搜索质量下降 |
| MongoDB | 按需 | 仅 byw-review 评价详情使用 |
| MinIO | 按需 | 仅 byw-file 文件/图片上传使用 |

> byw-settle 结算入账使用 Spring `@Scheduled` 定时扫描（内置于服务，无需额外中间件），并消费 byw-order 的确认收货事件（依赖 RocketMQ）。byw-shop / byw-merchant 无额外中间件依赖。

### 常见开发场景推荐启动组合

**场景 1：只测试登录注册**
> Nacos + MySQL + Redis → gateway → auth → user

**场景 2：测试商品浏览/搜索**
> 场景 1 + product（+ ES + RocketMQ）

**场景 3：测试完整下单流程**
> 场景 2 + cart + promotion + order + pay（+ RocketMQ）

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
