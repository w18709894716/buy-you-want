import { CLIENT_EVENT, PROTOCOL_VERSION } from "./types";
import { defaultRegistry } from "./capability";
import { executeAction, BUILTIN_DOM_TOOLS } from "./executor";
import { snapshotPage } from "./dom-analyzer";
/**
 * 生成 UUID v4。crypto.randomUUID 只在安全上下文（https / localhost）可用，
 * 用局域网 IP 访问 http 页面时不存在，此时回退到 getRandomValues 自行拼装。
 */
function generateId() {
    const c = globalThis.crypto;
    if (typeof c?.randomUUID === "function")
        return c.randomUUID();
    const bytes = c.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
/* ------------------------------------------------------------------ */
/* AgentClient                                                         */
/* ------------------------------------------------------------------ */
export class AgentClient {
    ws = null;
    sessionId = null;
    resumeToken = null;
    reconnectAttempt = 0;
    reconnectTimer = null;
    destroyed = false;
    listeners = new Map();
    pendingRuns = new Map();
    /** taskId → runId 映射，用于将 run.started 后的事件关联到正确的 pendingRun */
    taskToRun = new Map();
    registry;
    autoDiscoverDom;
    widgetEnabled;
    /** 当 widget 绑定时置为 true，client 将把 confirm.request 交给 widget 而非 emit */
    _widgetBound = null;
    endpoint;
    apiKey;
    systemId;
    constructor(opts) {
        this.endpoint = opts.endpoint;
        this.apiKey = opts.apiKey;
        this.systemId = opts.systemId;
        this.registry = opts.registry ?? defaultRegistry;
        this.autoDiscoverDom = opts.autoDiscoverDom ?? false;
        this.widgetEnabled = opts.widget ?? false;
    }
    /* ---- 连接管理 ---- */
    connect() {
        if (this.destroyed)
            return;
        this.ws = new WebSocket(this.endpoint);
        this.ws.addEventListener("open", () => {
            this.sendSessionStart();
        });
        this.ws.addEventListener("message", (ev) => {
            try {
                this.handleMessage(JSON.parse(ev.data));
            }
            catch {
                /* 忽略无法解析的消息 */
            }
        });
        this.ws.addEventListener("close", () => {
            if (!this.destroyed)
                this.scheduleReconnect();
        });
        this.ws.addEventListener("error", () => {
            /* close 事件紧随其后，重连由 close 触发 */
        });
    }
    destroy() {
        this.destroyed = true;
        this.clearReconnectTimer();
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
        this.pendingRuns.forEach(({ reject }) => reject(new Error("Client destroyed")));
        this.pendingRuns.clear();
        this.taskToRun.clear();
        this.listeners.clear();
    }
    /* ---- 公开 API ---- */
    /** 发送 task.submit，返回 Promise 在 run.finished / run.error 时 resolve / reject */
    ask(instruction, context) {
        const taskId = generateId();
        const result = { status: "success", steps: [], text: "" };
        const promise = new Promise((resolve, reject) => {
            this.pendingRuns.set(taskId, { taskId, resolve, reject, result });
        });
        this.send({
            type: CLIENT_EVENT.TASK_SUBMIT,
            ts: Date.now(),
            payload: { taskId, instruction, context },
        });
        return promise;
    }
    cancel(taskId) {
        this.send({
            type: CLIENT_EVENT.TASK_CANCEL,
            ts: Date.now(),
            payload: { taskId },
        });
    }
    /** 响应 confirm.request（widget 或宿主调用） */
    respondConfirm(invocationId, approved) {
        this.send({
            type: CLIENT_EVENT.CONFIRM_RESPONSE,
            ts: Date.now(),
            payload: { invocationId, approved },
        });
    }
    on(event, handler) {
        if (!this.listeners.has(event))
            this.listeners.set(event, new Set());
        this.listeners.get(event).add(handler);
    }
    off(event, handler) {
        this.listeners.get(event)?.delete(handler);
    }
    /* ---- 私有方法 ---- */
    emit(event, data) {
        this.listeners.get(event)?.forEach((fn) => fn(data));
        this.listeners.get("*")?.forEach((fn) => fn(data));
    }
    send(msg) {
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(msg));
        }
    }
    sendSessionStart() {
        const capabilities = [...this.registry.list()];
        if (this.autoDiscoverDom)
            capabilities.push(...BUILTIN_DOM_TOOLS);
        const meta = { protocol: PROTOCOL_VERSION };
        if (this.resumeToken)
            meta.resumeToken = this.resumeToken;
        this.send({
            type: CLIENT_EVENT.SESSION_START,
            ts: Date.now(),
            payload: {
                apiKey: this.apiKey,
                systemId: this.systemId,
                capabilities,
                meta,
                // resumeToken 同时作为顶层字段发送，兼容服务端两个读取位置
                ...(this.resumeToken ? { resumeToken: this.resumeToken } : {}),
            },
        });
    }
    handleMessage(msg) {
        switch (msg.type) {
            case "session.ready":
                this.sessionId = msg.payload.sessionId;
                this.resumeToken = msg.payload.resumeToken;
                this.reconnectAttempt = 0;
                this.emit("session.ready", msg.payload);
                break;
            case "run.started": {
                const pending = this.pendingRuns.get(msg.payload.taskId);
                if (pending) {
                    this.taskToRun.set(msg.payload.taskId, msg.payload.runId);
                    this.pendingRuns.delete(msg.payload.taskId);
                    this.pendingRuns.set(msg.payload.runId, pending);
                }
                this.emit("run.started", msg.payload);
                break;
            }
            case "text.delta": {
                const pending = this.pendingRuns.get(msg.payload.runId);
                if (pending) {
                    pending.result.steps.push(msg.payload);
                    pending.result.text += msg.payload.delta;
                }
                this.emit("text.delta", msg.payload);
                break;
            }
            case "action.request":
                this.handleActionRequest(msg.payload);
                break;
            case "confirm.request":
                if (this.widgetEnabled && this._widgetBound) {
                    this.emit("confirmRequest", msg.payload);
                }
                else {
                    this.emit("confirmRequest", msg.payload);
                }
                break;
            case "run.finished": {
                const p = this.pendingRuns.get(msg.payload.runId);
                if (p) {
                    p.result.status = msg.payload.status;
                    p.result.summary = msg.payload.summary;
                    p.resolve(p.result);
                    this.pendingRuns.delete(msg.payload.runId);
                    this.taskToRun.delete(p.taskId);
                }
                this.emit("run.finished", msg.payload);
                break;
            }
            case "run.error": {
                const p = this.pendingRuns.get(msg.payload.runId ?? "");
                if (p) {
                    p.result.status = "error";
                    p.reject(new Error(msg.payload.message));
                    this.pendingRuns.delete(msg.payload.runId ?? "");
                    this.taskToRun.delete(p.taskId);
                }
                this.emit("run.error", msg.payload);
                break;
            }
            case "state.delta":
                this.emit("state.delta", msg.payload);
                break;
        }
    }
    async handleActionRequest(payload) {
        const actionResult = await executeAction(payload.toolName, payload.params, this.registry);
        let pageSnapshot;
        if (this.autoDiscoverDom)
            pageSnapshot = snapshotPage();
        this.send({
            type: CLIENT_EVENT.ACTION_RESULT,
            ts: Date.now(),
            payload: {
                invocationId: payload.invocationId,
                ok: actionResult.ok,
                result: actionResult.result,
                error: actionResult.error,
                pageSnapshot,
            },
        });
        this.emit("action.result", {
            invocationId: payload.invocationId,
            ...actionResult,
        });
    }
    /* ---- 重连 ---- */
    scheduleReconnect() {
        this.clearReconnectTimer();
        const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempt), 8000);
        this.reconnectAttempt++;
        this.reconnectTimer = setTimeout(() => {
            if (!this.destroyed)
                this.connect();
        }, delay);
    }
    clearReconnectTimer() {
        if (this.reconnectTimer !== null) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }
    /* ---- 只读访问器 ---- */
    get connected() {
        return this.ws?.readyState === WebSocket.OPEN;
    }
    get currentSessionId() {
        return this.sessionId;
    }
}
