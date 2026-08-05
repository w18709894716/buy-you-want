<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openEdit()">
          <el-icon><Plus /></el-icon>新增角色
        </el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" width="160" />
        <el-table-column prop="isPreset" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isPreset === 1 ? 'info' : 'primary'">
              {{ row.isPreset === 1 ? '预设' : '自定义' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="200" />
        <el-table-column prop="userCount" label="成员数" width="80" align="center">
          <template #default="{ row }">{{ row.userCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openAuth(row)">菜单授权</el-button>
            <el-button size="small" text type="primary" @click="openCopy(row)">复制</el-button>
            <el-button size="small" text type="primary" @click="openMembers(row)">成员</el-button>
            <el-button v-if="row.isPreset !== 1" size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.isPreset !== 1" size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑角色 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="460px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="editForm.roleName" placeholder="请输入角色名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="说明" prop="remark">
          <el-input v-model="editForm.remark" type="textarea" :rows="3" placeholder="请输入角色说明" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 复制角色 -->
    <el-dialog v-model="copyVisible" :title="`复制角色 - ${currentCopyRole?.roleName || ''}`" width="460px">
      <el-form ref="copyFormRef" :model="copyForm" :rules="copyRules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="copyForm.roleName" placeholder="请输入新角色名称（不能与现有角色同名）" maxlength="20" />
        </el-form-item>
        <el-form-item label="说明" prop="remark">
          <el-input v-model="copyForm.remark" type="textarea" :rows="3" placeholder="请输入角色说明" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyVisible = false">取消</el-button>
        <el-button type="primary" :loading="copySubmitting" @click="submitCopy">确定</el-button>
      </template>
    </el-dialog>

    <!-- 角色成员 -->
    <el-dialog v-model="memberVisible" :title="`角色成员 - ${currentCopyRole?.roleName || ''}`" width="620px">
      <el-table :data="memberList" v-loading="memberLoading" stripe border size="small">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="realName" label="姓名" min-width="120">
          <template #default="{ row }">{{ row.realName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button v-if="currentRole?.isPreset !== 1" size="small" text type="danger" @click="unbindMember(row)">解绑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无成员" :image-size="80" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="memberVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 菜单授权抽屉 -->
    <el-drawer v-model="drawerVisible" :title="`菜单授权 - ${currentRole?.roleName || ''}`" size="380px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        :props="treeProps"
        v-loading="treeLoading"
      />
      <template #footer>
        <span v-if="readonly" class="readonly-tip">预设角色不可修改授权</span>
        <el-button @click="drawerVisible = false">{{ readonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readonly" type="primary" :loading="submitting" @click="submitAuth">保存授权</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox, ElTree, type FormInstance, type FormRules } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const treeLoading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])
const menuTree = ref<any[]>([])

const drawerVisible = ref(false)
const currentRole = ref<any>(null)
const readonly = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()

// 只读模式（预设角色）下禁用所有节点的勾选框；公共菜单（控制台）始终禁用
const treeProps = computed(() => ({
  label: 'menuName',
  children: 'children',
  disabled: (data: any) => readonly.value || data.disabled === true
}))

// 公共菜单（menu_type=2 且 perm_code 为空，如控制台）：默认勾选且不可取消（可见性不依赖角色绑定）
// 注意：只处理叶子菜单，目录（menu_type=1）不在此列，避免勾选目录级联选中其全部子菜单
const alwaysCheckedIds: number[] = []
const collectAlwaysChecked = (nodes: any[]) => {
  for (const n of nodes) {
    if (n.menuType === 2 && !n.permCode) {
      n.disabled = true
      alwaysCheckedIds.push(n.id)
    }
    if (n.children && n.children.length > 0) collectAlwaysChecked(n.children)
  }
}

// 收集所有“含子节点”的父目录 ID：回填时排除，只勾叶子节点，交由 el-tree 自行推算父目录半选/全选
const collectParentIds = (nodes: any[], set: Set<number>) => {
  for (const n of nodes) {
    if (n.children && n.children.length > 0) {
      set.add(n.id)
      collectParentIds(n.children, set)
    }
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/merchant/role/list')
    tableData.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取角色列表失败')
  } finally {
    loading.value = false
  }
}

const fetchMenuTree = async () => {
  const data: any = await request.get('/merchant/role/menu-tree')
  menuTree.value = data || []
  collectAlwaysChecked(menuTree.value)
}

// ===== 新增/编辑角色 =====
const editVisible = ref(false)
const editSubmitting = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = ref<any>({ id: null, roleName: '', remark: '' })
const editRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const openEdit = (row?: any) => {
  if (row) {
    editForm.value = { id: row.id, roleName: row.roleName, remark: row.remark }
  } else {
    editForm.value = { id: null, roleName: '', remark: '' }
  }
  editVisible.value = true
  nextTick(() => editFormRef.value?.clearValidate())
}

const submitEdit = async () => {
  await editFormRef.value?.validate()
  editSubmitting.value = true
  try {
    if (editForm.value.id) {
      await request.put('/merchant/role', editForm.value)
      ElMessage.success('保存成功')
    } else {
      await request.post('/merchant/role', editForm.value)
      ElMessage.success('新增成功')
    }
    editVisible.value = false
    await fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '保存失败')
  } finally {
    editSubmitting.value = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认删除角色“${row.roleName}”？`, '提示', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/merchant/role/${row.id}`)
    ElMessage.success('删除成功')
    await fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '删除失败')
  }
}

// ===== 复制角色（预设模板/自定义角色均可作为蓝本） =====
const copyVisible = ref(false)
const copySubmitting = ref(false)
const copyFormRef = ref<FormInstance>()
const currentCopyRole = ref<any>(null)
const copyForm = ref<any>({ roleName: '', remark: '' })
const copyRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const openCopy = (row: any) => {
  currentCopyRole.value = row
  copyForm.value = { roleName: '', remark: '' }
  copyVisible.value = true
  nextTick(() => copyFormRef.value?.clearValidate())
}

const submitCopy = async () => {
  await copyFormRef.value?.validate()
  copySubmitting.value = true
  try {
    await request.post(`/merchant/role/${currentCopyRole.value.id}/copy`, copyForm.value)
    ElMessage.success('复制成功，可在下方菜单授权中调整权限')
    copyVisible.value = false
    await fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '复制失败')
  } finally {
    copySubmitting.value = false
  }
}

// ===== 角色成员查看/解绑 =====
const memberVisible = ref(false)
const memberLoading = ref(false)
const memberList = ref<any[]>([])

const openMembers = async (row: any) => {
  currentRole.value = row
  currentCopyRole.value = row
  memberVisible.value = true
  memberLoading.value = true
  try {
    const data: any = await request.get(`/merchant/role/${row.id}/members`)
    memberList.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取成员列表失败')
  } finally {
    memberLoading.value = false
  }
}

const unbindMember = async (member: any) => {
  try {
    await ElMessageBox.confirm(`确认将员工“${member.username}”从角色“${currentRole.value.roleName}”中解绑？解绑后该员工将立即失去此角色的权限。`, '解绑确认', {
      type: 'warning', confirmButtonText: '解绑', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/merchant/role/${currentRole.value.id}/members/${member.id}`)
    ElMessage.success('解绑成功，该员工权限已即时更新')
    memberList.value = memberList.value.filter((m: any) => m.id !== member.id)
    await fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '解绑失败')
  }
}

// ===== 菜单授权 =====
const openAuth = async (row: any) => {
  currentRole.value = row
  readonly.value = row.isPreset === 1
  drawerVisible.value = true
  treeLoading.value = true
  // 先命令式清空上一个角色残留的勾选（抽屉复用同一个 el-tree 实例）
  await nextTick()
  treeRef.value?.setCheckedKeys([])
  try {
    if (menuTree.value.length === 0) await fetchMenuTree()
    const ids: any = await request.get(`/merchant/role/${row.id}/menu-ids`)
    // 只回填叶子节点，排除半选父目录 ID，避免父目录被直接勾选而强制选中其全部子菜单
    const parentIds = new Set<number>()
    collectParentIds(menuTree.value, parentIds)
    const leafKeys = (ids || []).filter((id: number) => !parentIds.has(id))
    // 公共菜单（控制台）强制勾选且不可取消
    for (const id of alwaysCheckedIds) {
      if (!leafKeys.includes(id)) leafKeys.push(id)
    }
    await nextTick()
    treeRef.value?.setCheckedKeys(leafKeys)
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取授权信息失败')
  } finally {
    treeLoading.value = false
  }
}

const submitAuth = async () => {
  submitting.value = true
  try {
    // 半选（父节点）也需下发，保证目录随子菜单一并绑定
    const checked = treeRef.value?.getCheckedKeys() || []
    const halfChecked = treeRef.value?.getHalfCheckedKeys() || []
    const menuIds = [...checked, ...halfChecked]
    await request.post(`/merchant/role/${currentRole.value.id}/menus`, menuIds)
    ElMessage.success('授权成功')
    drawerVisible.value = false
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '授权失败')
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-container {
  .table-card {
    margin-bottom: 16px;
  }
  .toolbar {
    margin-bottom: 16px;
  }
}
.readonly-tip {
  margin-right: auto;
  color: #909399;
  font-size: 13px;
}
</style>
