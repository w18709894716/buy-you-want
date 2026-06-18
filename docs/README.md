# BuyYouWant 电商平台技术文档

> BuyYouWant 是一套基于 **Spring Cloud 微服务架构** 的全功能电商平台，涵盖用户端商城和管理后台，支持完整的电商业务流程。

---

## 快速开始

**三步启动项目：**

1. **安装中间件** — [中间件安装指南](./start/middleware-setup.md)（Nacos / MySQL / Redis / Kafka / ES / MongoDB）
2. **启动后端** — [后端启动指南](./start/backend-startup.md)（编译 → 建库 → 按顺序启动 11 个服务）
3. **启动前端** — [前端启动指南](./start/frontend-startup.md)（管理端 :5174 / 用户端 :3000）

---

## 文档导航

### 📖 项目指南

| 文档 | 说明 |
|------|------|
| [项目简介](./guide/introduction.md) | BuyYouWant 是什么、核心功能、系统特点 |
| [系统架构](./guide/architecture.md) | 整体架构图、微服务划分、核心业务流程 |
| [技术栈](./guide/tech-stack.md) | 完整版本对照表、技术选型说明 |
| [目录结构](./guide/directory-structure.md) | 项目文件树、模块职责、标准包结构 |

### 🚀 快速启动

| 文档 | 说明 |
|------|------|
| [环境要求](./start/prerequisites.md) | 必需软件版本、可选工具推荐 |
| [中间件安装](./start/middleware-setup.md) | 6 个中间件的详细安装步骤（含踩坑记录） |
| [后端启动](./start/backend-startup.md) | Maven 编译、数据库初始化、服务启动顺序 |
| [前端启动](./start/frontend-startup.md) | 管理端 / 用户端的启动配置 |

### 🗃️ 数据库

| 文档 | 说明 |
|------|------|
| [数据库设计](./database/database-design.md) | 8 个库、22 张表的结构说明、ER 关系图 |
| [SQL 脚本](./database/) | 建库建表 SQL 文件（8个） |

### 🔧 开发规范

| 文档 | 说明 |
|------|------|
| [API 规范](./development/api-conventions.md) | 统一响应格式、错误码一览、认证方式、路由规则 |
| [编码规范](./development/coding-standards.md) | 包结构、命名规范、异常处理、日志规范 |

### 🚢 部署运维（即将更新）

| 文档 | 说明 |
|------|------|
| [部署指南](./deploy/) | Docker 容器化部署、CI/CD 自动化（规划中） |

---

## 技术栈概览

| 层面 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2.5 + Spring Cloud 2023.0.1 |
| 微服务治理 | Spring Cloud Alibaba（Nacos + Sentinel + Seata） |
| 数据层 | MyBatis-Plus 3.5.6 + MySQL 8.0 |
| 缓存 | Redis 7.x（Redisson 3.28.0） |
| 消息队列 | Kafka 3.7.0（KRaft 模式） |
| 搜索引擎 | Elasticsearch 8.13.4 + IK 分词 |
| 文档数据库 | MongoDB 7.0.9 |
| 用户端前端 | Nuxt.js 3.12 + Vue 3 + TailwindCSS |
| 管理端前端 | Vue 3 + Element Plus + Vite |

---

## 项目特色

- 🏗️ **微服务架构** — 12 个独立服务，职责清晰，易于扩展
- 🔒 **分布式事务** — Seata AT 模式保障下单核心链路数据一致性
- ⚡ **高性能库存** — Redis Lua 预扣 + Kafka 异步落库
- 🔍 **全文搜索** — Elasticsearch + IK 分词，支持多维度筛选
- 📨 **事件驱动** — Kafka 消息驱动实现最终一致性
- 🛡️ **安全防护** — JWT + Gateway 全局鉴权 + Sentinel 限流
- 🎯 **秒杀系统** — Redis 预扣 + Sentinel 限流 + Kafka 异步创单
