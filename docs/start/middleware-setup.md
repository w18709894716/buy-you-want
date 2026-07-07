# 中间件安装

本文档详细说明 BuyYouWant 电商平台所需各中间件的安装与配置步骤。

---

## Nacos 2.3.2（服务注册与配置中心）

### 下载

- GitHub Releases: https://github.com/alibaba/nacos/releases/tag/2.3.2
- 选择 `nacos-server-2.3.2.zip`

### 安装步骤

1. 解压到任意目录（如 `D:\nacos`）
2. 单机模式启动（开发环境）：
   - Windows：进入 `bin` 目录，执行：
     ```bash
     startup.cmd -m standalone
     ```
   - 默认端口 **8848**
3. 访问管理控制台：http://localhost:8848/nacos
4. 默认账号密码：`nacos` / `nacos`

### 验证

浏览器访问 http://localhost:8848/nacos 能看到登录页面即表示启动成功。

---

## MySQL 8.0（关系数据库）

### 安装

- 下载 MySQL Installer：https://dev.mysql.com/downloads/installer/
- 选择 **Server only** 或 **Full** 安装
- 配置 root 密码（请牢记此密码）
- 默认端口 **3306**

### 初始化数据库

项目包含 8 个 SQL 脚本，按以下顺序执行：

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

也可以在 Navicat 或 DBeaver 中打开并逐个执行每个 SQL 文件。

### 验证

```sql
SHOW DATABASES;
-- 应看到以下数据库:
-- byw_user, byw_product, byw_cart, byw_order,
-- byw_pay, byw_logistics, byw_review, byw_promotion
```

---

## Redis 7.x（分布式缓存）

### Windows 安装

- 下载 Windows 版本：https://github.com/tporadowski/redis/releases
- 选择 `Redis-x64-x.x.xxx.msi` 安装包
- 运行安装程序，自动安装并启动服务
- 默认端口 **6379**

### 验证

```bash
redis-cli ping
# 应返回 PONG
```

---

## RocketMQ 5.x（消息队列）

### 下载

- 官方下载页: https://rocketmq.apache.org/download/
- **Binary 直接下载**: https://dist.apache.org/repos/dist/release/rocketmq/5.5.0/rocketmq-all-5.5.0-bin-release.zip
- 选择 **bin-release.zip**（编译好的二进制包），不要选 source-release.zip

### 安装步骤

1. 解压到目录（如 `D:\rocketmq-5.5.0`），记住这个路径

2. 设置环境变量（二选一）：

   **方式一：永久设置**（推荐，`setx` 设置后**当前窗口不生效**，需新开窗口）
   ```cmd
   setx ROCKETMQ_HOME "D:\rocketmq-5.5.0"
   ```
   > 设置完成后，**必须新开一个 CMD 窗口**才能使用 `%ROCKETMQ_HOME%`

   **方式二：临时设置**（仅当前窗口有效，立即生效）
   ```cmd
   set ROCKETMQ_HOME=D:\rocketmq-5.5.0
   ```

3. 启动 NameServer（新开 CMD 窗口）：
   ```cmd
   cd /d %ROCKETMQ_HOME%\bin
   start mqnamesrv.cmd
   ```
   - 启动成功后会弹出新窗口显示 NameServer 日志
   - 默认端口 **9876**

4. 启动 Broker（再新开 CMD 窗口）：
   ```cmd
   cd /d %ROCKETMQ_HOME%\bin
   start mqbroker.cmd -n localhost:9876
   ```
   - 启动成功后会弹出新窗口显示 Broker 日志
   - 默认端口 **10911**

> **注意**：
> - 请将 `D:\rocketmq-5.5.0` 替换为你实际的解压路径
> - 使用 `cd /d` 可跨盘符切换（如从 C 盘切换到 E 盘），避免因盘符不同导致路径不变

### 验证

```cmd
# 查看 NameServer 弹窗日志，出现 "The Name Server boot success" 即表示启动成功
# 或使用 rocketmq-dashboard 可视化管理
```

### 可选：安装 Dashboard（可视化管理界面）

> Dashboard 是可选组件，用于可视化管理 RocketMQ 的 Topic、Consumer、消息查询等。
> **注意**：Dashboard 没有预编译的二进制包，需要从源码构建。

#### 下载源码

- GitHub 仓库: https://github.com/apache/rocketmq-dashboard
- 下载 2.1.0 版本源码: https://github.com/apache/rocketmq-dashboard/archive/refs/tags/rocketmq-dashboard-2.1.0.zip

#### 构建步骤

1. 解压源码到任意目录（如 `D:\rocketmq-dashboard`）

2. 进入源码目录，执行 Maven 构建：
   ```bash
   cd D:\rocketmq-dashboard
   mvn clean package -DskipTests
   ```

3. 构建完成后，jar 包位于 `target/rocketmq-dashboard-2.1.0.jar`

#### 启动 Dashboard

```bash
java -jar target/rocketmq-dashboard-2.1.0.jar --rocketmq.config.namesrvAddr=localhost:9876 --server.port=8180
```

- 默认端口 **8080**（如与项目冲突，可加 `--server.port=8180` 修改）
- 访问地址: http://localhost:8180
- 默认无需登录，可直接使用

#### 功能说明

- **Topic 管理**：查看/创建/删除 Topic，查看 Topic 统计信息
- **Consumer 管理**：查看 Consumer Group 消费进度、在线消费者
- **消息查询**：按 Topic + 时间范围 / Message Key / Message ID 查询消息
- **消息轨迹**：追踪消息从发送到消费的完整链路

---

## Elasticsearch 8.13.4（全文搜索引擎）

### 下载

- https://www.elastic.co/downloads/past-releases/elasticsearch-8-13-4
- 选择 **Windows zip** 格式

### 安装步骤

1. 解压到目录（如 `D:\elasticsearch-8.13.4`）

2. **重要：开发环境需修改配置**
   - 编辑 `config\elasticsearch.yml`
   - 添加以下配置：
     ```yaml
     xpack.security.enabled: false
     xpack.security.http.ssl.enabled: false
     discovery.type: single-node
     ```

3. **解决 JDK 问题（推荐）**：
   - ES 8.x 自带 JDK，建议设置环境变量让它使用自带的 JDK：
     ```bash
     set ES_JAVA_HOME=D:\elasticsearch-8.13.4\jdk
     ```
   - 设置后再执行启动命令

4. 启动：
   ```bash
   bin\elasticsearch.bat
   ```

5. 默认端口 **9200**

### ⚠️ 常见错误

如果启动报 `dt.jar` 找不到：
- **原因**：ES 使用了系统 `JAVA_HOME` 而非自带 JDK，版本不匹配
- **解决**：设置 `ES_JAVA_HOME` 环境变量指向 ES 自带的 JDK 目录（如 `D:\elasticsearch-8.13.4\jdk`）

### 验证

```bash
curl http://localhost:9200
# 应返回 ES 集群信息 JSON
```

---

## MongoDB 7.0.9（文档数据库）

### 下载

- https://www.mongodb.com/try/download/community
- 选择 **Windows x64 MSI** 安装包

### 安装步骤

1. 运行安装程序
2. 选择 **Custom**（自定义）安装类型，这样可以设置安装路径
3. 选择安装路径（如 `D:\MongoDB\Server\7.0`）
4. 勾选 **Install MongoDB as a Service**
5. 端口默认 **27017**
6. 完成安装

### 验证

```bash
mongosh
# 进入 MongoDB Shell 即表示安装成功
```

---

## Seata 2.0.0（分布式事务，可选）

> **说明**：Seata 是可选组件。如果不需要分布式事务，可以不启动 Seata Server，byw-order 会持续打印警告日志但不影响基本业务。

### 下载

- https://github.com/apache/incubator-seata/releases/tag/v2.0.0
- 选择 `seata-server-2.0.0.zip`

### Seata Server 配置

解压后编辑 `conf/application.yml`，配置注册中心为 Nacos：

```yaml
seata:
  registry:
    type: nacos
    nacos:
      server-addr: 127.0.0.1:8848
      namespace: ""
      cluster: default
  store:
    mode: file
```

> **注意**：`group` 和 `application` 不配则使用默认值 `DEFAULT_GROUP` / `seata-server`。如果配了自定义 group（如 `SEATA_GROUP`），客户端也必须配相同的 group，否则互相找不到。

### 客户端配置（byw-order）

`byw-order/src/main/resources/bootstrap.yml` 中的 Seata 配置：

```yaml
seata:
  enabled: true
  application-id: byw-order
  tx-service-group: byw-order-tx-group
  service:
    vgroup-mapping:
      byw-order-tx-group: default    # 值必须与 Server 的 cluster 一致
  registry:
    type: nacos
    nacos:
      server-addr: localhost:8848
      namespace: ""
  config:
    type: file                       # 从本地 yml 读取 vgroup 映射
```

### 配置对齐要点

| 配置项 | Seata Server | byw-order（客户端） | 说明 |
|--------|-------------|-------------------|------|
| Nacos 地址 | `registry.nacos.server-addr` | `registry.nacos.server-addr` | 必须相同 |
| Namespace | `registry.nacos.namespace` | `registry.nacos.namespace` | 必须相同 |
| Cluster | `registry.nacos.cluster` | `service.vgroup-mapping.xxx` | 值必须对应 |
| Group | `registry.nacos.group`（可选） | `registry.nacos.group`（可选） | 都配或都不配 |
| Config | - | `config.type: file` | 从本地读取 vgroup 映射 |

### 启动

```bash
bin\seata-server.bat
```

默认端口：**8091**（客户端连接）/ **7091**（TC 通信）

### 验证

1. 访问 Nacos 控制台 http://localhost:8848/nacos
2. 服务管理 → 服务列表，应看到 `seata-server` 服务
3. 启动 byw-order，日志中无 `no available service` 报错

### 常见问题

| 错误 | 原因 | 解决 |
|------|------|------|
| `ConfigNotFoundException: service.vgroupMapping...` | `config.type` 配成了 `nacos`，去 Nacos 找 vgroup 映射但找不到 | 改为 `config.type: file` |
| `no available service found in cluster 'default'` | Seata Server 未启动，或 Server/Client 的 Nacos 地址/namespace/group 不一致 | 启动 Server 并检查配置对齐 |
| 启动后 Nacos 看不到 seata-server | Server 的 `registry.type` 没配成 `nacos` | 检查 `conf/application.yml` |

---

## Sentinel Dashboard 1.8.8（限流熔断控制台）

### 下载

- https://github.com/alibaba/Sentinel/releases/tag/1.8.8
- 下载 `sentinel-dashboard-1.8.8.jar`

### 启动

```bash
java -Dserver.port=8858 -jar sentinel-dashboard-1.8.8.jar
```

- 默认端口 **8858**（Sentinel 默认端口，避免与项目服务 8080-8090 冲突）
- 访问地址：http://localhost:8858
- 默认账号密码：`sentinel` / `sentinel`

### 服务发现

Dashboard 不会自动显示所有服务，需要满足以下条件：

1. **先启动 Dashboard**，再启动业务服务
2. **至少访问一次接口**，Dashboard 才会显示该服务（Sentinel 按需上报）
3. Sentinel 日志位于 `C:\Users\{用户名}\logs\csp\`

### 流控规则配置

Dashboard 中会显示**所有 HTTP 请求**的资源（自动 URL 埋点），资源名格式为 `GET:/xxx`。不仅限于代码中 `@SentinelResource` 标注的接口，所有接口都可以配置限流规则。

详细配置说明见 [Sentinel 配置指南](../guide/sentinel-setup.md)。

### 验证

1. 启动 Dashboard 并访问 http://localhost:8858
2. 启动业务服务（如 byw-product）
3. 访问一次业务接口（如 `curl http://localhost:8083/product/category/tree`）
4. 刷新 Dashboard 页面，左侧应出现对应服务
