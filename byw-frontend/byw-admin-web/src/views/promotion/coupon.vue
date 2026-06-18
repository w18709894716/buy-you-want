<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>优惠券管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>创建优惠券
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="优惠券名称" min-width="160" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'FIXED' ? 'primary' : 'warning'">
              {{ row.type === 'FIXED' ? '满减券' : '折扣券' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优惠" width="110">
          <template #default="{ row }">
            {{ row.type === 'FIXED' ? `¥${row.value}` : `${row.value}折` }}
          </template>
        </el-table-column>
        <el-table-column prop="minAmount" label="最低消费" width="100">
          <template #default="{ row }">¥{{ row.minAmount }}</template>
        </el-table-column>
        <el-table-column prop="totalCount" label="发放数量" width="90" />
        <el-table-column prop="usedCount" label="已使用" width="90" />
        <el-table-column label="有效期" min-width="200">
          <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
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

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '创建优惠券' : '编辑优惠券'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" placeholder="如：新用户满100减20" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="FIXED">满减券</el-radio>
            <el-radio value="PERCENT">折扣券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="优惠值" prop="value">
          <el-input-number
            v-model="form.value"
            :min="form.type === 'FIXED' ? 1 : 0.1"
            :max="form.type === 'FIXED' ? 9999 : 9.9"
            :step="form.type === 'FIXED' ? 1 : 0.1"
            :precision="form.type === 'FIXED' ? 0 : 1"
          />
          <span style="margin-left:8px;color:#909399;">
            {{ form.type === 'FIXED' ? '元' : '折' }}
          </span>
        </el-form-item>
        <el-form-item label="最低消费" prop="minAmount">
          <el-input-number v-model="form.minAmount" :min="0" :step="10" />
          <span style="margin-left:8px;color:#909399;">元</span>
        </el-form-item>
        <el-form-item label="发放数量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" :max="999999" />
        </el-form-item>
        <el-form-item label="有效期" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const tableData = ref<any[]>([])

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/promotion/coupon/list')
    tableData.value = data || []
  } catch {
    tableData.value = [
      { id: 1, name: '新用户专享满减券', type: 'FIXED', value: 20, minAmount: 100, totalCount: 1000, usedCount: 328, startTime: '2025-06-01 00:00:00', endTime: '2025-06-30 23:59:59', status: 1 },
      { id: 2, name: '618大促折扣券', type: 'PERCENT', value: 8.5, minAmount: 200, totalCount: 5000, usedCount: 1256, startTime: '2025-06-15 00:00:00', endTime: '2025-06-20 23:59:59', status: 1 },
      { id: 3, name: '会员专属满减券', type: 'FIXED', value: 50, minAmount: 300, totalCount: 500, usedCount: 89, startTime: '2025-05-01 00:00:00', endTime: '2025-12-31 23:59:59', status: 1 }
    ]
  } finally {
    loading.value = false
  }
}

// 弹窗
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  type: 'FIXED',
  value: 10,
  minAmount: 0,
  totalCount: 100,
  timeRange: [] as string[]
})

const rules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入优惠值', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入发放数量', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  Object.assign(form, { name: '', type: 'FIXED', value: 10, minAmount: 0, totalCount: 100, timeRange: [] })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, type: row.type, value: row.value,
    minAmount: row.minAmount, totalCount: row.totalCount,
    timeRange: [row.startTime, row.endTime]
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除优惠券"${row.name}"？`, '提示', { type: 'warning' })
  tableData.value = tableData.value.filter(item => item.id !== row.id)
  ElMessage.success('删除成功')
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate((valid) => {
    if (!valid) return
    if (dialogType.value === 'add') {
      tableData.value.push({
        id: Date.now(), name: form.name, type: form.type, value: form.value,
        minAmount: form.minAmount, totalCount: form.totalCount, usedCount: 0,
        startTime: form.timeRange[0], endTime: form.timeRange[1], status: 1
      })
      ElMessage.success('创建成功')
    } else {
      const target = tableData.value.find(item => item.id === editingId.value)
      if (target) {
        Object.assign(target, {
          name: form.name, type: form.type, value: form.value,
          minAmount: form.minAmount, totalCount: form.totalCount,
          startTime: form.timeRange[0], endTime: form.timeRange[1]
        })
      }
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
  })
}

onMounted(fetchData)
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
