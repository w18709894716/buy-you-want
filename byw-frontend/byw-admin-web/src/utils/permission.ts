import type { App, Directive } from 'vue'
import { useUserStore } from '../stores/user'

/**
 * 权限判断：拥有通配 * 即视为全部权限；否则精确匹配权限标识。
 */
export function hasPerm(code?: string): boolean {
  if (!code) return true
  const store = useUserStore()
  const perms = store.perms || []
  return perms.includes('*') || perms.includes(code)
}

/**
 * v-perm 指令：无权限则移除元素（用于按钮/入口级权限控制）。
 * 用法：v-perm="'sys:user'"
 */
const vPerm: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    if (!hasPerm(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
}

export function setupPermission(app: App) {
  app.directive('perm', vPerm)
}
