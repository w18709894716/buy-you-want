# 配置项加密（Jasypt）

> **状态：已落地（2026-10-20）。** 已在 Boot 3.2.5 + Spring Cloud 2023 + Nacos shared-config 下真机验证。

## 一、加密了什么

| 位置 | 配置项 | 明文 |
|---|---|---|
| 11 个有数据源的服务（im/logistics/order/pay/product/promotion/review/settle/shop/user/cart 的 `bootstrap.yml`） | `spring.datasource.password: 'ENC(...)'` | 原默认值 `root` |
| Nacos 的 `byw-common.yml`（共享配置） | `byw.security.internal-secret: 'ENC(...)'` | 原来的开发默认值 `byw-internal-gateway-secret` |

> 内部签名密钥**加密的是原值**，所以行为不变（网关与服务端仍然互认），只是仓库/配置中心里不再有明文。

## 二、主密钥怎么放（唯一的红线）

主密钥只从**环境变量** `JASYPT_ENCRYPTOR_PASSWORD` 读取。

| 环境 | 做法 |
|---|---|
| 本地开发 | 已生成在 `%USERPROFILE%\.byw-jasypt.key`（64 位十六进制，**不入仓库**）；启动前执行一次：<br>`$env:JASYPT_ENCRYPTOR_PASSWORD = (Get-Content "$env:USERPROFILE\.byw-jasypt.key" -Raw).Trim()` |
| IDE 里跑 | Run Configuration → Environment variables 加上同名变量（**否则服务起不来**，见第四节） |
| CI / K8s / 生产 | 平台 Secret（K8s Secret、GitHub Actions Secret、容器 secret）注入，**绝不写进** `bootstrap.yml`、Nacos 或镜像 |

**绝对不能**把主密钥写进仓库或 Nacos —— 那样等于没加密（密文和钥匙放在一起）。

### 环境变量的生效范围（真机踩过，2026-10-21）

Windows 的环境变量是**进程创建时从父进程复制**的，所以：

- 在终端里 `$env:JASYPT_ENCRYPTOR_PASSWORD = ...` 只对**该窗口及其子进程**有效；
- 用 `[Environment]::SetEnvironmentVariable(..., 'User')` 持久化后，**已经开着的终端 / IDE 仍然拿不到**，
  必须**重开一个终端窗口**（IDE 则要重启），否则表现为服务启动即报
  `either 'jasypt.encryptor.password' ... must be provided`。

判断方法：**新开一个** PowerShell 窗口执行 `$env:JASYPT_ENCRYPTOR_PASSWORD.Length`，应输出 `64`；
为空说明这个窗口仍是旧环境。

想让启动脚本不依赖外部环境，可在脚本开头自愈：

```powershell
if (-not $env:JASYPT_ENCRYPTOR_PASSWORD) {
  $keyFile = Join-Path $env:USERPROFILE '.byw-jasypt.key'
  if (Test-Path $keyFile) { $env:JASYPT_ENCRYPTOR_PASSWORD = (Get-Content $keyFile -Raw).Trim() }
  else { throw "缺少 jasypt 主密钥：请设置 JASYPT_ENCRYPTOR_PASSWORD 或创建 $keyFile" }
}
```

另外两个同类坑：`Start-Process -UseNewEnvironment` 会**丢弃**父进程环境；`Get-Content` 取值后
务必 `.Trim()`（否则密码尾部带换行，表现为"解密失败"而不是"缺密码"）。

## 三、生成 / 查看密文

```powershell
# 加密（输出 ENC(...)）
.\docs\deploy\jasypt\encrypt.ps1 -Text 'root'

# 解密（排查用）
.\docs\deploy\jasypt\encrypt.ps1 -Text 'ENC(xxx)' -Mode decrypt
```

脚本会先编译 `byw-common-core` 并生成依赖 classpath，再调用
`com.byw.common.core.crypto.JasyptEncryptTool`。

注意事项：

- **密文含 `+ / =`，写进 YAML 必须加引号**：`password: 'ENC(...)'`；
- 算法固定为 `PBEWITHHMACSHA512ANDAES_256` + 随机 IV（与 jasypt-spring-boot 默认一致），
  改了算法旧密文就解不开；
- **密文与环境绑定**：换了主密钥或换库密码，必须重新生成密文。临时覆盖数据库密码可以用
  `SPRING_DATASOURCE_PASSWORD` 环境变量（Spring Boot 松散绑定，优先级高于配置文件）。

## 四、行为变化（重要）

1. **没有主密钥时服务启动失败**：jasypt 解不开 `ENC(...)` 会直接抛异常（fail-closed，不会静默用明文）。
   这既是安全特性也是运维成本 —— 所有启动脚本、IDE 配置、CI 都要带这个环境变量。
2. **改密文需要重启**：实测 Nacos 的 refresh 事件会触发（日志 `Refresh keys changed`），
   但用 ```RefreshScope` 观察到的值仍是旧值。密码类配置本来也应重启（连接池不会自动换口令）。
3. **构建必须 `clean package`**：只改共享模块（如 `byw-common-core`）时，Maven 可能认为各服务的
   classes 没变而**跳过 fat jar 重打包**，结果新依赖（jasypt）没进 jar，运行时表现为
   "配置里有 ENC 但没解密" —— 实测踩过**三次**，症状分别是：
   login 403（byw-auth 用未解密的 `ENC(...)` 去签内部请求）、
   数据源 `Access denied`、网关调用 401（`身份头签名校验失败`）。

   三条铁律：
   - 改共享模块后跑 `mvn -DskipTests clean package`；
   - 单个模块用 `-pl <服务> -am`（**不带 `-am` 会从本地仓库取旧 SNAPSHOT**）；
   - `mvn install` 只更新本地仓库，**不会**重打已存在的 fat jar。

## 五、轮换与丢失

| 场景 | 后果 / 做法 |
|---|---|
| 轮换主密钥 | 用新密钥**重新加密**所有 `ENC(...)`，替换配置 → 发布 → 全部服务重启（旧密文在新密钥下解不开） |
| 主密钥泄露 | 所有密文等同明文，必须换密钥 + 换真实密码 |
| 主密钥丢失 | 所有 `ENC(...)` 作废，只能从已知明文重新加密 |
| 新增一个敏感项 | `encrypt.ps1` 生成密文 → 写进对应 `bootstrap.yml` 或共享配置（ASCII）→ 用 `clean package` 重新打包 |

## 六、边界（别指望它防什么）

Jasypt 防的是"**仓库/配置中心泄露后直接看到密码**"。它**防不住**能登录主机的人：
主密钥以环境变量形式存在进程环境里，拿得到进程就能拿到密钥。真正的强隔离要靠
K8s Secret + RBAC、Vault/KMS，以及数据库侧的按服务最小权限账号。
