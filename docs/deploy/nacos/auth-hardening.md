# Nacos 开启鉴权（方案，待评审）

> **状态：仅方案，尚未执行。** 目标：让 Nacos 从"无鉴权、明文 HTTP"变成"要账号密码"，
> 闭合当前最大的安全短板（配置中心里已有各库密码密文、内部签名密钥密文、以及全部服务配置）。

## 一、现状（2026-10-20 实测）

| 项 | 值 |
|---|---|
| 版本 / 模式 | **2.3.2 / standalone**（内嵌 Derby，`E:\nacos\data\derby-data`） |
| `nacos.core.auth.enabled` | **false**（所有 API 匿名可读，含全部配置正文） |
| `login_page_enabled` | **false**（控制台登录页关闭） |
| `nacos.core.auth.plugin.nacos.token.secret.key` | 空 |
| `nacos.core.auth.server.identity.key/value` | 空 |
| 传输 | 明文 HTTP（8848 / 9848 gRPC） |
| 默认账号 | `nacos / nacos`（**未改**） |

当前风险：任何能访问 8848 的人可以 **读取/篡改全部配置**（包括用 jasypt 加密的密文，虽然解不开，
但能被替换成攻击者自己的密文）、**注册伪造服务实例**（把网关流量导到假服务）。
注意 Jasypt 保护的只是"密码不以明文出现"，挡不住"改配置"。

## 二、影响面

| 受影响对象 | 变化 | 必须做 |
|---|---|---|
| 16 个服务的 `bootstrap.yml` | 新增 Nacos 账号密码（discovery + config 各一份） | 改代码 + 重启 |
| 所有服务的启动方式 | 需额外注入 `NACOS_USERNAME` / `NACOS_PASSWORD`（或写死弱口令在本地） | 更新启动命令 / IDE 配置 / compose |
| `docs/deploy/nacos/publish.ps1` | v1 API 需要 `accessToken`，脚本要**先登录再发布** | 改脚本 |
| `deploy/docker-compose.yml` | nacos 服务要加 `NACOS_AUTH_ENABLE=true` 等环境变量 | 改编排 |
| 控制台 | 需要登录；默认口令必须改 | 人工操作 |
| Nacos 自身 | 改 `conf/application.properties` + **重启 Nacos** | 运维操作 |

**不会变的**：服务的业务逻辑、配置内容本身（只是读取时要凭证）。

## 三、关键约束（三条，先想清楚再动手）

1. **自举悖论**：读 Nacos 配置需要账号密码，所以**凭证必须放在本地 `bootstrap.yml` 或环境变量**，
   不能放进 Nacos（放进去也要先有凭证才能读）。
2. **fail-closed**：凭证错 / 权限不足时，服务**启动即失败**（拉不到配置、注册不上）。
   这是好事，但意味着"改错会 16 个服务一起起不来"。
3. **权限模型是命名空间级的**：Nacos 2.x 的授权资源形如 `命名空间:*:*`。
   只给"配置读"权限不够 —— **注册服务实例是另一个权限**，漏了会导致服务能读配置但注册不上，
   表现为网关 503（服务列表空）。

## 四、变更清单

### A. Nacos 侧（`E:\nacos\conf\application.properties`）

| 行 | 配置项 | 改为 |
|---|---|---|
| 145 | `nacos.core.auth.enabled` | `true` |
| 163 | `nacos.core.auth.plugin.nacos.token.secret.key` | **必填**：32 字节随机数的 Base64（44 字符），
2.2+ 会校验长度，为空或过短会导致启动/登录报错 |
| 155 | `nacos.core.auth.server.identity.key` | 任意随机串（如 `byw-server`） |
| 156 | `nacos.core.auth.server.identity.value` | 任意随机串（32 位） |
| 142 | `nacos.core.auth.system.type` | 保持 `nacos` |
| 151 | `nacos.core.auth.enable.userAgentAuthWhite` | 保持 `false`（不要开，开了等于留后门） |

> 关于控制台登录页：状态接口里的 `login_page_enabled` 是**派生字段**，`application.properties` 里
> **没有**对应配置项（已查证）。鉴权打开后控制台会要求登录；重启后请实测确认。

生成 token secret key：

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

### B. 账号与权限（控制台或 API）

1. 用 `nacos / nacos` 首次登录 → **立刻修改默认口令**（改成强密码，存到密码管理器）；
2. 新建**专用账号** `byw-app`（服务端用，不给人工使用），密码存环境变量；
3. 新建角色 `byw-role`，把 `byw-app` 绑上去；
4. 给角色授予**公共命名空间**的读写权限：资源选择"命名空间 = public"，
   动作勾**读写**，并确认**配置管理**与**服务管理**两类资源都被授予（控制台里是分开的）；
5. 平台用户（你/我）保留一个运维账号，只用于控制台，不给服务用。

> 不建议让 16 个服务共用 `nacos` 超级账号：一旦某台机器泄露，等于整个配置中心失守。

### C. 应用侧（16 个 `bootstrap.yml`，**本地**）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        username: ${NACOS_USERNAME:byw-app}
        password: ${NACOS_PASSWORD:}      # 只从环境变量来，不写死
      config:
        server-addr: ${NACOS_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        file-extension: yml
        username: ${NACOS_USERNAME:byw-app}
        password: ${NACOS_PASSWORD:}
        shared-configs:
          - data-id: byw-common.yml
            group: DEFAULT_GROUP
            refresh: true
```

> `NACOS_PASSWORD` 无默认值：不注入就启动失败（fail-closed，符合预期）。

### D. 脚本与编排

- `publish.ps1`：改为"先 `POST /nacos/v1/auth/login` 拿 `accessToken` → 后续请求带 `accessToken` 参数"；
- `deploy/docker-compose.yml` 的 nacos 服务加：

```yaml
    environment:
      MODE: standalone
      NACOS_AUTH_ENABLE: "true"
      NACOS_AUTH_TOKEN: ${NACOS_AUTH_TOKEN:?required}          # = token.secret.key（base64）
      NACOS_AUTH_IDENTITY_KEY: ${NACOS_AUTH_IDENTITY_KEY:?}
      NACOS_AUTH_IDENTITY_VALUE: ${NACOS_AUTH_IDENTITY_VALUE:?}
```

## 五、执行步骤（建议顺序）

1. **备份** `E:\nacos\conf\application.properties`（`Copy-Item ... .bak-auth`）；
2. 生成 token secret key / identity key / identity value，先写进一个**临时密码文件**（不进仓库）；
3. 改 application.properties 的 A 表 5 项；
4. **重启 Nacos**（standalone 单进程，约 10 秒）；
5. 验证匿名访问被拒：不带 token 的 `GET /nacos/v1/cs/configs` 应返回 403；
6. 登录控制台 → 改 `nacos` 密码 → 建 `byw-app` + 角色 + 授权；
7. 改 16 个 `bootstrap.yml`（C 表）+ 启动脚本注入 `NACOS_PASSWORD`；
8. **停掉全部服务 → 逐个启动验证**（每起一个看：注册成功 + 加载 `byw-<name>.yml` + `/actuator/health` UP）；
9. 改 `publish.ps1` 与 compose；
10. 更新文档（`nacos/README.md`、`deploy/README.md` 的检查清单）。

## 六、验证清单

| # | 验证 | 期望 |
|---|---|---|
| 1 | 匿名 `GET /nacos/v1/cs/configs?dataId=byw-common.yml` | **403** |
| 2 | 错误密码登录 | 401/403 |
| 3 | `byw-app` 登录 | 拿到 token |
| 4 | 服务启动日志 | `nacos registry ... register finished` + `Listening config: dataId=byw-order.yml` |
| 5 | `/actuator/health` | UP（说明数据源等配置也读到了） |
| 6 | 网关 → 任一服务接口 | 200（说明服务注册正常） |
| 7 | `publish.ps1` 发布 + 回读 | 发布成功、内容一致 |
| 8 | 旧默认账号 `nacos/nacos` | 登录失败（口令已改） |

## 七、回滚（1 分钟内）

1. 把 `nacos.core.auth.enabled` 改回 `false`（其它改动可留着）；
2. 重启 Nacos；
3. 服务无需改（凭证在无鉴权模式下被忽略）。

前提：`application.properties` 的 `.bak-auth` 备份还在。

## 八、风险与对策

| 风险 | 对策 |
|---|---|
| 改错导致 16 个服务全起不来 | 先只重启 1 个服务（如 byw-cart）验证凭证与权限，再批量；回滚只需关 auth 重启 Nacos |
| 只授了"配置"权限、漏了"服务注册" | 验证清单第 4/6 条专门覆盖（注册日志 + 网关能调用） |
| token secret key 用了短/默认值 | 2.2+ 校验长度，为空/过短会启动或登录报错；用 32 字节 base64 |
| 口令散落在 16 个 `bootstrap.yml` | 一律 `${NACOS_PASSWORD:}` 占位，实际值走环境变量/IDE 配置/CI Secret |
| 控制台仍显示无登录页 | `login_page_enabled` 是派生字段（无对应配置项）；若仍有问题，用 `/nacos/v1/auth/login` 走 API 或检查 `nacos.console.ui.enabled` |
| 凭证仍以明文 HTTP 传输 | **本次不解决**：Nacos 本身不支持 HTTPS，需要 Nginx 反代 + 证书（见第九节） |

## 九、本次**不做**但建议排期

1. **TLS / 反代**：在 Nacos 前加 Nginx（443 + 证书），服务改成 `https://` 地址；
   否则鉴权只是"密码明文传输 + 服务端校验"，同网段抓包仍可截获。
2. **Nacos 集群 + 外置 MySQL**：standalone + 内嵌 Derby 无高可用，且配置数据与安装目录绑定。
3. **网络隔离**：只允许应用网段访问 8848/9848，控制台只允许运维网段。
4. **审计**：Nacos 有 `server.tomcat.accesslog`（当前已开），可接入日志平台做"谁改了什么配置"。

## 十、待你确认的三个选择

| 选项 | 我的建议 |
|---|---|
| 用专用账号 `byw-app` 还是继续用 `nacos` 超级账号？ | **专用账号**（泄露影响面更小） |
| 是否同时做 Nginx + TLS？ | **本次不做**，先把鉴权上了；TLS 单独排期（涉及所有服务的 `NACOS_ADDR` 与证书管理） |
| 是否打开控制台登录页？ | 打开（运维要能看配置），账号用运维专用号 |

## 十一、工作量

约 **1.5~2 小时**（含逐个验证），其中人工步骤：改 application.properties、重启 Nacos、建账号授权。
代码改动是 16 个 `bootstrap.yml` 各加 4 行 + 2 个脚本/编排文件。
