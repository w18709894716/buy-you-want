# BuyYouWant 电商平台

> BuyYouWant 是一套基于 **Spring Cloud 微服务架构** 的全功能电商平台，已演进为**多商家入驻平台**，涵盖用户端商城、商家端工作台与平台管理后台，支持从店铺入驻、商品审核、多商家拆单下单到结算分账的完整业务流程。

📚 **完整技术文档入口：[docs 文档中心](docs/README.md)**

---

## 快速开始

1. **安装中间件** — [中间件安装指南](docs/start/middleware-setup.md)（Nacos / MySQL / Redis / RocketMQ / ES / MongoDB / Sentinel）
2. **启动后端** — [后端启动指南](docs/start/backend-startup.md)（编译 → 建库 → 按顺序启动 15 个服务）
3. **启动前端** — [前端启动指南](docs/start/frontend-startup.md)（用户端 :3000 / 管理端 :5174 / 商家端 :5175）
4. **脚本启动**（推荐，可跳过步骤 2、3） — [脚本启动指南](docs/start/script-startup.md)（一键启动/停止前后端，支持交互式重启单个服务）

---

## 文档导航

### 📖 项目指南

| 文档 | 说明 |
|------|------|
| [项目简介](docs/guide/introduction.md) | BuyYouWant 是什么、核心功能、系统特点 |
| [系统架构](docs/guide/architecture.md) | 整体架构图、微服务划分、核心业务流程 |
| [技术栈](docs/guide/tech-stack.md) | 完整版本对照表、技术选型说明 |
| [目录结构](docs/guide/directory-structure.md) | 项目文件树、模块职责、标准包结构 |

### 🚀 快速启动

| 文档 | 说明 |
|------|------|
| [环境要求](docs/start/prerequisites.md) | 必需软件版本、可选工具推荐 |
| [中间件安装](docs/start/middleware-setup.md) | 7 个中间件的详细安装步骤（含踩坑记录） |
| [后端启动](docs/start/backend-startup.md) | Maven 编译、数据库初始化、服务启动顺序 |
| [前端启动](docs/start/frontend-startup.md) | 管理端 / 用户端的启动配置 |
| [脚本方式启动](docs/start/script-startup.md) | 一键启动/停止脚本的使用方法与交互命令 |

### 🗃️ 数据库

| 文档 | 说明 |
|------|------|
| [数据库设计](docs/database/database-design.md) | 10 个库的结构说明、ER 关系图、多商家增量 |

### 🔧 开发规范

| 文档 | 说明 |
|------|------|
| [API 规范](docs/development/api-conventions.md) | 统一响应格式、错误码一览、认证方式、路由规则 |
| [编码规范](docs/development/coding-standards.md) | 包结构、命名规范、异常处理、日志规范 |

### 🚢 部署运维（即将更新）

| 文档 | 说明 |
|------|------|
| [部署指南](docs/deploy/README.md) | Docker 容器化部署、CI/CD 自动化（规划中） |

---

## 技术栈概览

| 层面 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 + Spring Cloud 2023.0.1 |
| 微服务治理 | Spring Cloud Alibaba（Nacos + Sentinel + Seata） |
| 数据层 | MyBatis-Plus 3.5.6 + MySQL 8.0 |
| 缓存 | Redis 7.x（Redisson 3.28.0） |
| 消息队列 | RocketMQ 5.x |
| 搜索引擎 | Elasticsearch 8.13.4 + IK 分词 |
| 文档数据库 | MongoDB 7.0.9 |
| 用户端前端 | Nuxt.js 3.12 + Vue 3 + TailwindCSS |
| 管理端前端 | Vue 3 + Element Plus + Vite |
| 商家端前端 | Vue 3 + Element Plus + Vite |

---

## 项目特色

- 🏗️ **微服务架构** — 15 个独立服务，职责清晰，易于扩展
- 🏪 **多商家平台** — 店铺入驻审核、商品审核工作流、shop_id 多租户隔离、商家端工作台
- ✂️ **多商家拆单** — 购物车按店铺分组，父订单聚合支付 + 子订单按店铺独立履约
- 💰 **结算分账** — 按分类佣金率计算、T+N 冷静期入账、商家提现与平台审批
- 🔐 **分布式事务** — Seata AT 模式保障下单核心链路数据一致性
- ⚡ **高性能库存** — Redis Lua 预扣 + RocketMQ 异步落库
- 🔍 **全文搜索** — Elasticsearch + IK 分词，支持多维度筛选
- 📨 **事件驱动** — RocketMQ 消息驱动实现最终一致性
- 🛡️ **安全防护** — JWT + Gateway 全局鉴权 + Sentinel 限流
- 🎯 **秒杀系统** — Redis 预扣 + Sentinel 限流 + RocketMQ 异步创单
