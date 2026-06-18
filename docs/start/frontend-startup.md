# 前端启动

## 管理端（byw-admin-web）

### 技术栈

- Vue 3.4 + TypeScript
- Element Plus 2.7（UI 组件库）
- Vite 5.2（构建工具）
- Pinia 2.1（状态管理）
- ECharts 5.5（数据可视化）
- Axios 1.6（HTTP 客户端）

### 启动步骤

```bash
cd byw-frontend/byw-admin-web
npm install
npm run dev
```

访问 http://localhost:5174

### 构建生产版本

```bash
npm run build
npm run preview
```

---

## 用户端（byw-web）

### 技术栈

- Nuxt.js 3.12（SSR 框架）
- Vue 3.4
- Pinia 2.1（状态管理）
- TailwindCSS 6.12（CSS 框架）
- VueUse 10.9（工具库）

### 启动步骤

```bash
cd byw-frontend/byw-web
npm install
npm run dev
```

访问 http://localhost:3000

### 构建生产版本

```bash
npm run build
node .output/server/index.mjs
```

---

## 配置后端 API 地址

前端项目中所有 API 请求统一通过 Gateway 入口代理到后端：**http://localhost:8080**

### 管理端（byw-admin-web）

在 `vite.config.ts` 中已配置代理：

```ts
server: {
  port: 5174,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

前端请求 `/api/xxx` 会自动代理到 `http://localhost:8080/api/xxx`。

### 用户端（byw-web）

在 `nuxt.config.ts` 中已配置路由代理：

```ts
routeRules: {
  '/api/**': {
    proxy: 'http://localhost:8080/**',
  },
},
```

也可通过环境变量 `NUXT_PUBLIC_API_BASE` 自定义 API 基础路径。

---

## 常见问题

### npm install 报 vite 找不到

- **原因**：未执行 `npm install`，`node_modules` 不存在
- **解决**：先执行 `npm install` 再执行 `npm run dev`

### 端口冲突

- 管理端默认端口 **5174**，用户端默认端口 **3000**
- 如需修改管理端端口，在 `vite.config.ts` 中修改 `server.port`
- 如需修改用户端端口，在 `nuxt.config.ts` 中配置 `devServer.port`
