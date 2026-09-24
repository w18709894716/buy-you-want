/**
 * AI Agent 中间件 —— WebSocket 协议契约（纯类型 + 常量定义，无任何运行时逻辑）。
 *
 * 注意：server/src/protocols/events.ts 与 sdk/src/types.ts 两份文件必须保持完全一致。
 */
export declare const PROTOCOL_VERSION = "1.0.0";
/** 统一事件信封：{ type, ts, payload } */
export interface EventEnvelope<TType extends string, TPayload> {
    type: TType;
    /** Unix epoch 毫秒时间戳 */
    ts: number;
    payload: TPayload;
}
/** 简化的 JSON Schema 对象（用于描述能力的参数与返回值） */
export interface JSONSchema {
    type?: string | string[];
    description?: string;
    properties?: Record<string, JSONSchema>;
    required?: string[];
    items?: JSONSchema | JSONSchema[];
    enum?: Array<string | number | boolean | null>;
    additionalProperties?: boolean | JSONSchema;
    [key: string]: unknown;
}
/** 能力风险等级 */
export type RiskLevel = "read" | "write" | "critical";
/** 业务系统注册的能力声明 */
export interface CapabilitySpec {
    name: string;
    description: string;
    parameters: JSONSchema;
    riskLevel: RiskLevel;
    returns?: JSONSchema;
}
export declare const CLIENT_EVENT: {
    readonly SESSION_START: "session.start";
    readonly TASK_SUBMIT: "task.submit";
    readonly ACTION_RESULT: "action.result";
    readonly CONFIRM_RESPONSE: "confirm.response";
    readonly TASK_CANCEL: "task.cancel";
};
export type ClientEventType = (typeof CLIENT_EVENT)[keyof typeof CLIENT_EVENT];
export interface SessionStartPayload {
    apiKey: string;
    systemId: string;
    capabilities: CapabilitySpec[];
    meta?: Record<string, unknown>;
}
export interface TaskSubmitPayload {
    taskId: string;
    instruction: string;
    context?: Record<string, unknown>;
}
export interface ActionResultPayload {
    invocationId: string;
    ok: boolean;
    result?: unknown;
    error?: string;
    pageSnapshot?: unknown;
}
export interface ConfirmResponsePayload {
    invocationId: string;
    approved: boolean;
}
export interface TaskCancelPayload {
    taskId: string;
}
export type SessionStartEvent = EventEnvelope<"session.start", SessionStartPayload>;
export type TaskSubmitEvent = EventEnvelope<"task.submit", TaskSubmitPayload>;
export type ActionResultEvent = EventEnvelope<"action.result", ActionResultPayload>;
export type ConfirmResponseEvent = EventEnvelope<"confirm.response", ConfirmResponsePayload>;
export type TaskCancelEvent = EventEnvelope<"task.cancel", TaskCancelPayload>;
/** 客户端 → 服务端事件判别联合 */
export type ClientEvent = SessionStartEvent | TaskSubmitEvent | ActionResultEvent | ConfirmResponseEvent | TaskCancelEvent;
export declare const SERVER_EVENT: {
    readonly SESSION_READY: "session.ready";
    readonly RUN_STARTED: "run.started";
    readonly TEXT_DELTA: "text.delta";
    readonly ACTION_REQUEST: "action.request";
    readonly CONFIRM_REQUEST: "confirm.request";
    readonly RUN_FINISHED: "run.finished";
    readonly RUN_ERROR: "run.error";
    readonly STATE_DELTA: "state.delta";
};
export type ServerEventType = (typeof SERVER_EVENT)[keyof typeof SERVER_EVENT];
/** run.finished 的终态 */
export type RunStatus = "success" | "cancelled" | "error" | "max_steps";
export interface SessionReadyPayload {
    sessionId: string;
    resumeToken: string;
}
export interface RunStartedPayload {
    taskId: string;
    runId: string;
}
export interface TextDeltaPayload {
    runId: string;
    delta: string;
}
export interface ActionRequestPayload {
    invocationId: string;
    toolName: string;
    params: Record<string, unknown>;
}
export interface ConfirmRequestPayload {
    invocationId: string;
    toolName: string;
    params: Record<string, unknown>;
    description: string;
    riskLevel: RiskLevel;
}
export interface RunFinishedPayload {
    runId: string;
    status: RunStatus;
    summary?: string;
}
export interface RunErrorPayload {
    runId?: string;
    code: string;
    message: string;
}
export interface StateDeltaPayload {
    runId: string;
    patch: Record<string, unknown>;
}
export type SessionReadyEvent = EventEnvelope<"session.ready", SessionReadyPayload>;
export type RunStartedEvent = EventEnvelope<"run.started", RunStartedPayload>;
export type TextDeltaEvent = EventEnvelope<"text.delta", TextDeltaPayload>;
export type ActionRequestEvent = EventEnvelope<"action.request", ActionRequestPayload>;
export type ConfirmRequestEvent = EventEnvelope<"confirm.request", ConfirmRequestPayload>;
export type RunFinishedEvent = EventEnvelope<"run.finished", RunFinishedPayload>;
export type RunErrorEvent = EventEnvelope<"run.error", RunErrorPayload>;
export type StateDeltaEvent = EventEnvelope<"state.delta", StateDeltaPayload>;
/** 服务端 → 客户端事件判别联合 */
export type ServerEvent = SessionReadyEvent | RunStartedEvent | TextDeltaEvent | ActionRequestEvent | ConfirmRequestEvent | RunFinishedEvent | RunErrorEvent | StateDeltaEvent;
