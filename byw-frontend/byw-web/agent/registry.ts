/**
 * 业务能力注册表。
 *
 * 约定大于配置：agent/tools/ 下每个 *.tool.ts 默认导出 ToolDefinition 数组，
 * 本模块在客户端启动时全量收集并注册进 SDK 全局注册表。
 *
 * 新增一个工具 = 在 agent/tools/ 下新建（或修改）一个 *.tool.ts 文件，无需改动这里。
 */
import { registerTool } from '~/agent/sdk/index.js'
import type { ToolDefinition } from '~/agent/sdk/index.js'

const modules = import.meta.glob<{ default: ToolDefinition[] }>('./tools/*.tool.ts', { eager: true })

/** 注册全部业务工具，返回注册的工具名列表（重复调用同名工具会被覆盖，不会重复注册） */
export function registerBusinessTools(): string[] {
  const names: string[] = []
  for (const mod of Object.values(modules)) {
    for (const tool of mod.default ?? []) {
      registerTool(tool)
      names.push(tool.name)
    }
  }
  return names
}
