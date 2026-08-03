<template>
  <template v-for="node in menus" :key="node.id">
    <!-- 目录（含子菜单） -->
    <el-sub-menu v-if="node.children && node.children.length" :index="String(node.id)">
      <template #title>
        <el-icon v-if="node.icon"><component :is="iconOf(node.icon)" /></el-icon>
        <span>{{ node.menuName }}</span>
      </template>
      <MenuTree :menus="node.children" :im-unread="imUnread" :is-collapse="isCollapse" />
    </el-sub-menu>
    <!-- 客服工作台：保留未读角标特殊结构 -->
    <el-menu-item v-else-if="node.path === '/im'" :index="node.path">
      <el-badge :hidden="!imUnread || !isCollapse" is-dot class="im-menu-badge">
        <el-icon><component :is="iconOf(node.icon)" /></el-icon>
      </el-badge>
      <template #title>
        <span>{{ node.menuName }}</span>
        <span v-if="imUnread" class="im-unread-pill">{{ imUnread > 99 ? '99+' : imUnread }}</span>
      </template>
    </el-menu-item>
    <!-- 普通叶子菜单 -->
    <el-menu-item v-else-if="node.path" :index="node.path">
      <el-icon v-if="node.icon"><component :is="iconOf(node.icon)" /></el-icon>
      <template #title>{{ node.menuName }}</template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { MenuNode } from '../stores/user'
import * as ElIcons from '@element-plus/icons-vue'

defineProps<{ menus: MenuNode[]; imUnread?: number; isCollapse?: boolean }>()

// icon 名直接映射为图标组件对象（而非字符串）：避免 el-sub-menu 展开重渲染时
// <component :is="字符串"> 在 patch 阶段按名解析全局组件失败而导致图标消失；缺省用 Menu
const iconOf = (icon?: string) => (icon && (ElIcons as Record<string, any>)[icon]) || ElIcons.Menu
</script>

<style scoped lang="scss">
.im-menu-badge {
  line-height: 1;
  :deep(.el-icon) {
    margin-right: 5px;
    vertical-align: middle;
  }
}

.im-unread-pill {
  display: inline-block;
  margin-left: 8px;
  padding: 0 5px;
  min-width: 16px;
  height: 16px;
  line-height: 16px;
  border-radius: 8px;
  background-color: var(--el-color-danger);
  color: #fff;
  font-size: 11px;
  text-align: center;
  vertical-align: middle;
}
</style>
