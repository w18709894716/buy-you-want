<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-radio-group v-model="scopeType" @change="loadTree">
          <el-radio-button value="platform">平台端</el-radio-button>
          <el-radio-button value="merchant">商家端</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="openEdit()">新增菜单</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border row-key="id" default-expand-all
                :tree-props="{ children: 'children' }">
        <el-table-column prop="menuName" label="菜单名称" min-width="220">
          <template #default="{ row }">
            {{ row.menuName }}
            <el-tag v-if="row.menuType === 3" size="small" type="warning" class="type-tag">按钮</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="menuType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.menuType === 1 ? 'info' : row.menuType === 2 ? 'primary' : 'warning'" size="small">
              {{ row.menuType === 1 ? '目录' : row.menuType === 2 ? '菜单' : '按钮' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="150">
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="permCode" label="权限标识" min-width="150">
          <template #default="{ row }">{{ row.permCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="icon" label="图标" width="80">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column prop="visible" label="可见" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.visible === 1" :disabled="row.menuType === 3" @change="(v: any) => updateMenu({ ...row, visible: v ? 1 : 0 })" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="(v: any) => updateMenu({ ...row, status: v ? 1 : 0 })" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.menuType !== 3" size="small" text type="primary" @click="openEdit(null, row)">新增子项</el-button>
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑菜单 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑菜单' : '新增菜单'" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="父级菜单">
          <el-input v-if="editForm.id || addSubMode" :model-value="parentName" disabled />
          <el-tree-select v-else v-model="editForm.parentId" :data="parentOptions" check-strictly clearable
                          :props="{ label: 'menuName', children: 'children' }" placeholder="不选则为顶层菜单"
                          node-key="id" class="full-width" />
        </el-form-item>
        <el-form-item label="类型" prop="menuType">
          <el-radio-group v-model="editForm.menuType" :disabled="!!editForm.id || addSubMode">
            <el-radio :value="1">目录</el-radio>
            <el-radio :value="2">菜单</el-radio>
            <el-radio :value="3" :disabled="!canAddButton">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" prop="menuName">
          <el-input v-model="editForm.menuName" placeholder="请输入菜单名称" maxlength="20" />
        </el-form-item>
        <el-form-item v-if="editForm.menuType === 2" label="路由路径" prop="path">
          <el-input v-model="editForm.path" placeholder="如 /product/list" maxlength="80" />
        </el-form-item>
        <el-form-item v-if="editForm.menuType !== 1" label="权限标识" prop="permCode">
          <el-input v-model="editForm.permCode" placeholder="菜单可空（空=公共菜单）；按钮必填" maxlength="50" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="editForm.icon" placeholder="Element Plus 图标名，如 Goods" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="editForm.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref<any[]>([])
const scopeType = ref<'platform' | 'merchant'>('platform')

const loadTree = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/sys/menu/tree', { params: { scope: scopeType.value } })
    tableData.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取菜单树失败')
  } finally {
    loading.value = false
  }
}

/** 平台端菜单变更后同步刷新左侧导航快照（Pinia + localStorage），商家端由商家重新登录生效 */
const refreshSidebar = async () => {
  if (scopeType.value !== 'platform') return
  try {
    await userStore.fetchMenus()
  } catch {
    // 拉取失败不阻断，后续刷新页面兜底
  }
}

/** 保存成功的提示文案：商家端改动对商家侧用户延迟生效 */
const saveTip = () => (scopeType.value === 'merchant' ? '保存成功，商家端用户重新登录后生效' : '保存成功')

// ===== 新增/编辑 =====
const editVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = ref<any>({ id: null, parentId: null, menuName: '', menuType: 1, path: '', permCode: '', icon: '', sortOrder: 10 })
const editRules: FormRules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }],
  permCode: [{ required: true, message: '请输入权限标识', trigger: 'blur' }]
}

/** 父级下拉数据：编辑态固定；新增子项时仅展示可选父级（过滤自身，避免挂到自己名下） */
const parentOptions = computed(() => filterOptions(tableData.value))
const parentName = computed(() => findParentName(tableData.value, editForm.value.parentId))
const canAddButton = computed(() => {
  const parentId = editForm.value.parentId
  if (!parentId) return false
  const parent = findNode(tableData.value, parentId)
  return parent?.menuType === 2
})

/** 过滤出可作为父级的节点（目录可挂目录/菜单；菜单可挂按钮） */
const filterOptions = (nodes: any[]): any[] => {
  const result: any[] = []
  for (const n of nodes) {
    const item = { ...n }
    if (n.children && n.children.length > 0) {
      const subs = filterOptions(n.children)
      if (subs.length > 0) item.children = subs
    } else {
      delete item.children
    }
    result.push(item)
  }
  return result
}

const findNode = (nodes: any[], id: number): any => {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children) {
      const hit = findNode(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

const findParentName = (nodes: any[], id: number): string => {
  const node = findNode(nodes, id)
  return node ? node.menuName : '-'
}

/** 是否顶层节点（种子里顶层 parent_id 存 0，后端新建存 null，两者视为同级） */
const isTopLevel = (pid: any) => pid === null || pid === undefined || pid === 0

/** 收集同父级的节点（含顶层），用于推算默认排序 */
const collectSiblings = (nodes: any[], parentId: number | null, arr: any[]): any[] => {
  const top = isTopLevel(parentId)
  for (const n of nodes) {
    if (top ? isTopLevel(n.parentId) : n.parentId === parentId) arr.push(n)
    if (n.children && n.children.length > 0) collectSiblings(n.children, parentId, arr)
  }
  return arr
}

/** 同父级最大排序 +10，避免新菜单与现有菜单排序重复 */
const nextSort = (parentId: number | null) => {
  const siblings = collectSiblings(tableData.value, parentId, [])
  const max = siblings.reduce((m: number, s: any) => Math.max(m, s.sortOrder || 0), 0)
  return max + (isTopLevel(parentId) ? 10 : 1)
}

/** 是否“新增子项”模式（父级与类型锁定，仅编辑/顶层新增可自由选择） */
const addSubMode = ref(false)

const openEdit = (row?: any, parent?: any) => {
  // 新增子项模式：父级与类型由当前行推导并锁定
  addSubMode.value = !row && !!parent
  if (row) {
    editForm.value = { ...row, permCode: row.permCode || '', path: row.path || '' }
  } else if (parent) {
    // 目录的子项默认菜单，菜单的子项为按钮
    editForm.value = {
      id: null,
      parentId: parent.id,
      menuName: '',
      menuType: parent.menuType === 2 ? 3 : 2,
      path: '',
      permCode: '',
      icon: '',
      sortOrder: nextSort(parent.id)
    }
  } else {
    // 顶层新增：父级/类型自由选择
    editForm.value = {
      id: null,
      parentId: null,
      menuName: '',
      menuType: 1,
      path: '',
      permCode: '',
      icon: '',
      sortOrder: nextSort(null)
    }
  }
  editVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

const submitEdit = async () => {
  await editFormRef.value?.validate()
  editSubmitting.value = true
  try {
    const payload = { ...editForm.value, scope: scopeType.value }
    if (payload.menuType === 1) {
      payload.path = null
      payload.permCode = null
    } else if (payload.menuType === 3) {
      payload.path = null
    }
    if (editForm.value.id) {
      await request.put('/admin/sys/menu', payload)
      ElMessage.success(saveTip())
    } else {
      await request.post('/admin/sys/menu', payload)
      ElMessage.success(saveTip())
    }
    editVisible.value = false
    await loadTree()
    await refreshSidebar()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '保存失败')
  } finally {
    editSubmitting.value = false
  }
}

const updateMenu = async (row: any) => {
  try {
    await request.put('/admin/sys/menu', {
      id: row.id,
      scope: row.scope,
      menuName: row.menuName,
      menuType: row.menuType,
      path: row.path,
      permCode: row.permCode,
      icon: row.icon,
      sortOrder: row.sortOrder,
      visible: row.visible,
      status: row.status
    })
    ElMessage.success(saveTip())
    await loadTree()
    await refreshSidebar()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '保存失败')
    await loadTree()
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      try {
        await request.delete(`/admin/sys/menu/${row.id}`)
        ElMessage.success('删除成功')
        await loadTree()
        await refreshSidebar()
      } catch (error: any) {
        if (!error._handled) ElMessage.error(error?.message || '删除失败')
      }
    })
    .catch(() => {})
}

loadTree()
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.type-tag {
  margin-left: 6px;
}
.full-width {
  width: 100%;
}
</style>
