/**
 * DOM 分析器：扫描页面可交互元素，生成快照供 Agent 使用。
 */
/** CSS.escape polyfill fallback（jsdom 不实现 CSS.escape） */
function cssEscape(str) {
    if (typeof CSS !== "undefined" && typeof CSS.escape === "function") {
        return CSS.escape(str);
    }
    return str.replace(/([^\w-])/g, "\\$1");
}
/** 用于 querySelectorAll 的可交互元素选择器 */
const INTERACTIVE_SELECTOR = [
    "button",
    "a[href]",
    "input",
    "select",
    "textarea",
    "[role=button]",
    "[onclick]",
    "[tabindex]",
].join(",");
/** 最大扫描元素数量 */
const MAX_ELEMENTS = 150;
/**
 * 为元素生成稳定唯一的 CSS 选择器。
 * 优先级：id > name > data-testid > nth-child 路径
 */
export function generateSelector(el) {
    const tag = el.tagName.toLowerCase();
    // 1. id（最高优先级）
    if (el.id) {
        return `#${cssEscape(el.id)}`;
    }
    // 2. name 属性
    const name = el.getAttribute("name");
    if (name) {
        const sel = `${tag}[name="${cssEscape(name)}"]`;
        if (el.ownerDocument.querySelectorAll(sel).length === 1) {
            return sel;
        }
    }
    // 3. data-testid
    const testid = el.getAttribute("data-testid");
    if (testid) {
        return `${tag}[data-testid="${cssEscape(testid)}"]`;
    }
    // 4. nth-child 路径
    const parts = [];
    let current = el;
    while (current && current !== current.ownerDocument.documentElement) {
        const curTag = current.tagName.toLowerCase();
        const parent = current.parentElement;
        if (parent) {
            const siblings = Array.from(parent.children).filter((c) => c.tagName.toLowerCase() === curTag);
            if (siblings.length > 1) {
                const idx = siblings.indexOf(current) + 1;
                parts.unshift(`${curTag}:nth-of-type(${idx})`);
            }
            else {
                parts.unshift(curTag);
            }
        }
        else {
            parts.unshift(curTag);
        }
        current = parent;
    }
    return parts.join(" > ");
}
/** 提取元素可见文本（截断至 100 字符） */
function getVisibleText(el) {
    const text = (el.textContent ?? "").trim().replace(/\s+/g, " ");
    return text.length > 100 ? text.slice(0, 100) : text;
}
/** 检测元素是否处于禁用状态 */
function isElementDisabled(el) {
    if (el.hasAttribute("disabled"))
        return true;
    if (el.getAttribute("aria-disabled") === "true")
        return true;
    return false;
}
/**
 * 扫描当前页面（或指定根节点）的可交互元素，返回快照数组。
 * 最多返回 150 个元素。
 */
export function snapshotPage(root = document) {
    const elements = Array.from(root.querySelectorAll(INTERACTIVE_SELECTOR)).slice(0, MAX_ELEMENTS);
    return elements.map((el, i) => {
        const tag = el.tagName.toLowerCase();
        const type = el.getAttribute("type") ?? undefined;
        const text = getVisibleText(el);
        const selector = generateSelector(el);
        const disabled = isElementDisabled(el);
        const pageEl = { ref: i, tag, text, selector };
        if (type !== null && type !== undefined)
            pageEl.type = type;
        if (disabled)
            pageEl.disabled = true;
        return pageEl;
    });
}
