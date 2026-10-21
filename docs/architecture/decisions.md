# 架构决策记录（ADR）

> 这里记录**已经权衡过、不再讨论**的架构选择，避免复审时反复拉扯。
> 每条包含：结论、理由、代价，以及结论是否已**锁定**（锁定项不再复议；未锁定的会写明重开条件）。

## D1 服务粒度：保持 16 个服务（**已确认，最终形态**）

> **状态：已由项目负责人确认（2026-10-19）—— 现有拆分就是确认后的形态，后续复审不再提出"合并服务"的建议。**
> 本节记录"为什么是这 16 个"，作为后续演进的事实依据；不再讨论合并。

**结论**：byw-gateway / auth / user / product / cart / order / pay / logistics / review / im / promotion / file / shop / settle + 两个 BFF（admin / merchant）。
模块清单以根 `pom.xml` 的 `<modules>` 为准。

**理由**
- 边界清晰：按业务域切分（交易、商品、营销、履约、内容、资金），每个服务一个库（`byw_order` / `byw_product` …），
  数据所有权明确，不存在跨服务写同一张表；
- 故障隔离：商品搜索/IM 这类重 IO 服务抖动，不会把下单链路一起拖垮（配合 Feign 超时 + Sentinel）；
- 独立扩缩容：订单/商品是热点，日志/审核是低频，扩缩容策略本来就不同；
- 学习与演示价值：这个仓库的定位是"微服务全链路样例"，合并成单体就失去意义。

**代价**
- 运维成本高（16 个 JVM，约 8~12 GB 堆内存）；
- 本地一键起要 8 个中间件（已用 `deploy/docker-compose.yml` 收敛）；
- 一次业务改动常要跨 2~3 个仓库模块。

**"合并成单体"的方案：已否决。** 服务边界承载着业务场景（购物车、履约、资金、内容各自独立演进），
合并等于删除这些独立边界，与"只修缺陷、不删业务场景"的项目原则冲突，因此不再作为候选方案。
资源压力通过容器编排与扩缩容解决（见 `deploy/`），而不是靠合并服务来省内存。

## D2 admin 与 merchant 两个 BFF 保持对称，不强行抽象

**结论**：`byw-admin`（8090）与 `byw-merchant`（8093）继续各自实现，不合并成一个"通用 BFF"。

**理由**
- 鉴权模型不同：平台端按 `sys:user/sys:role` 等菜单权限；商家端按 `m:product:*` 且**强制店铺隔离**
  （`UserContext.canAccessShop` 的 fail-closed 校验只在商家侧生效）；
- 参数/返回差异大：列表查询、字段可见性（商家看不到成本价、平台看不到商家私信）都不同；
- 攻击面隔离：平台 BFF 出问题不该影响商家端。

**代价**：两个 BFF 有 ~60% 结构相似的 Controller（都是"校验 + 转发 Feign"）。

**缓解措施（待办）**：抽出 `byw-common-bff`（统一的分页/参数校验/异常映射基类），
而不是合并服务。**不建议**用泛型 Controller 硬合并 —— 权限模型不同，抽象会变成一堆 if。

## D3 Nacos 配置中心：全量托管（2026-10-19 公共配置 + 2026-10-20 服务私有配置）

- **公共**：`byw-common.yml` —— sentinel 数据源 / redis / mybatis-plus / springdoc / 内部签名密钥(ENC)；
- **私有**：`services/byw-<name>.yml` ×16 —— datasource(ENC 密码) / rocketmq / jetcache / minio / mongodb / es / 网关路由 / sa-token / byw.*；
- **本地只留 23 行**：server.port、spring.application.name、nacos 连接与 shared-configs（自举必需）、logging（初始化顺序）；
- 优先级：服务名 data-id > extension-configs > shared-configs > 本地 bootstrap.yml。

两条硬规则（实测，详见 [nacos/README.md](../deploy/nacos/README.md)）：
1. Nacos 配置内容**只能 ASCII** —— 含中文会被客户端静默解析成空；
2. 发布统一走 `publish.ps1`（PowerShell 的 `-Body @{}` 会破坏非 ASCII 内容）。

回滚：删对应 data-id + 从 git 恢复本地 `bootstrap.yml`。

两条硬规则（实测，详见 [nacos/README.md](../deploy/nacos/README.md)）：
1. Nacos 配置内容**只能 ASCII** —— 含中文会被客户端静默解析成空，配置看着加载了却不生效；
2. 改完公共配置必须用 `docs/deploy/nacos/publish.ps1` 发布（PowerShell 的 `-Body @{}` 会破坏非 ASCII 内容）。

回滚：删 `byw-common.yml` + 各服务去掉 `shared-configs` 并恢复本地块。

## D4 数据边界：一服务一库；结构变更走"基线脚本 + 手工增量脚本"

- 每个服务只写自己的库，跨服务读通过 Feign/消息，不做跨库 JOIN；
- **基线**：`docs/database/*.sql`（含 DROP，只在干净库/新环境执行，自带建库与种子数据）；
- **增量**：`docs/database/migration/<日期>_<描述>.sql`，写幂等语句，由运维在各环境手工执行并记录；
- **不引入 Flyway/Liquibase**（2026-10-19 负责人决定）：环境间一致性由后续接入的**数据库同步工具**保证。
  因此这里的取舍是"同步工具负责跨环境对齐，仓库只保存变更脚本本身"；
- 唯一键/生成列这类"不依赖代码纪律"的约束，优先下沉到数据库（见复审 P0-3/P0-4）。

## D5 一致性策略：能补偿就不引入分布式事务

- 资金链路（支付→订单）用 RocketMQ **事务消息**（半消息 + 本地事务），不用 Seata；
- 非资金链路（积分、通知、ES 同步）用"事件 + 幂等消费 + 对账补偿"；
- 定时对账（`SettleServiceImpl`、`OrderTimeoutTask`）兜住消息丢失。

**推翻条件**：出现跨 3 个以上服务的强一致写场景（目前没有）。
