# 脚本方式启动

> 项目提供了 Python 一键启动/停止脚本，简化微服务启动流程，并支持交互式管理单个服务。
> 脚本位于 `docs/scripts/` 目录下。

---

## 脚本一览

| 脚本 | 位置 | 功能 |
|------|------|------|
| `start_all.py` | `docs/scripts/start_all.py` | 一键构建并启动所有服务，支持交互式命令管理 |
| `stop_all.py` | `docs/scripts/stop_all.py` | 一键停止所有服务并关闭 CMD 窗口 |

---

## 前置条件

1. **Python 3.x** 已安装
2. **JDK 17** 已安装，`JAVA_HOME` 环境变量已配置
3. **Maven** 已安装并添加到 PATH
4. **Node.js 18+** 和 **npm** 已安装
5. 所有中间件已启动（Nacos / MySQL / Redis / RocketMQ / ES / MongoDB）

---

## 一键启动：start_all.py

### 基本用法

```bash
python docs/scripts/start_all.py
```

### 可选参数

| 参数 | 说明 |
|------|------|
| `--skip-build` | 跳过 Maven 构建（已构建过时使用） |
| `--skip-frontend` | 跳过前端启动（仅启动后端服务） |

### 使用示例

```bash
# 完整启动（构建 + 后端 + 前端）
python docs/scripts/start_all.py

# 跳过构建，直接启动（代码未修改时使用）
python docs/scripts/start_all.py --skip-build

# 仅启动后端服务
python docs/scripts/start_all.py --skip-frontend

# 快速启动（跳过构建 + 前端）
python docs/scripts/start_all.py --skip-build --skip-frontend
```

### 启动流程

脚本按以下顺序自动启动：

```
阶段 1: Maven 构建
  └─ mvn clean package -DskipTests

阶段 2: Java 微服务（按依赖顺序分 3 批）
  ├─ 第 1 批: byw-gateway, byw-auth
  ├─ 第 2 批: byw-user, byw-product
  └─ 第 3 批: byw-cart, byw-order, byw-pay, byw-logistics,
              byw-review, byw-promotion, byw-admin

阶段 3: 前端应用
  ├─ byw-web       http://localhost:3000
  └─ byw-admin-web http://localhost:5173
```

---

## 交互式命令

启动完成后，脚本进入交互模式，提示符为 `byw>`，可输入命令管理单个服务。

### 命令列表

| 命令 | 说明 | 示例 |
|------|------|------|
| `restart <服务名> [--skip-build]` | 重启指定服务（默认先编译打包） | `restart byw-order` |
| `stop <服务名>` | 停止指定服务 | `stop byw-order` |
| `start <服务名> [--skip-build]` | 启动指定服务（默认先编译打包） | `start byw-order` |
| `status` | 显示所有服务状态 | `status` |
| `list` | 列出所有服务名 | `list` |
| `help` | 显示帮助信息 | `help` |
| `quit` / `exit` / `q` | 退出交互模式（服务继续运行） | `quit` |
| `shutdown` | 停止所有服务（后端 + 前端）并退出 | `shutdown` |

> **注意**：`restart` 和 `start` 命令默认会先执行 `mvn package -pl <模块> -am -DskipTests` 编译打包，编译成功后再启动服务。如果不需要编译，可加 `--skip-build` 跳过。

### 可用服务名

```
后端微服务:
byw-gateway    byw-auth       byw-user       byw-product
byw-cart       byw-order      byw-pay        byw-logistics
byw-review     byw-promotion  byw-admin

前端:
byw-web        byw-admin-web
```

### 交互示例

```
byw> restart byw-order

  --- 重启 byw-order ---
  [OK] byw-order 已停止
  [..] 编译 byw-order ...
  [OK] byw-order 编译完成
  [OK] byw-order :8085  (pid=12345)

byw> restart byw-order --skip-build

  --- 重启 byw-order ---
  [OK] byw-order 已停止
  [OK] byw-order :8085  (pid=12346)

byw> quit

  退出交互模式，服务继续运行...
  停止服务: python docs/scripts/stop_all.py
```

### 典型使用场景

**场景 1：修改了某个服务的代码后重启**

```bash
# 在交互模式下重启该服务（自动编译打包 + 启动）
byw> restart byw-order

# 如果只改了配置文件不需要重新编译，可跳过构建
byw> restart byw-order --skip-build
```

**场景 2：只想启动后端，不启动前端**

```bash
python docs/scripts/start_all.py --skip-frontend
```

**场景 3：快速重启单个服务（不重新构建）**

```bash
# 直接启动脚本，跳过构建和前端
python docs/scripts/start_all.py --skip-build --skip-frontend
# 进入交互模式后
byw> restart byw-order --skip-build
byw> quit
```

---

## 一键停止：stop_all.py

### 基本用法

```bash
python docs/scripts/stop_all.py
```

### 功能说明

- 停止所有 Java 微服务（11 个）
- 停止前端开发服务器（byw-web、byw-admin-web）
- **关闭所有 CMD 窗口**（通过窗口标题查找进程）

### 查找策略

脚本按以下优先级查找并停止服务：

1. **窗口标题匹配** — 通过 `tasklist` 查找窗口标题包含服务名的 CMD 进程
2. **JPS 查找** — 通过 `jps -l` 查找 Java 进程
3. **端口查找** — 通过 `netstat` 查找占用指定端口的进程（前端服务）

---

## 服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| byw-gateway | 8080 | API 网关 |
| byw-auth | 8081 | 认证服务 |
| byw-user | 8082 | 用户服务 |
| byw-product | 8083 | 商品服务 |
| byw-cart | 8084 | 购物车服务 |
| byw-order | 8085 | 订单服务 |
| byw-pay | 8086 | 支付服务 |
| byw-logistics | 8087 | 物流服务 |
| byw-review | 8088 | 评价服务 |
| byw-promotion | 8089 | 营销服务 |
| byw-admin | 8090 | 管理后台服务 |
| byw-web | 3000 | 用户端前端 |
| byw-admin-web | 5173 | 管理端前端 |

---

## 注意事项

1. **首次启动前** 必须先执行 `npm install`（前端依赖安装）
2. **代码修改后** 需要重新编译：`mvn compile` 或 `mvn package -DskipTests`
3. **交互模式下** 退出（quit）不会停止服务，需手动执行 `python docs/scripts/stop_all.py`
4. **shutdown 命令** 会停止所有服务，等同于执行 `python docs/scripts/stop_all.py`
5. **中间件不在脚本管理范围内**，需单独启动和停止

---

## 常见问题

### Q: 启动脚本报错 "jar 不存在"

A: 需要先执行 Maven 构建：
```bash
mvn clean package -DskipTests
```

### Q: 重启服务后端口仍被占用

A: 等待几秒让端口释放，或手动停止占用进程：
```bash
# 查找占用端口的进程
netstat -ano | findstr :8085
# 停止进程
taskkill /PID <pid> /F
```

### Q: 如何只重启前端？

A: 前端支持热更新，修改代码后自动刷新，无需重启。如需重启：
```bash
# 停止前端窗口后，重新启动
python docs/scripts/start_all.py --skip-build
```
