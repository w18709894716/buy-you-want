/**
 * DOM 分析器：扫描页面可交互元素，生成快照供 Agent 使用。
 */
/** 页面元素快照 */
export interface PageElement {
    ref: number;
    tag: string;
    type?: string;
    text: string;
    selector: string;
    disabled?: boolean;
}
/**
 * 为元素生成稳定唯一的 CSS 选择器。
 * 优先级：id > name > data-testid > nth-child 路径
 */
export declare function generateSelector(el: Element): string;
/**
 * 扫描当前页面（或指定根节点）的可交互元素，返回快照数组。
 * 最多返回 150 个元素。
 */
export declare function snapshotPage(root?: ParentNode): PageElement[];
