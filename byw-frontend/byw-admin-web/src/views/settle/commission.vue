<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>佣金规则</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增规则
          </el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="分类佣金率：结算时按商品所属分类匹配佣金率；分类ID为 0 的规则作为默认兜底率。"
        style="margin-bottom: 16px"
      />

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="categoryId" label="分类ID" width="100">
          <template #default="{ row }">
            {{ row.categoryId === 0 ? '默认(兜底)' : row.categoryId }}
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类名称" min-width="160">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column label="佣金率" width="120">
          <template #default="{ row }">{{ ratePercent(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增佣金规则' : '编辑佣金规则'"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="分类ID" prop="categoryId">
          <el-input-number v-model="form.categoryId" :min="0" :step="1" />
          <span style="margin-left:8px;color:#909399;">0 表示默认兜底率</span>
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" placeholder="如：数码 / 默认" />
        </el-form-item>
        <el-form-item label="佣金率(%)" prop="ratePercent">
          <el-input-number v-model="form.ratePercent" :min="0" :max="100" :precision="2" :step="0.5" />
          <span style="margin-left:8px;color:#909399;">% （5 表示 0.05）</span>
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const tableData = ref<any[]>([])

const ratePercent = (r: any) => (Number(r ?? 0) * 100).toFixed(2) + '%'

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/settle/commission/list')
    tableData.value = data || []
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取佣金规则失败')
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const form = reactive({ categoryId: 0, categoryName: '', ratePercent: 5, enabled: 1 })

const rules = {
  categoryId: [{ required: true, message: '请输入分类ID', trigger: 'blur' }],
  ratePercent: [{ required: true, message: '请输入佣金率', trigger: 'blur' }]
}

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  Object.assign(form, { categoryId: 0, categoryName: '', ratePercent: 5, enabled: 1 })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    categoryId: row.categoryId,
    categoryName: row.categoryName || '',
    ratePercent: Number(row.commissionRate ?? 0) * 100,
    enabled: row.enabled ?? 1
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除该佣金规则（分类ID：${row.categoryId}）？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/settle/commission/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '删除失败')
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const payload: any = {
        categoryId: form.categoryId,
        categoryName: form.categoryName,
        commissionRate: Number((form.ratePercent / 100).toFixed(4)),
        enabled: form.enabled
      }
      if (editingId.value != null) payload.id = editingId.value
      await request.post('/admin/settle/commission/save', payload)
      ElMessage.success(editingId.value != null ? '修改成功' : '新增成功')
      dialogVisible.value = false
      fetchData()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '操作失败')
    }
  })
}

fetchData()
</script>

<style scoped lang="scss">
.page-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
