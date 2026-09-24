/**
 * AgentClient：WebSocket 客户端，负责与服务端通信、事件分发、自动重连、action 执行。
 */
import type { RunStatus, ServerEvent, TextDeltaPayload, ConfirmRequestPayload } from "./types";
import { CapabilityRegistry } from "./capability";
export interface AgentClientOptions {
    endpoint: string;
    apiKey: string;
    systemId: string;
    autoDiscoverDom?: boolean;
    widget?: boolean;
    registry?: CapabilityRegistry;
}
export interface RunResult {
    status: RunStatus;
    summary?: string;
    steps: TextDeltaPayload[];
    text: string;
}
/** 客户端发出的事件（透传服务端事件 + 自定义 confirmRequest） */
export type ClientEmittedEvent = ServerEvent | {
    type: "confirmRequest";
    payload: ConfirmRequestPayload;
};
export declare class AgentClient {
    private ws;
    private sessionId;
    private resumeToken;
    private reconnectAttempt;
    private reconnectTimer;
    private destroyed;
    private listeners;
    private pendingRuns;
    /** taskId → runId 映射，用于将 run.started 后的事件关联到正确的 pendingRun */
    private taskToRun;
    private registry;
    private autoDiscoverDom;
    private widgetEnabled;
    /** 当 widget 绑定时置为 true，client 将把 confirm.request 交给 widget 而非 emit */
    _widgetBound: {
        respondConfirm: (invocationId: string, approved: boolean) => void;
    } | null;
    readonly endpoint: string;
    readonly apiKey: string;
    readonly systemId: string;
    constructor(opts: AgentClientOptions);
    connect(): void;
    destroy(): void;
    /** 发送 task.submit，返回 Promise 在 run.finished / run.error 时 resolve / reject */
    ask(instruction: string, context?: Record<string, unknown>): Promise<RunResult>;
    cancel(taskId: string): void;
    /** 响应 confirm.request（widget 或宿主调用） */
    respondConfirm(invocationId: string, approved: boolean): void;
    on(event: string, handler: (data: unknown) => void): void;
    off(event: string, handler: (data: unknown) => void): void;
    private emit;
    private send;
    private sendSessionStart;
    private handleMessage;
    private handleActionRequest;
    private scheduleReconnect;
    private clearReconnectTimer;
    get connected(): boolean;
    get currentSessionId(): string | null;
}
