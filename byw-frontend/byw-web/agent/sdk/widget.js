const PANEL_W = 400;
const PANEL_H = 500;
const BTN_SIZE = 56;
/** 触发按钮距视口右下角的默认间距 */
const DEFAULT_OFFSET = 24;
/** 触发按钮与展开面板之间的垂直留白 */
const PANEL_GAP = 8;
/** 本地保留的消息条数上限 */
const MAX_PERSISTED_MESSAGES = 50;
/** 回填历史时插入的分隔行样式（持久化时会被排除，避免重复累积） */
const HISTORY_DIVIDER_CLASS = "msg tool history-divider";
export class AgentWidget {
    shadow;
    host;
    panel;
    triggerBtn;
    messagesEl;
    inputEl;
    confirmOverlay;
    visible = false;
    client;
    bottom;
    right;
    persistKey;
    // 拖拽状态
    dragging = false;
    dragOffX = 0;
    dragOffY = 0;
    constructor(client, options = {}) {
        this.client = client;
        this.bottom = options.bottom ?? DEFAULT_OFFSET;
        this.right = options.right ?? DEFAULT_OFFSET;
        this.persistKey = options.persistKey;
        this.host = document.createElement("div");
        this.host.style.cssText = "all:initial;position:fixed;z-index:2147483647;bottom:0;right:0;";
        this.shadow = this.host.attachShadow({ mode: "closed" });
        (options.container ?? document.body).appendChild(this.host);
        this.buildUI();
        this.restoreHistory();
        this.bindClientEvents();
        // 绑定到 client 以便 confirm.request 走 widget 弹窗
        client._widgetBound = { respondConfirm: (id, ok) => this.client.respondConfirm(id, ok) };
    }
    show() {
        this.triggerBtn.style.display = "flex";
    }
    hide() {
        this.triggerBtn.style.display = "none";
        this.panel.style.display = "none";
        this.visible = false;
    }
    destroy() {
        this.client._widgetBound = null;
        // 清理全局拖拽事件监听，避免内存泄漏
        document.removeEventListener("mousemove", this.onDragMove);
        document.removeEventListener("mouseup", this.onDragEnd);
        this.host.remove();
    }
    /* ---- UI 构建 ---- */
    buildUI() {
        const style = document.createElement("style");
        style.textContent = `
      *{box-sizing:border-box;margin:0;padding:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif}
      :host{all:initial}
      .trigger{width:${BTN_SIZE}px;height:${BTN_SIZE}px;border-radius:50%;background:#4f46e5;color:#fff;
        border:none;font-size:24px;cursor:pointer;display:flex;align-items:center;justify-content:center;
        position:fixed;bottom:${this.bottom}px;right:${this.right}px;box-shadow:0 4px 12px rgba(0,0,0,.25);z-index:1}
      .panel{width:${PANEL_W}px;height:${PANEL_H}px;position:fixed;bottom:${this.bottom + BTN_SIZE + PANEL_GAP}px;right:${this.right}px;
        background:#1e1e2e;color:#cdd6f4;border-radius:12px;display:none;flex-direction:column;
        box-shadow:0 8px 32px rgba(0,0,0,.4);overflow:hidden;z-index:2}
      .header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;
        background:#181825;cursor:grab;user-select:none}
      .header h3{font-size:14px;font-weight:600;color:#cdd6f4}
      .close-btn{background:none;border:none;color:#a6adc8;font-size:18px;cursor:pointer}
      .messages{flex:1;overflow-y:auto;padding:12px 16px;font-size:13px;line-height:1.5}
      .msg{margin-bottom:8px;padding:8px 10px;border-radius:8px;background:#313244;word-break:break-word}
      .msg.tool{background:#45475a;font-size:12px;color:#a6e3a1}
      .msg.error{color:#f38ba8}
      .input-row{display:flex;padding:8px;gap:6px;border-top:1px solid #313244}
      .input-row input{flex:1;padding:8px 12px;border:1px solid #45475a;border-radius:8px;
        background:#313244;color:#cdd6f4;font-size:13px;outline:none}
      .input-row button{padding:8px 16px;border:none;border-radius:8px;background:#4f46e5;
        color:#fff;font-size:13px;cursor:pointer}
      .confirm-overlay{position:absolute;inset:0;background:rgba(0,0,0,.6);display:none;
        flex-direction:column;align-items:center;justify-content:center;padding:24px;z-index:10}
      .confirm-box{background:#313244;border-radius:12px;padding:20px;width:90%;text-align:center}
      .confirm-box h4{font-size:14px;margin-bottom:8px;color:#cdd6f4}
      .confirm-box p{font-size:12px;color:#a6adc8;margin-bottom:16px}
      .confirm-btns{display:flex;gap:8px;justify-content:center}
      .confirm-btns button{padding:8px 20px;border:none;border-radius:8px;font-size:13px;cursor:pointer}
      .btn-approve{background:#a6e3a1;color:#1e1e2e}
      .btn-reject{background:#f38ba8;color:#1e1e2e}
    `;
        this.shadow.appendChild(style);
        // 触发按钮
        this.triggerBtn = document.createElement("button");
        this.triggerBtn.className = "trigger";
        this.triggerBtn.textContent = "🤖";
        this.triggerBtn.addEventListener("click", () => this.togglePanel());
        this.shadow.appendChild(this.triggerBtn);
        // 面板
        this.panel = document.createElement("div");
        this.panel.className = "panel";
        // 标题栏（可拖拽）
        const header = document.createElement("div");
        header.className = "header";
        const title = document.createElement("h3");
        title.textContent = "Agent Assistant";
        const closeBtn = document.createElement("button");
        closeBtn.className = "close-btn";
        closeBtn.textContent = "✕";
        closeBtn.addEventListener("click", () => this.togglePanel());
        header.appendChild(title);
        header.appendChild(closeBtn);
        header.addEventListener("mousedown", (e) => this.startDrag(e));
        this.panel.appendChild(header);
        // 消息区
        this.messagesEl = document.createElement("div");
        this.messagesEl.className = "messages";
        this.panel.appendChild(this.messagesEl);
        // 输入行
        const inputRow = document.createElement("div");
        inputRow.className = "input-row";
        this.inputEl = document.createElement("input");
        this.inputEl.placeholder = "输入指令…";
        const sendBtn = document.createElement("button");
        sendBtn.textContent = "发送";
        sendBtn.addEventListener("click", () => this.handleSend());
        this.inputEl.addEventListener("keydown", (e) => {
            if (e.key === "Enter")
                this.handleSend();
        });
        inputRow.appendChild(this.inputEl);
        inputRow.appendChild(sendBtn);
        this.panel.appendChild(inputRow);
        // 确认弹窗覆盖层
        this.confirmOverlay = document.createElement("div");
        this.confirmOverlay.className = "confirm-overlay";
        this.panel.appendChild(this.confirmOverlay);
        this.shadow.appendChild(this.panel);
        // 全局拖拽事件
        document.addEventListener("mousemove", this.onDragMove);
        document.addEventListener("mouseup", this.onDragEnd);
    }
    /* ---- 拖拽 ---- */
    startDrag(e) {
        this.dragging = true;
        const rect = this.panel.getBoundingClientRect();
        this.dragOffX = e.clientX - rect.left;
        this.dragOffY = e.clientY - rect.top;
    }
    onDragMove = (e) => {
        if (!this.dragging)
            return;
        this.panel.style.left = `${e.clientX - this.dragOffX}px`;
        this.panel.style.top = `${e.clientY - this.dragOffY}px`;
        this.panel.style.right = "auto";
        this.panel.style.bottom = "auto";
    };
    onDragEnd = () => {
        this.dragging = false;
    };
    /* ---- 面板切换 ---- */
    togglePanel() {
        this.visible = !this.visible;
        this.panel.style.display = this.visible ? "flex" : "none";
    }
    /* ---- 发送指令 ---- */
    handleSend() {
        const text = this.inputEl.value.trim();
        if (!text)
            return;
        this.inputEl.value = "";
        this.addMessage(`> ${text}`, "msg");
        this.client.ask(text).catch((err) => {
            this.addMessage(`Error: ${err.message}`, "msg error");
        });
    }
    /* ---- 消息渲染 ---- */
    addMessage(text, cls = "msg") {
        this.appendMessage(text, cls);
        this.persistHistory();
    }
    appendMessage(text, cls) {
        const div = document.createElement("div");
        div.className = cls;
        div.textContent = text;
        this.messagesEl.appendChild(div);
        this.messagesEl.scrollTop = this.messagesEl.scrollHeight;
    }
    /* ---- 历史持久化 ---- */
    historyStorageKey() {
        return this.persistKey === undefined ? undefined : `aam_widget_history:${this.persistKey}`;
    }
    /**
     * 运行期补设历史存储键。接入方的用户标识常常异步才拿到（如登录态恢复要先请求 /user/me），
     * 此时可以先用 undefined 构造、拿到标识后再补设，避免为了等它而推迟悬浮窗出现。
     * 面板已有消息时不回填历史（避免把历史插到本次会话中间），只影响后续写入。
     */
    setPersistKey(key) {
        if (this.persistKey === key)
            return;
        this.persistKey = key;
        if (this.messagesEl.children.length === 0)
            this.restoreHistory();
    }
    /** 构造时回填上次的消息；仅在确有条目时才插入分隔行，避免空面板出现无意义的分隔 */
    restoreHistory() {
        const key = this.historyStorageKey();
        if (key === undefined)
            return;
        let items = [];
        try {
            const raw = localStorage.getItem(key);
            if (raw) {
                const parsed = JSON.parse(raw);
                if (Array.isArray(parsed))
                    items = parsed;
            }
        }
        catch {
            return; // 存储被禁用 / 数据损坏，当作没有历史
        }
        if (items.length === 0)
            return;
        for (const item of items) {
            if (typeof item?.text === "string")
                this.appendMessage(item.text, item.cls || "msg");
        }
        // 分隔行跟在历史之后：它标的是"本次会话从这里开始"，新消息会追加在它下面
        this.appendMessage("— 以上为历史消息 —", HISTORY_DIVIDER_CLASS);
    }
    /**
     * 落盘当前消息。只在 addMessage 时写（流式 delta 不写），
     * 因此单轮对话会在 run.finished 后才把完整正文存下来。
     */
    persistHistory() {
        const key = this.historyStorageKey();
        if (key === undefined)
            return;
        const items = Array.from(this.messagesEl.children)
            .filter((el) => !el.classList.contains("history-divider"))
            .slice(-MAX_PERSISTED_MESSAGES)
            .map((el) => ({ text: el.textContent ?? "", cls: el.className }));
        try {
            localStorage.setItem(key, JSON.stringify(items));
        }
        catch {
            // 配额超限或隐私模式禁止写入：不影响本次会话
        }
    }
    /** 流式追加到最后一条消息（text.delta） */
    appendDelta(delta) {
        const last = this.messagesEl.lastElementChild;
        if (last && last.classList.contains("msg") && !last.classList.contains("tool")) {
            last.textContent += delta;
        }
        else {
            const div = document.createElement("div");
            div.className = "msg";
            div.textContent = delta;
            this.messagesEl.appendChild(div);
        }
        this.messagesEl.scrollTop = this.messagesEl.scrollHeight;
    }
    /* ---- 确认弹窗 ---- */
    showConfirm(payload) {
        this.confirmOverlay.innerHTML = "";
        const box = document.createElement("div");
        box.className = "confirm-box";
        const h4 = document.createElement("h4");
        h4.textContent = `确认: ${payload.toolName}`;
        const p = document.createElement("p");
        p.textContent = payload.description;
        const btns = document.createElement("div");
        btns.className = "confirm-btns";
        const approveBtn = document.createElement("button");
        approveBtn.className = "btn-approve";
        approveBtn.textContent = "批准";
        approveBtn.addEventListener("click", () => {
            this.client.respondConfirm(payload.invocationId, true);
            this.confirmOverlay.style.display = "none";
        });
        const rejectBtn = document.createElement("button");
        rejectBtn.className = "btn-reject";
        rejectBtn.textContent = "拒绝";
        rejectBtn.addEventListener("click", () => {
            this.client.respondConfirm(payload.invocationId, false);
            this.confirmOverlay.style.display = "none";
        });
        btns.appendChild(approveBtn);
        btns.appendChild(rejectBtn);
        box.appendChild(h4);
        box.appendChild(p);
        box.appendChild(btns);
        this.confirmOverlay.appendChild(box);
        this.confirmOverlay.style.display = "flex";
    }
    /* ---- 绑定 Client 事件 ---- */
    bindClientEvents() {
        this.client.on("text.delta", (data) => {
            this.appendDelta(data.delta);
        });
        this.client.on("run.started", () => {
            this.addMessage("— 任务开始 —", "msg tool");
        });
        this.client.on("run.finished", (data) => {
            const p = data;
            // success 的 summary 就是已经流式渲染过的正文，重复回显没有意义；
            // 其余终态（cancelled / max_steps）的 summary 是唯一的中止原因，保留
            const detail = p.status === "success" || !p.summary ? "" : `: ${p.summary}`;
            this.addMessage(`— 完成 (${p.status})${detail} —`, "msg tool");
        });
        this.client.on("run.error", (data) => {
            const p = data;
            this.addMessage(`错误: ${p.message}`, "msg error");
        });
        this.client.on("confirmRequest", (data) => {
            this.showConfirm(data);
        });
        this.client.on("action.result", (data) => {
            const r = data;
            this.addMessage(`工具调用 ${r.ok ? "✓" : "✗"} ${r.invocationId}${r.error ? " " + r.error : ""}`, "msg tool");
        });
    }
}
