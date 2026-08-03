<template>
  <template v-for="node in menus" :key="node.id">
    <!-- 目录（含子菜单） -->
    <el-sub-menu v-if="node.children && node.children.length" :index="String(node.id)">
      <template #title>
        <el-icon v-if="node.icon"><component :is="iconOf(node.icon)" /></el-icon>
        <span>{{ node.menuName }}</span>
      </template>
      <MenuTree :menus="node.children" />
    </el-sub-menu>
    <!-- 叶子菜单（menuType=2 且有路径） -->
    <el-menu-item v-else-if="node.path" :index="node.path">
      <el-icon v-if="node.icon"><component :is="iconOf(node.icon)" /></el-icon>
      <template #title>{{ node.menuName }}</template>
    </el-menu-item>
  </template>
</template>

<script setup lang="ts">
import type { MenuNode } from '../stores/user'
import * as ElIcons from '@element-plus/icons-vue'

defineProps<{ menus: MenuNode[] }>()

// icon 名直接映射为图标组件对象（而非字符串）：避免 el-sub-menu 展开重渲染时
// <component :is="字符串"> 在 patch 阶段按名解析全局组件失败而导致图标消失；缺省用 Menu
const iconOf = (icon?: string) => (icon && (ElIcons as Record<string, any>)[icon]) || ElIcons.Menu
</script>
