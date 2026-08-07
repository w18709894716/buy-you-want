<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>新增FAQ
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="question" label="问题" min-width="200">
          <template #default="{ row }">
            <div class="cell-wrap">{{ row.question }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="answer" label="答案" min-width="300">
          <template #default="{ row }">
            <div class="cell-wrap">{{ row.answer }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :loading="row._statusLoading"
              @change="(val: boolean) => toggleStatus(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑FAQ' : '新增FAQ'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="form.question" type="textarea" :rows="2" placeholder="请输入用户常见问题" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="4" placeholder="请输入自动回复内容" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '../../utils/request'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<any>({
  id: undefined,
  question: '',
  answer: '',
  sort: 0
})

const rules: FormRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/im/faq/list')
    tableData.value = data || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取FAQ列表失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  isEdit.value = false
  form.id = undefined
  form.question = ''
  form.answer = ''
  form.sort = 0
  dialogVisible.value = true
}

const openEdit = (row: any) => {
  isEdit.value = true
  form.id = row.id
  form.question = row.question
  form.answer = row.answer
  form.sort = row.sort ?? 0
  dialogVisible.value = true
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await request.put('/im/faq', form)
      ElMessage.success('更新成功')
    } else {
      await request.post('/im/faq', form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (row: any, val: boolean) => {
  const newStatus = val ? 1 : 0
  row._statusLoading = true
  try {
    await request.put(`/im/faq/${row.id}/status`, null, { params: { status: newStatus } })
    row.status = newStatus
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '操作失败')
  } finally {
    row._statusLoading = false
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定要删除该FAQ吗？', '提示', { type: 'warning' })
  try {
    await request.delete(`/im/faq/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '删除失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.page-container {
  .toolbar {
    margin-bottom: 16px;
  }
  .cell-wrap {
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>