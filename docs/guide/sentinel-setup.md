# Sentinel 限流熔断配置指南

> 本文档介绍如何启动 Sentinel Dashboard 并配置流控规则。

---

## 1. 下载 Sentinel Dashboard

从 [GitHub Releases](https://github.com/alibaba/Sentinel/releases) 下载 Sentinel Dashboard 1.8.8：

```
sentinel-dashboard-1.8.8.jar
```

---

## 2. 启动 Dashboard

```bash
java -Dserver.port=8858 -jar sentinel-dashboard-1.8.8.jar
```

启动参数说明：
- `-Dserver.port=8858`：Dashboard 端口（8858 是 Sentinel 默认端口，避免与项目服务 8080-8090 冲突）

启动后访问：http://localhost:8858

默认账号密码：`sentinel` / `sentinel`

---

## 3. 服务接入与发现

项目已配置 Sentinel，启动业务服务后，Dashboard 会自动发现服务。

各服务的 `bootstrap.yml` 已配置：

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8858}
```

如需连接其他地址的 Dashboard，设置环境变量：
```bash
export SENTINEL_DASHBOARD=your-dashboard-host:port
```

### 3.1 服务发现机制

**重要**：Dashboard 不会自动显示所有服务，需要满足以下条件：

1. **先启动 Dashboard**，再启动业务服务
2. **至少访问一次接口**，Dashboard 才会显示该服务（Sentinel 按需上报）
3. 服务启动后，Sentinel 日志位于 `C:\Users\{用户名}\logs\csp\`

**操作步骤**：
```bash
# 1. 启动 Dashboard
java -Dserver.port=8858 -jar sentinel-dashboard-1.8.8.jar

# 2. 启动业务服务
java -jar byw-product.jar

# 3. 访问一次接口触发 Sentinel 资源
curl http://localhost:8083/product/category/tree

# 4. 刷新 Dashboard 页面
```

### 3.2 资源发现机制

Dashboard 中会显示**所有 HTTP 请求**的资源，不仅限于 `@SentinelResource` 标注的接口。

**两种资源方式**：

| 方式 | 资源名格式 | 降级处理 | 适用场景 |
|------|------------|----------|----------|
| **自动埋点** | `GET:/user/me`（HTTP方法:URL路径） | 全局 `BlockExceptionHandler` | 简单限流，无需改代码 |
| **@SentinelResource** | 自定义名称如 `user:info` | 注解指定的 fallback 方法 | 需要自定义降级逻辑 |

**说明**：
- 自动埋点由 `spring-cloud-starter-alibaba-sentinel` 自动完成，所有 HTTP 接口都会被监控
- `@SentinelResource` 用于自定义资源名称和提供降级方法，是可选的
- **两种方式都可以配置限流规则**，直接在 Dashboard 中选择资源名即可

---

## 4. 配置流控规则

### 4.1 通过 Dashboard 配置

1. 登录 Dashboard（http://localhost:8858）
2. 左侧菜单选择目标服务（需先触发请求才会显示）
3. 点击「流控规则」->「新增规则」
4. 配置参数：
   - **资源名**：自动埋点为 `GET:/xxx`，或 `@SentinelResource` 的自定义名称
   - **阈值类型**：QPS 或 线程数
   - **单机阈值**：具体数值
   - **流控效果**：快速失败 / Warm Up / 排队等待

**示例**：限制 `/user/me` 接口 QPS 为 100
- 资源名：`GET:/user/me`
- 阈值类型：QPS
- 单机阈值：100

### 4.2 已注册的资源（@SentinelResource）

以下接口使用 `@SentinelResource` 注解定义了自定义资源名：

| 模块 | 资源名 | 说明 |
|------|--------|------|
| byw-product | `category:tree` | 分类树查询 |
| byw-product | `product:list` | 商品列表 |
| byw-product | `product:detail` | 商品详情 |
| byw-order | `order:create` | 创建订单 |
| byw-order | `order:list` | 订单列表 |
| byw-user | `user:info` | 用户信息 |
| byw-cart | `cart:list` | 购物车列表 |
| byw-cart | `cart:add` | 添加购物车 |
| byw-auth | `auth:login` | 登录 |

### 4.3 通过 Nacos 持久化规则

规则持久化到 Nacos，Data ID 格式：

- **流控规则**：`{服务名}-flow-rules`
- **降级规则**：`{服务名}-degrade-rules`
- **Group ID**：`SENTINEL_GROUP`

示例（byw-product-flow-rules）：
```json
[
  {
    "resource": "product:list",
    "grade": 1,
    "count": 100,
    "strategy": 0,
    "controlBehavior": 0
  }
]
```

字段说明：
- `resource`：资源名
- `grade`：阈值类型（1=QPS，0=线程数）
- `count`：阈值
- `strategy`：流控策略（0=直接，1=关联，2=链路）
- `controlBehavior`：流控效果（0=快速失败，1=Warm Up，2=排队等待）

---

## 5. 限流响应

当请求被限流时，返回统一 JSON 响应：

```json
{
  "code": 9001,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "timestamp": 1718524800000
}
```

HTTP 状态码：`429 Too Many Requests`

---

## 6. 注意事项

1. Dashboard 端口使用 8858（Sentinel 默认端口），避免与项目服务 8080-8090 冲突
2. 规则持久化到 Nacos 后，Dashboard 中的修改不会实时同步，需直接修改 Nacos 配置
3. 生产环境建议通过 Nacos 管理规则，便于版本控制和审计
