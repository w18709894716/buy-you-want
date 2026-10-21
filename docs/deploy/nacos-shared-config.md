# Nacos 配置收敛（shared-configs）方案

> **状态：✅ 2026-10-19 完成公共配置收敛；2026-10-20 完成全量迁移**
> （公共 @@byw-common.yml@@ + 16 个 @@services/byw-<name>.yml@@，本地只剩"自举 + 日志"）。
> 操作手册以 [nacos/README.md](nacos/README.md) 为准（含发布脚本与踩坑说明）。
>
> ~~原状态：方案已就绪，尚未执行。~~

## 一、问题

16 个服务的 `bootstrap.yml` 各自复制了一份几乎相同的配置块：

| 配置块 | 各服务差异 | 现状 |
|---|---|---|
| `spring.cloud.nacos.discovery/config` | 仅 `server-addr`/`namespace` 占位符相同 | 16 份完全相同 |
| `spring.cloud.sentinel.*` | `data-id` 用 `${spring.application.name}` 参数化 | 16 份完全相同（已参数化） |
| `spring.datasource` | **库名不同**（byw_order / byw_product …） | 只有 URL 的库名不同 |
| `spring.data.redis` | 相同 | 16 份完全相同 |
| `mybatis-plus` | 相同 | 16 份完全相同 |
| `springdoc` | 相同 | 16 份完全相同 |
| `logging.pattern.console` | 相同 | 16 份完全相同 |
| `rocketmq.producer.group` | **不同**（byw-order-producer …） | 必须各自保留 |
| `jetcache` | 仅 byw-product 使用 | 保留在各自文件 |

改动一处公共配置（例如统一调整 Redis 连接参数、日志格式）需要同步 16 个文件，极易漏改产生环境漂移。

## 二、目标结构

```
Nacos（namespace = 你的 NACOS_NAMESPACE，group = DEFAULT_GROUP）
├── byw-common.yml        # 公共配置：redis / mybatis-plus / springdoc / logging / sentinel datasource
└── byw-<service>.yml     # 服务私有：spring.datasource.url、rocketmq.producer.group、jetcache（如有）
```

各服务 `bootstrap.yml` 只保留差异化内容 + 引用公共配置：

```yaml
spring:
  application:
    name: byw-order
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yml
        shared-configs:
          - data-id: byw-common.yml
            group: DEFAULT_GROUP
            refresh: true
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/byw_order?...
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## 三、要发布的 `byw-common.yml`

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cloud:
    sentinel:
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8858}
      datasource:
        flow:
          nacos:
            server-addr: ${NACOS_ADDR:localhost:8848}
            namespace: ${NACOS_NAMESPACE:}
            data-id: ${spring.application.name}-flow-rules
            group-id: SENTINEL_GROUP
            rule-type: flow
        degrade:
          nacos:
            server-addr: ${NACOS_ADDR:localhost:8848}
            namespace: ${NACOS_NAMESPACE:}
            data-id: ${spring.application.name}-degrade-rules
            group-id: SENTINEL_GROUP
            rule-type: degrade

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

springdoc:
  api-docs:
    path: /v3/api-docs

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId:-}] [%thread] %logger{36} - %msg%n"
```

> `data-id` 里引用 `${spring.application.name}` 在 shared-config 中同样生效（属性源合并时每个服务有自己的 application name）。

各服务私有 `byw-<service>.yml` 至少包含：

```yaml
server:
  port: 8085

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/byw_order?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true

rocketmq:
  name-server: ${ROCKETMQ_NAMESRV:localhost:9876}
  producer:
    group: byw-order-producer
    send-message-timeout: 3000
```

## 四、执行步骤（务必按顺序）

1. **先在 Nacos 发布配置**：`byw-common.yml` + 16 个 `byw-<service>.yml`（内容 = 当前 `bootstrap.yml` 去掉公共块后的剩余部分）。
2. 确认 Nacos 上 17 个 data-id 都已存在且内容正确（控制台逐个核对）。
3. **再改代码**：给每个 `bootstrap.yml` 加 `shared-configs`，并删除已移入 Nacos 的公共块。
4. 启动 1~2 个服务验证（`/actuator/health` + 一次业务调用），再全量启动。
5. 回滚：删掉 `shared-configs` 块并恢复原文件即可（Nacos 上的配置留着不影响）。

## 五、风险与注意

- **顺序不能反**：如果先加 `shared-configs` 而 Nacos 上还没有对应 data-id，服务启动会取不到公共配置
  （Redis / MyBatis-Plus 缺失），表现为启动失败或行为异常。**先发布配置，再改代码。**
- 配置文件从 Git 移到 Nacos 后，**变更不再有代码评审与版本记录**，建议同时导出到本仓库
  `docs/deploy/nacos/` 作为"配置即代码"的备份，并在 Nacos 上开启历史版本。
- `NACOS_NAMESPACE` 多环境时，每个 namespace 都要各发布一份。

## 六、执行记录（2026-10-19）

与原计划的两处差异：

1. **没有为每个服务建 `byw-<service>.yml`**。服务私有配置（端口、库名、rocketmq、jetcache 等）继续留在各服务本地
   `bootstrap.yml`，只把公共块（sentinel 数据源 / redis / mybatis-plus / springdoc）上移到 `byw-common.yml`。
   这样"改公共配置"同样只需改一处，但少维护 16 个 data-id，回滚也只涉及一个 data-id。
2. **`logging` 没有上移**：日志系统早于远程配置初始化，放 Nacos 里不生效（实测）。

执行中发现的坑（详见 `nacos/README.md`）：

- Nacos 配置内容含中文（哪怕注释）会被客户端**静默**解析成空 → 全部保持 ASCII；
- 网关缺 `sentinel-datasource-nacos`，共享配置里的 `spring.cloud.sentinel.datasource.*` 会让它启动失败 → 已补依赖。

验证结果：网关、auth、user、cart、pay、im 六个服务启动正常、`/actuator/health` 200、
`/actuator/env` 显示 sentinel/redis/mybatis-plus/springdoc 四组属性均来自
`bootstrapProperties-byw-common.yml,DEFAULT_GROUP`，登录与 `/api/user/5`（走 Redis 缓存）正常。

## 七、为什么最初没有直接改

本仓库当前**没有正在运行的 Nacos**，无法验证 17 个 data-id 的发布与合并结果；
一旦 `shared-configs` 指向不存在的 data-id，16 个服务会同时启动失败，且本机无法自测。
因此本次只交付可执行的迁移方案，等有可用的 Nacos 环境（或 CI 里起一个）时按上面步骤落地。
