/**
 * @aam/sdk — 公共入口，导出所有 API 和类型。
 */
// 核心类
export { AgentClient } from "./client";
export { AgentWidget } from "./widget";
export { CapabilityRegistry, defaultRegistry, registerTool } from "./capability";
// DOM 分析
export { snapshotPage, generateSelector } from "./dom-analyzer";
// 执行器
export { executeAction, BUILTIN_DOM_TOOLS } from "./executor";
// 协议类型（全量 re-export）
export { PROTOCOL_VERSION, CLIENT_EVENT, SERVER_EVENT, } from "./types";
