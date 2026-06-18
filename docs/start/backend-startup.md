# 后端启动

## 前置条件

- 所有中间件已启动并正常运行（Nacos、MySQL、Redis、Kafka、Elasticsearch、MongoDB）
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

执行 8 个 SQL 脚本（详见 [中间件安装 - MySQL 部分](./middleware-setup.md#mysql-80关系数据库)）：

```bash
mysql -u root -p < docs/database/byw_user.sql
mysql -u root -p < docs/database/byw_product.sql
mysql -u root -p < docs/database/byw_cart.sql
mysql -u root -p < docs/database/byw_order.sql
mysql -u root -p < docs/database/byw_pay.sql
mysql -u root -p < docs/database/byw_logistics.sql
mysql -u root -p < docs/database/byw_review.sql
mysql -u root -p < docs/database/byw_promotion.sql
```

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

## 第四步：按顺序启动服务

| 顺序 | 服务 | 端口 | 说明 |
|------|------|------|------|
| 1 | byw-gateway | 8080 | 网关（最先启动，所有请求入口） |
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

访问 Knife4j 文档：http://localhost:8080/doc.html（通过 Gateway 聚合）

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
