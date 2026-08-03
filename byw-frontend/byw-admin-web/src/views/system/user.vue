<template>
  <div class="page-container">
    <!-- 搜索 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="用户名/昵称/手机号" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="openCreate">
            <el-icon><Plus /></el-icon>新增员工
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="roleNames" label="角色" min-width="160" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" min-width="160" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="warning" @click="openRoles(row)">分配角色</el-button>
            <el-button size="small" text @click="resetPassword(row)">重置密码</el-button>
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              text
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑员工' : '新增员工'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="420px">
      <el-select v-model="roleForm.roleIds" multiple placeholder="选择角色" style="width: 100%">
        <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const submitting = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])
const roleOptions = ref<any[]>([])

const searchForm = reactive({ keyword: '' })

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  id: undefined,
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  roleIds: [] as number[]
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const roleDialogVisible = ref(false)
const roleForm = reactive<any>({ userId: undefined, roleIds: [] as number[] })

const fetchRoles = async () => {
  try {
    const data: any = await request.get('/admin/sys/role/list')
    roleOptions.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取角色列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/sys/user/page', {
      params: { pageNum: page.value, pageSize: pageSize.value, keyword: searchForm.keyword }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取员工列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.keyword = ''
  handleSearch()
}

const openCreate = () => {
  isEdit.value = false
  Object.assign(form, { id: undefined, username: '', password: '', nickname: '', phone: '', email: '', roleIds: [] })
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    nickname: row.nickname,
    phone: row.phone,
    email: row.email,
    roleIds: row.roleIds || []
  })
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await request.put('/admin/sys/user', form)
    } else {
      await request.post('/admin/sys/user', form)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const openRoles = (row: any) => {
  roleForm.userId = row.id
  roleForm.roleIds = row.roleIds || []
  roleDialogVisible.value = true
}

const submitRoles = async () => {
  submitting.value = true
  try {
    await request.post(`/admin/sys/user/${roleForm.userId}/roles`, roleForm.roleIds)
    ElMessage.success('分配成功')
    roleDialogVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '分配失败')
  } finally {
    submitting.value = false
  }
}

const resetPassword = async (row: any) => {
  const { value } = await ElMessageBox.prompt('请输入新密码', '重置密码', {
    inputType: 'password',
    inputPlaceholder: '新密码'
  })
  try {
    await request.put(`/admin/sys/user/${row.id}/password`, null, { params: { password: value } })
    ElMessage.success('重置成功')
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '重置失败')
  }
}

const toggleStatus = async (row: any) => {
  const action = row.status === 1 ? '禁用' : '启用'
  const newStatus = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确定要${action}该员工吗？`, '提示', { type: 'warning' })
  try {
    await request.put(`/admin/sys/user/${row.id}/status`, null, { params: { status: newStatus } })
    ElMessage.success(`${action}成功`)
    row.status = newStatus
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || `${action}失败`)
  }
}

onMounted(() => {
  fetchRoles()
  fetchData()
})
</script>

<style scoped lang="scss">
.page-container {
  .search-card {
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
