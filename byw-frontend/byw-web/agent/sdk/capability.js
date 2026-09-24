export class CapabilityRegistry {
    tools = new Map();
    /** 注册（同名覆盖）一个工具 */
    register(def) {
        if (!def || typeof def.name !== "string" || def.name.trim() === "") {
            throw new Error("registerTool: name 不能为空");
        }
        if (typeof def.handler !== "function") {
            throw new Error(`registerTool: 工具 "${def.name}" 缺少 handler 函数`);
        }
        if (typeof def.description !== "string") {
            throw new Error(`registerTool: 工具 "${def.name}" 缺少 description`);
        }
        this.tools.set(def.name, { ...def });
    }
    unregister(name) {
        return this.tools.delete(name);
    }
    get(name) {
        return this.tools.get(name);
    }
    has(name) {
        return this.tools.has(name);
    }
    /** 输出能力声明清单（剥离 handler），用于 session.start 的 capabilities */
    list() {
        return Array.from(this.tools.values()).map(({ name, description, parameters, riskLevel, returns }) => {
            const spec = { name, description, parameters, riskLevel };
            if (returns !== undefined)
                spec.returns = returns;
            return spec;
        });
    }
    clear() {
        this.tools.clear();
    }
}
/** 默认全局注册表（AgentClient 未显式传入 registry 时使用） */
export const defaultRegistry = new CapabilityRegistry();
/** 便捷函数：向默认注册表注册工具 */
export function registerTool(def) {
    defaultRegistry.register(def);
}
