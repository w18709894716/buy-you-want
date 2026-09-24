/**
 * 工具参数取值助手。
 *
 * Agent 传来的 params 运行时是 unknown，这里做最小必要的类型收敛：
 * 必填项缺失时抛错（错误信息会作为 action.result 回传给 Agent，便于它自我纠正）。
 */

export function str(params: Record<string, unknown>, key: string): string {
  const value = params[key]
  if (value === undefined || value === null || value === '') {
    throw new Error(`缺少必填参数：${key}`)
  }
  return String(value)
}

export function num(params: Record<string, unknown>, key: string): number {
  const raw = params[key]
  if (raw === undefined || raw === null || raw === '') {
    throw new Error(`缺少必填参数：${key}`)
  }
  const value = Number(raw)
  if (!Number.isFinite(value)) {
    throw new Error(`参数 ${key} 必须是数字`)
  }
  return value
}

export function numOr(params: Record<string, unknown>, key: string, fallback: number): number {
  const raw = params[key]
  if (raw === undefined || raw === null || raw === '') return fallback
  const value = Number(raw)
  return Number.isFinite(value) ? value : fallback
}

export function strOr(params: Record<string, unknown>, key: string, fallback: string): string {
  const raw = params[key]
  return raw === undefined || raw === null || raw === '' ? fallback : String(raw)
}

export function optionalStr(params: Record<string, unknown>, key: string): string | undefined {
  const raw = params[key]
  return raw === undefined || raw === null || raw === '' ? undefined : String(raw)
}

export function optionalNum(params: Record<string, unknown>, key: string): number | undefined {
  const raw = params[key]
  if (raw === undefined || raw === null || raw === '') return undefined
  const value = Number(raw)
  return Number.isFinite(value) ? value : undefined
}

export function numArray(params: Record<string, unknown>, key: string): number[] {
  const raw = params[key]
  if (!Array.isArray(raw) || raw.length === 0) {
    throw new Error(`缺少必填参数：${key}（应为非空数字数组）`)
  }
  return raw.map((item) => {
    const value = Number(item)
    if (!Number.isFinite(value)) throw new Error(`参数 ${key} 中含有非数字项：${String(item)}`)
    return value
  })
}
