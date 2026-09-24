import type { AgentClient } from "./client";
export interface AgentWidgetOptions {
    /** 挂载容器，默认 document.body */
    container?: Element;
    /** 触发按钮距视口底部偏移（px），默认 24。接入方右下角已有悬浮入口时用它错开 */
    bottom?: number;
    /** 触发按钮距视口右侧偏移（px），默认 24 */
    right?: number;
    /**
     * 消息历史的 localStorage 键名后缀；传入后消息会落盘并在下次构造时回填。
     * 建议带上用户标识（如 `byw-web:user-7`），否则同一浏览器上的不同用户会互相看到对方的记录。
     * 不传则不持久化。
     */
    persistKey?: string;
}
export declare class AgentWidget {
    private shadow;
    private host;
    private panel;
    private triggerBtn;
    private messagesEl;
    private inputEl;
    private confirmOverlay;
    private visible;
    private client;
    private bottom;
    private right;
    private persistKey;
    private dragging;
    private dragOffX;
    private dragOffY;
    constructor(client: AgentClient, options?: AgentWidgetOptions);
    show(): void;
    hide(): void;
    destroy(): void;
    private buildUI;
    private startDrag;
    private onDragMove;
    private onDragEnd;
    private togglePanel;
    private handleSend;
    private addMessage;
    private appendMessage;
    private historyStorageKey;
    /**
     * 运行期补设历史存储键。接入方的用户标识常常异步才拿到（如登录态恢复要先请求 /user/me），
     * 此时可以先用 undefined 构造、拿到标识后再补设，避免为了等它而推迟悬浮窗出现。
     * 面板已有消息时不回填历史（避免把历史插到本次会话中间），只影响后续写入。
     */
    setPersistKey(key: string | undefined): void;
    /** 构造时回填上次的消息；仅在确有条目时才插入分隔行，避免空面板出现无意义的分隔 */
    private restoreHistory;
    /**
     * 落盘当前消息。只在 addMessage 时写（流式 delta 不写），
     * 因此单轮对话会在 run.finished 后才把完整正文存下来。
     */
    private persistHistory;
    /** 流式追加到最后一条消息（text.delta） */
    private appendDelta;
    private showConfirm;
    private bindClientEvents;
}
