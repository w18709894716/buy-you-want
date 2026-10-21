# 可观测性（日志追踪与健康检查）

本页记录项目当前的日志追踪与健康检查能力，以及刻意保留的边界。

---

## 一、日志追踪（traceId）

项目未接入完整的分布式追踪后端（OTel/Zipkin），采用**最小可用组合**：

1. **入口生成/透传**：`byw-common-core` 的 `TraceIdFilter`（自动装配，仅 Servlet 应用生效）
   读取请求头 `X-Trace-Id`（做字符白名单校验，防日志注入），无则生成，写入 MDC 与响应头；
2. **跨服务透传**：`FeignTraceIdInterceptor`（byw-common-security）在每次 Feign 调用带上 `X-Trace-Id`；
3. **日志格式**：各服务 `bootstrap.yml` 的 `logging.pattern.console` 打印 `[%X{traceId:-}]`。

于是"网关 → 服务 → Feign 下游"的同步链路，可以用同一个 traceId 串起日志：

```bash
grep '<traceId>' *.log
```

> **未覆盖**：RocketMQ 消息头透传。异步链路的 traceId 需要在 Producer 上写 user property、
> 并把消费者改为 `RocketMQListener<MessageExt>` 才能读取，属于后续工作。

### SQL 日志

原先各服务 `mybatis-plus.configuration.log-impl` 配置为 `StdOutImpl`，会把**所有 SQL 直接打到 stdout**，
生产环境噪音极大且无法按级别收敛。现已移除该配置，MyBatis 自动发现 SLF4J：
需要排查 SQL 时按 logger 打开 DEBUG 即可，例如：

```yaml
logging:
  level:
    com.byw.order.mapper: debug
```

---

## 二、健康检查

`byw-common-core` 引入了 `spring-boot-starter-actuator`（唯一被所有服务依赖的模块，避免 16 处重复声明），
各服务暴露 `/actuator/health`，仅内网可达（网关不路由 `/actuator`），并已在 `AuthInterceptor` 的
排除列表中加入 `/actuator/**`，避免健康检查被鉴权拦成 401。

```bash
curl http://localhost:8085/actuator/health   # {"status":"UP"}
```

---

## 三、操作审计日志（落库 + 管理端可查）

各业务方法的 `@OperationLog(module, operation, description)` 由切面采集，**异步**落库，管理端可查。

```
业务方法(@OperationLog)
   └─ OperationLogAspect（byw-common-log）
        ├─ 文件日志：[操作日志] 模块/操作/用户/方法 + 成功/失败 + 耗时
        └─ RocketMQ topic: operation-log（asyncSend，绝不阻塞业务）
              └─ OperationLogConsumer（byw-user）
                    └─ t_operation_log（byw_user 库，只追加）
                          └─ GET /api/admin/system/operation-log（管理端页面：系统管理 → 操作日志）
```

设计要点：

- **不拖垮业务**：投递用 `asyncSend`。曾用 `syncSend`，broker 抖动时阻塞到生产者超时（3s），
  直接把调用方 BFF 的 Feign 读超时（5s）打爆，业务接口变成 500 —— 审计不能影响业务响应时间；
- **不记假成功**：成功且处于事务中时，注册 `TransactionSynchronization.afterCommit` 才投递，
  避免"日志说成功、事务回滚了库里却没有"；失败（抛异常）则立即投递，失败尝试同样留痕；
- **切面异常全吞**：构建/投递的任何异常只 `warn`，审计问题不影响业务；
- **字段与列宽对齐**并在切面截断（超长会导致消费者反复失败）；
- 记录的操作人为网关注入的身份（`UserContext`），含 `operatorType`（sys/merchant/c）与 `traceId`，便于与日志文件对照。

部署/迁移：`docs/database/migration/2026-10-16_operation-log.sql`（建表 + 新增菜单 `系统管理 → 操作日志`；
super_admin 走 `*` 通配无需再绑角色）。

> **注意**：消费链路依赖 RocketMQ。broker 若把虚拟网卡地址（如 `172.18.0.1`）注册到 NameServer，
> 本机客户端会连不上（报 `connect to 172.18.0.1:10911 failed`）——需在 `conf/broker.conf` 显式
> `brokerIP1 = 127.0.0.1`（或宿主可达 IP）并用 `-c` 指定该配置启动 broker。

---

## 四、仍未接入的能力（按需再补）

- **指标（metrics）**：actuator 已就位，但没有 Prometheus 抓取与看板；需要的话在
  `management.endpoints.web.exposure.include` 打开 `metrics,prometheus` 并部署采集端；
- **链路追踪后端**：无 OTel/Brave 采集，因此没有跨服务 span 与调用拓扑；
- **日志聚合**：本地文件日志，没有 ELK/Loki；
- **告警**：补偿失败、死信、退款卡单目前只有 `log.error`/`log.warn`，没有推送到告警通道。
