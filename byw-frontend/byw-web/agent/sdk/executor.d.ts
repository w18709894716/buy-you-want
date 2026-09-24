/**
 * Action 执行器：接收服务端的 action.request，调用注册工具或内置 DOM 工具，返回统一结果。
 */
import type { CapabilitySpec } from "./types";
import type { CapabilityRegistry } from "./capability";
/** 执行结果 */
export interface ActionResult {
    ok: boolean;
    result?: unknown;
    error?: string;
}
/** 内置 DOM 工具的能力声明 */
export declare const BUILTIN_DOM_TOOLS: CapabilitySpec[];
/**
 * 执行一个 action：优先从 registry 查找已注册 handler，否则尝试内置 DOM 工具。
 * 统一 25 秒超时，返回 ActionResult。
 */
export declare function executeAction(toolName: string, params: Record<string, unknown>, registry: CapabilityRegistry): Promise<ActionResult>;
