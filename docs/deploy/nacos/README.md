# Nacos 配置中心（全量托管）

> **状态：2026-10-20 完成全量迁移。** 16 个服务 + 1 份公共配置都在 Nacos 里，本地 `bootstrap.yml` 只剩"自举 + 日志"。

## 一、结构

```
Nacos（group = DEFAULT_GROUP，namespace = 默认）
├── byw-common.yml            公共：sentinel 数据源 / redis / mybatis-plus / springdoc / 内部签名密钥(ENC)
└── services/byw-<name>.yml   私有：datasource(ENC 密码) / rocketmq / jetcache / minio / mongodb / es / 网关路由 / sa-token / byw.*
```

仓库镜像（配置即代码，改这里 → 发布 → 提交 git）：

```
docs/deploy/nacos/
├── byw-common.yml
├── services/byw-<name>.yml   16 个
└── publish.ps1
```

**加载方式**：Spring Cloud Alibaba 会按 `spring.application.name` 自动加载 `byw-<name>.yml`
（日志里可见 `Ignore the empty nacos configuration ... dataId[byw-order.yml]` 或加载成功），
**不需要**在 bootstrap.yml 里逐个声明。

## 二、本地 `bootstrap.yml` 现在只剩 23 行

```yaml
server:
  port: 8085
spring:
  application:
    name: byw-order
  cloud:
    nacos:
      discovery: { server-addr: ..., namespace: ... }
      config:
        server-addr: ...
        namespace: ...
        file-extension: yml
        shared-configs:
          - data-id: byw-common.yml
            group: DEFAULT_GROUP
            refresh: true
logging:
  pattern:
    console: "..."
```

为什么这几项不能上移：

| 项 | 原因 |
|---|---|
| `spring.application.name`、`nacos.discovery/config.server-addr`、`namespace`、`file-extension`、`shared-configs` | **自举**：要读 Nacos 得先知道 Nacos 在哪、自己叫什么 |
| `server.port` | 与部署/端口映射绑定；放 Nacos 反而容易在缺配置时全挤到 8080 |
| `logging.*` | 日志系统早于远程配置初始化，**放 Nacos 不生效**（实测） |

## 三、优先级

`服务名 data-id（byw-<name>.yml） > extension-configs > shared-configs（byw-common.yml） > 本地 bootstrap.yml`

所以"某服务要覆盖公共值"时，直接写进它的 `services/byw-<name>.yml` 即可。

## 四、两条硬规则（都是实测踩出来的）

1. **Nacos 内容必须 ASCII**：含中文（哪怕注释）会被客户端**静默**解析成空 —— 属性源看着加载了却不生效。
   中文说明写在本 README，不要写进 yml。
2. **发布必须走 `publish.ps1`**：PowerShell 5.1 的 `-Body @{}` 会按系统代码页编码表单、破坏非 ASCII；
   脚本用 `curl --data-urlencode "content@file"` 按原始字节上传，并回读比对。

> 另外：`publish.ps1` 自己也要保持 **ASCII-only**（PS 5.1 按 ANSI 读 .ps1，中文注释会导致诡异行为，已踩过）。

## 五、发布与校验

```powershell
.\publish.ps1                          # 递归发布 byw-common.yml + services/*.yml
.\publish.ps1 -DataId byw-order.yml    # 只发一个
.\publish.ps1 -Server http://nacos:8848
```

**验证某个服务是否真的吃到了配置**：

1. 启动日志：`Located property source: [... 'bootstrapProperties-byw-order.yml' ...]`（只证明拉到）；
2. 强证据：临时在本地 `bootstrap.yml` 加 `management.endpoints.web.exposure.include: health,info,env`，
   然后 `GET /actuator/env/spring.datasource.url` 看来源是不是 `bootstrapProperties-byw-order.yml,DEFAULT_GROUP`；
3. 最省事：把某项从本地删掉后服务仍能启动（本次迁移就是这么验证的）。

## 六、迁移时踩的坑

1. **中文内容静默失效**（规则 1）：最初用 PowerShell 发布，中文注释被破坏，排查很久；
2. **网关缺 `sentinel-datasource-nacos`**：共享配置带 `spring.cloud.sentinel.datasource.*` 后网关启动
   `NoClassDefFoundError: ...NacosDataSource`，已补依赖；
3. **改共享模块后必须 `clean package`**：Maven 会因 classes 未变跳过 fat jar 重打包，
   新依赖（jasypt）静默缺失，表现为"配置里有 `ENC(...)` 但没解密"。

## 七、回滚

| 粒度 | 做法 |
|---|---|
| 回滚某个服务 | 用它 `git show HEAD:...` 的历史版本恢复本地 `bootstrap.yml`，并删除 Nacos 上对应 data-id |
| 全部回滚 | 删除 Nacos 的 17 个 data-id + 从 git 恢复 16 个 `bootstrap.yml` |
| 只回滚公共配置 | 删 `byw-common.yml` 并把各服务本地块贴回（注意 `logging` 本来就在本地） |

## 八、新增配置 / 新增服务的流程

| 场景 | 做法 |
|---|---|
| 改公共服务配置 | 改 `byw-common.yml` → `.\publish.ps1` → 提交 → 服务 `refresh: true` 热生效（部分项仍需重启，见 jasypt/README） |
| 改某服务私有配置 | 改 `services/byw-<name>.yml` → 发布 → **重启该服务**（datasource/rocketmq 这类初始化参数不支持热改） |
| 新增一个服务 | 本地 `bootstrap.yml` 照第二节模板（23 行）；在 `services/` 下加 `byw-<name>.yml`；执行 `publish.ps1` |
| 新增敏感项 | 用 [jasypt/encrypt.ps1](jasypt/encrypt.ps1) 生成 `ENC(...)` → 写进对应 yml（**加引号**）→ 发布 |

> 安全边界：当前 Nacos 是 standalone + 内嵌 Derby、**未开鉴权**、明文 HTTP。生产必须开鉴权 + 内网 + TLS，
> 并把配置落到外置 MySQL（集群硬要求）。
