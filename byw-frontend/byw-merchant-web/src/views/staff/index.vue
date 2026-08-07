<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增员工
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="roleNames" label="角色" min-width="180">
          <template #default="{ row }">
            {{ row.roleNames || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="warning" @click="openRoles(row)">分配角色</el-button>
            <el-button size="small" text type="primary" @click="openSkillGroups(row)">技能组</el-button>
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

    <!-- 新增员工弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增员工" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="初始密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="员工姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple filterable :teleported="false" placeholder="选择角色（可搜索）" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" style="height: auto; min-height: 44px; line-height: 1.5;">
              <div class="role-option">
                <span class="role-option-name">{{ r.roleName }}</span>
                <span v-if="r.remark" class="role-option-remark">{{ r.remark }}</span>
              </div>
            </el-option>
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
      <el-select v-model="roleForm.roleIds" multiple filterable :teleported="false" placeholder="选择角色（可搜索）" style="width: 100%">
        <el-option v-for="r in roleOptions" :key="r.id" :label="r.roleName" :value="r.id" style="height: auto; min-height: 44px; line-height: 1.5;">
          <div class="role-option">
            <span class="role-option-name">{{ r.roleName }}</span>
            <span v-if="r.remark" class="role-option-remark">{{ r.remark }}</span>
          </div>
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配技能组弹窗 -->
    <el-dialog v-model="skillGroupDialogVisible" title="分配技能组" width="420px">
      <el-select v-model="skillGroupForm.groupIds" multiple filterable :teleported="false" placeholder="选择技能组（可搜索）" style="width: 100%">
        <el-option v-for="g in skillGroupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
      </el-select>
      <template #footer>
        <el-button @click="skillGroupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitSkillGroups">确定</el-button>
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

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  username: '',
  password: '',
  realName: '',
  phone: '',
  roleIds: [] as number[]
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}

const roleDialogVisible = ref(false)
const roleForm = reactive<any>({ staffId: undefined, roleIds: [] as number[] })

const skillGroupDialogVisible = ref(false)
const skillGroupOptions = ref<any[]>([])
const skillGroupForm = reactive<any>({ staffId: undefined, groupIds: [] as number[] })

const fetchRoles = async () => {
  try {
    const data: any = await request.get('/merchant/staff/roles')
    roleOptions.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取角色列表失败')
  }
}

const fetchSkillGroups = async () => {
  try {
    const data: any = await request.get('/im/skill-group/list')
    skillGroupOptions.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取技能组列表失败')
  }
}

const openSkillGroups = async (row: any) => {
  skillGroupForm.staffId = row.id
  skillGroupForm.groupIds = []
  try {
    const data: any = await request.get(`/im/skill-group/staff/${row.id}`)
    skillGroupForm.groupIds = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取员工技能组失败')
  }
  skillGroupDialogVisible.value = true
}

const submitSkillGroups = async () => {
  submitting.value = true
  try {
    await request.post('/im/skill-group/staff', {
      staffId: skillGroupForm.staffId,
      groupIds: skillGroupForm.groupIds
    })
    ElMessage.success('分配成功')
    skillGroupDialogVisible.value = false
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '分配失败')
  } finally {
    submitting.value = false
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/merchant/staff/list', {
      params: { pageNum: page.value, pageSize: pageSize.value }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取员工列表失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, { username: '', password: '', realName: '', phone: '', roleIds: [] })
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await request.post('/merchant/staff', form)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '新增失败')
  } finally {
    submitting.value = false
  }
}

const openRoles = (row: any) => {
  roleForm.staffId = row.id
  roleForm.roleIds = row.roleIds || []
  roleDialogVisible.value = true
}

const submitRoles = async () => {
  submitting.value = true
  try {
    await request.post(`/merchant/staff/${roleForm.staffId}/roles`, roleForm.roleIds)
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
    await request.put(`/merchant/staff/${row.id}/password`, null, { params: { password: value } })
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
    await request.put(`/merchant/staff/${row.id}/status`, null, { params: { status: newStatus } })
    ElMessage.success(`${action}成功`)
    row.status = newStatus
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || `${action}失败`)
  }
}

onMounted(() => {
  fetchRoles()
  fetchSkillGroups()
  fetchData()
})
</script>

<style scoped lang="scss">
.page-container {
  .toolbar {
    margin-bottom: 16px;
  }
  .role-option {
    display: flex;
    flex-direction: column;
    line-height: 1.5;
    padding: 2px 0;
    .role-option-name {
      font-weight: 500;
    }
    .role-option-remark {
      color: #909399;
      font-size: 12px;
      line-height: 1.4;
      word-break: break-word;
    }
  }
  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
