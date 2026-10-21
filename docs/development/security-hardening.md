# 安全加固与上线检查清单

本页记录已落地的安全加固项、**上线前必须设置的环境变量**，以及尚未闭环的残留风险。

---

## 一、已落地的加固项

| # | 问题 | 处置 | 位置 |
|---|---|---|---|
| 1 | 网关 CORS 为通配来源且允许携带凭证（等于对全网开放跨域） | 改为**来源白名单**（`byw.security.cors.allowed-origins`）；显式配置 `*` 时强制关闭 `allowCredentials` 并告警 | `byw-gateway/CorsConfig` |
| 2 | API 文档（`/v3/api-docs`、`/swagger-ui`）无条件对外暴露，泄露全部接口结构 | 新增开关 `byw.gateway.api-docs.enabled`（默认开并 **warn**）；关闭后直接 404 | `byw-gateway/ApiDocsAccessFilter` |
| 3 | **BCrypt 密码哈希随接口外泄**：管理端会员列表、商家审核列表、商家详情、C 端 `/user/me` 都会返回 `password` | 出网前统一置空（`toSafeDTO` / `toSafeMerchantDTO`）；仅登录校验接口保留哈希，并加注释说明。`/user/me` 是本人已认证端点，只清 `password`，phone/email 属本人信息、个人中心需展示故保留（公开端点 `/user/{userId}` 三者全清） | `byw-user/UserFeignImpl`、`byw-user/UserController`、`byw-shop/ShopServiceImpl` |
| 4 | 文件上传无类型/大小/目录校验，删除无归属校验 | 扩展名白名单 + 大小上限 + folder 白名单 + 对象键校验（禁 `..`、禁绝对路径）；删除接口收紧为**平台管理员** | `byw-file/FileServiceImpl`、`FileUploadController` |
| 5 | 登录无失败锁定，可无限爆破；短信验证码明文打日志、无发送冷却 | 连续 5 次失败锁定 15 分钟（按"端+账号"隔离，StringRedisTemplate INCR 实现）；短信 60s 冷却、日志脱敏、验证码仅 DEBUG 可见 | `byw-auth/AuthServiceImpl` |
| 6 | 用户端把 token 额外写入**非 HttpOnly Cookie**（后端并不读），凭据随请求自动外发 | 移除 Cookie 写入/读取，仅保留 localStorage，并清理历史遗留 Cookie | `byw-web/utils/request.ts` |
| 7 | WebSocket 允许任意来源（CSWSH 风险） | 改为来源白名单（`byw.security.ws.allowed-origins`），配 `*` 时告警 | `byw-im/WebSocketConfig` |

> 另有此前几批已完成的：网关注入身份头的 HMAC 签名、内部 Feign 端点服务间凭证（`@InternalApi`）、
> 租户校验 fail-closed、下单价格/数量不可信、支付金额绑定订单等。

---

## 二、上线前必须设置的环境变量

| 变量 | 作用 | 不设置的后果 |
|---|---|---|
| `BYW_SECURITY_INTERNAL_SECRET` | 身份头签名 / 服务间调用凭证的共享密钥 | 使用源码内置默认值，直连服务端口伪造身份头的防护形同虚设（启动会 warn） |
| `BYW_SNOWFLAKE_WORKER_ID` | 每实例唯一的雪花 workerId（0-31） | 多实例可能生成重复订单号/支付单号（启动会 warn） |
| `BYW_CORS_ALLOWED_ORIGINS` | 允许的前端来源 | 默认只放行 localhost 三个端口，生产前端会被浏览器拦截 |
| `BYW_API_DOCS_ENABLED=false` | 关闭 API 文档 | 全站接口结构对外可见 |
| `BYW_SECURITY_WS_ALLOWED_ORIGINS` | WebSocket 允许来源 | 默认只放行 localhost |
| `MYSQL_PASSWORD` / `REDIS_HOST` 等 | 数据源凭据 | 默认 root/root、localhost |
| `byw.pay.mock-enabled=false` | 关闭模拟支付回调 | 回调不做渠道验签（当前仅限支付单归属用户可触发，仍不应在生产开启） |

**另外两项必须在部署层完成**（代码无法覆盖）：

1. **服务端口网络隔离**：15 个服务的 8081-8095 只允许内网/网关访问，禁止公网直连。
   `@InternalApi` 与身份签名是第二道防线，不是替代品。
2. **HTTPS 终结 + 安全响应头**：建议在网关/入口 Nginx 上补 `Strict-Transport-Security`、
   `X-Content-Type-Options`、`X-Frame-Options`、`Content-Security-Policy`。

---

## 三、尚未闭环的残留风险

| 风险 | 说明 | 建议 |
|---|---|---|
| 文件删除无归属记录 | 文件不落库，无法判断"这个文件是谁上传的"，删除只能按角色粗粒度限制 | 增加 `t_file`（object_key / uploader_id / shop_id），删除时校验归属 |
| 上传接口对匿名开放 | 商家入驻需要游客上传资质，因此 `/file/upload` 保持 `@Public` | 接入人机校验或按 IP 限流；对 bucket 做容量告警 |
| 登录失败按账号限制，未按 IP | 攻击者可用大量不同账号绕过单账号锁定 | 网关侧加 IP 维度限流（Sentinel 热点参数/黑名单） |
| WebSocket token 走 URL query | 浏览器无法在 WS 握手设置请求头，Proxy/网关访问日志可能记录 token | 改为"一次性 ticket 换连接"，或改用 `Sec-WebSocket-Protocol` 承载 |
| 短信验证码无任何校验流程 | `sendSmsCode` 只写入 Redis，注册/登录都不校验它（接入真实通道前是占位实现） | 若要启用短信登录/注册，需补校验 + 失败次数限制 |
| MQ 消息未透传 traceId | 异步链路日志无法与上游串联 | Producer 写 user property + 消费者改用 `RocketMQListener<MessageExt>` |
| 本批安全改动无自动化验证 | 当前仓库不含任何测试用例，上面这些加固项**只能靠手工冒烟验证** | 若打算长期维护，建议至少为"鉴权 / 文件上传 / 登录锁定"补一组接口级测试 |
