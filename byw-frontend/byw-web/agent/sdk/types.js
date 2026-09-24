/**
 * AI Agent 中间件 —— WebSocket 协议契约（纯类型 + 常量定义，无任何运行时逻辑）。
 *
 * 注意：server/src/protocols/events.ts 与 sdk/src/types.ts 两份文件必须保持完全一致。
 */
export const PROTOCOL_VERSION = "1.0.0";
/* ============================================================
 * 客户端 → 服务端
 * ============================================================ */
export const CLIENT_EVENT = {
    SESSION_START: "session.start",
    TASK_SUBMIT: "task.submit",
    ACTION_RESULT: "action.result",
    CONFIRM_RESPONSE: "confirm.response",
    TASK_CANCEL: "task.cancel",
};
/* ============================================================
 * 服务端 → 客户端
 * ============================================================ */
export const SERVER_EVENT = {
    SESSION_READY: "session.ready",
    RUN_STARTED: "run.started",
    TEXT_DELTA: "text.delta",
    ACTION_REQUEST: "action.request",
    CONFIRM_REQUEST: "confirm.request",
    RUN_FINISHED: "run.finished",
    RUN_ERROR: "run.error",
    STATE_DELTA: "state.delta",
};
