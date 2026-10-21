# 部署运维

本目录给出**可执行的**容器化与流水线配置；K8s/监控/日志聚合仍是后续规划（见文末）。

## 一、目录结构

```
deploy/
├── Dockerfile                 # 通用服务镜像（--build-arg MODULE=byw-xxx）
├── docker-compose.yml         # 中间件：MySQL/Redis/Nacos/RocketMQ/Mongo/ES/MinIO
├── docker-compose.app.yml     # 16 个后端服务
└── rocketmq/broker.conf       # broker 配置（含 autoCreateTopicEnable）
.dockerignore                  # 构建上下文裁剪（排除 node_modules/target/docs）
.github/workflows/ci.yml       # CI：后端打包 + 三端前端构建 + 编排文件校验
```

## 二、本地/演示环境一键起

```bash
# 1) 中间件（首次启动会自动执行 docs/database/*.sql 建库建表）
docker compose -f deploy/docker-compose.yml up -d

# 2) 应用（会按 MODULE 参数逐个构建镜像，首次较慢）
docker compose -f deploy/docker-compose.app.yml up -d --build

# 也可以一条命令起全
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.app.yml up -d --build
```

两个 compose 用**固定网络名 `byw-net`**，所以分两步起也能互相解析服务名。

只重建某个服务：

```bash
docker compose -f deploy/docker-compose.app.yml up -d --build byw-order
```

## 三、单独构建镜像

```bash
docker build -f deploy/Dockerfile --build-arg MODULE=byw-gateway -t byw/byw-gateway:local .
```

镜像特性：多阶段构建（编译用 maven+jdk17，运行用 jre17）、非 root 用户、
`-XX:MaxRAMPercentage=75`（可被环境变量 `JAVA_OPTS` 覆盖）。

## 四、CI（GitHub Actions）

[ci.yml](.github/workflows/ci.yml) 三个 job：

| job | 内容 |
|---|---|
| `backend` | JDK 17 + Maven 缓存 → `mvn -B -ntp -DskipTests package` → 上传各模块 jar |
| `frontend` | 矩阵 `byw-web / byw-admin-web / byw-merchant-web` → `npm ci` → `npm run build` |
| `compose` | `docker compose config -q` 校验两份编排文件（含变量插值） |

> 当前仓库没有自动化测试，因此后端只编译打包；补单测后去掉 `-DskipTests` 即可。
> 16 个镜像的构建没有放进 CI（太慢），发布时按需触发。

## 五、生产上线检查清单

| 项 | 说明 |
|---|---|
| `BYW_SECURITY_INTERNAL_SECRET` | **必填**：网关与服务之间的身份头签名 / 服务间调用凭证密钥。缺失或仍等于源码里的开发默认值时，网关和业务服务**启动即失败**（fail-closed，无兜底默认值）。可由 Nacos `byw-common.yml` 的 `byw.security.internal-secret` 提供，二者有其一即可 |
| `BYW_PAY_MOCK_ENABLED=false` | 关闭模拟支付回调；否则 `/pay/mock/**` 可被登录用户触发 |
| `BYW_PAY_CALLBACK_SECRET` | 真实渠道回调验签密钥；**留空则所有渠道回调都会被拒绝**（fail-closed） |
| `JASYPT_ENCRYPTOR_PASSWORD` | **必填**：配置里的 `ENC(...)`（数据库密码、内部签名密钥）靠它解密；缺失时服务启动直接失败。见 [jasypt/README.md](jasypt/README.md) |
| RocketMQ `brokerIP1` | broker 自动探测可能选中虚拟网卡导致客户端连不上；需显式配置为客户端可达地址 |
| Nacos | 生产建议开鉴权并隔离 namespace；`NACOS_NAMESPACE` 由环境变量注入 |
| 数据库 | 新环境先跑 `docs/database/*.sql` 建库；结构增量脚本在 `docs/database/migration/`，按日期顺序手工执行（无迁移工具，环境对齐由数据库同步工具负责） |
| 前端 | `byw-web` 是 Nuxt（`npm run build` 后用 node 常驻 `.output/server/index.mjs`）；两个 BFF 前端是 Vite（`dist/` 交给 Nginx，`/api` 反代到网关 8080） |
| 限流 | 网关有默认令牌桶（`byw-gateway` 的 `default-filters`），生产按接口再细化 |

## 六、仍未做的部分

- **Kubernetes / Helm**：没有编排清单；
- **监控告警**：actuator 已就位，但没有 Prometheus/Grafana 与告警规则；
- **日志聚合**：仍是本地文件日志，没有 ELK/Loki；
- **镜像仓库与发布流水线**：CI 只产出 jar，没有 push 镜像到 registry。
