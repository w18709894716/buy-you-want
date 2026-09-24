/** 统一超时 25 秒 */
const ACTION_TIMEOUT = 25_000;
/** 内置 DOM 工具的能力声明 */
export const BUILTIN_DOM_TOOLS = [
    {
        name: "dom_click",
        description: "Click a DOM element identified by a CSS selector",
        parameters: {
            type: "object",
            properties: { selector: { type: "string", description: "CSS selector of the target element" } },
            required: ["selector"],
        },
        riskLevel: "write",
    },
    {
        name: "dom_fill",
        description: "Fill an input, textarea, or select element with a value",
        parameters: {
            type: "object",
            properties: {
                selector: { type: "string", description: "CSS selector of the target element" },
                value: { type: "string", description: "Value to fill" },
            },
            required: ["selector", "value"],
        },
        riskLevel: "write",
    },
    {
        name: "dom_read",
        description: "Read the text content or value of a DOM element",
        parameters: {
            type: "object",
            properties: { selector: { type: "string", description: "CSS selector of the target element" } },
            required: ["selector"],
        },
        riskLevel: "read",
        returns: { type: "string", description: "Text content or value of the element" },
    },
    {
        name: "dom_scroll",
        description: "Scroll the page or a specific element into view",
        parameters: {
            type: "object",
            properties: {
                selector: { type: "string", description: "CSS selector of the target element (optional)" },
                x: { type: "number", description: "Horizontal scroll offset in pixels" },
                y: { type: "number", description: "Vertical scroll offset in pixels" },
            },
        },
        riskLevel: "write",
    },
];
/* ------------------------------------------------------------------ */
/* 内置 DOM 工具实现                                                   */
/* ------------------------------------------------------------------ */
function domClick(params) {
    const el = document.querySelector(params.selector);
    if (!el)
        throw new Error(`Element not found: ${params.selector}`);
    el.click();
}
function domFill(params) {
    const el = document.querySelector(params.selector);
    if (!el)
        throw new Error(`Element not found: ${params.selector}`);
    const value = String(params.value ?? "");
    el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
}
function domRead(params) {
    const el = document.querySelector(params.selector);
    if (!el)
        throw new Error(`Element not found: ${params.selector}`);
    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
        return el.value;
    }
    return el.textContent ?? "";
}
function domScroll(params) {
    const { selector, x, y } = params;
    if (selector) {
        const el = document.querySelector(selector);
        if (!el)
            throw new Error(`Element not found: ${selector}`);
        el.scrollIntoView({ behavior: "smooth" });
    }
    else {
        window.scrollBy(x ?? 0, y ?? 0);
    }
}
const BUILTIN_HANDLERS = {
    dom_click: domClick,
    dom_fill: domFill,
    dom_read: domRead,
    dom_scroll: domScroll,
};
/* ------------------------------------------------------------------ */
/* 核心执行函数                                                       */
/* ------------------------------------------------------------------ */
/**
 * 执行一个 action：优先从 registry 查找已注册 handler，否则尝试内置 DOM 工具。
 * 统一 25 秒超时，返回 ActionResult。
 */
export async function executeAction(toolName, params, registry) {
    const timeout = new Promise((_, reject) => setTimeout(() => reject(new Error(`Action timeout: ${toolName}`)), ACTION_TIMEOUT));
    const exec = async () => {
        const def = registry.get(toolName);
        if (def)
            return def.handler(params);
        const builtin = BUILTIN_HANDLERS[toolName];
        if (builtin)
            return builtin(params);
        throw new Error(`Unknown tool: ${toolName}`);
    };
    try {
        const result = await Promise.race([exec(), timeout]);
        return { ok: true, result };
    }
    catch (err) {
        return { ok: false, error: err.message };
    }
}
