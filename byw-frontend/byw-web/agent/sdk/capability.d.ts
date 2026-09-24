/**
 * 能力注册表：宿主页面通过 registerTool 把业务能力注册为智能体工具，
 * AgentClient 组装 session.start 时通过 list() 获取能力声明（CapabilitySpec）。
 */
import type { CapabilitySpec, JSONSchema, RiskLevel } from "./types";
/** 宿主提供的工具处理函数（可同步可异步） */
export type ToolHandler = (params: Record<string, unknown>) => unknown | Promise<unknown>;
/** 工具定义 = 能力声明 + 执行 handler */
export interface ToolDefinition {
    name: string;
    description: string;
    parameters: JSONSchema;
    riskLevel: RiskLevel;
    returns?: JSONSchema;
    handler: ToolHandler;
}
export declare class CapabilityRegistry {
    private tools;
    /** 注册（同名覆盖）一个工具 */
    register(def: ToolDefinition): void;
    unregister(name: string): boolean;
    get(name: string): ToolDefinition | undefined;
    has(name: string): boolean;
    /** 输出能力声明清单（剥离 handler），用于 session.start 的 capabilities */
    list(): CapabilitySpec[];
    clear(): void;
}
/** 默认全局注册表（AgentClient 未显式传入 registry 时使用） */
export declare const defaultRegistry: CapabilityRegistry;
/** 便捷函数：向默认注册表注册工具 */
export declare function registerTool(def: ToolDefinition): void;
