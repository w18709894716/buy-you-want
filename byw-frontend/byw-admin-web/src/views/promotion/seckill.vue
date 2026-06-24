<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>秒杀活动管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>创建活动
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="活动标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="活动时间" min-width="220">
          <template #default="{ row }">{{ row.startTime }} ~ {{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button type="warning" size="small" text @click="handleManageProducts(row)">商品</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '创建秒杀活动' : '编辑秒杀活动'"
      width="540px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" placeholder="如：618限时秒杀专场" />
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="活动描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入活动描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 活动商品管理弹窗 -->
    <el-dialog v-model="productDialogVisible" title="秒杀商品管理" width="700px">
      <div style="margin-bottom:12px;">
        <el-button type="primary" size="small" @click="addSeckillProduct">
          <el-icon><Plus /></el-icon>添加商品
        </el-button>
      </div>
      <el-table :data="seckillProducts" stripe border size="small">
        <el-table-column prop="productName" label="商品名称" min-width="160" />
        <el-table-column prop="originalPrice" label="原价" width="90">
          <template #default="{ row }">¥{{ row.originalPrice.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="seckillPrice" label="秒杀价" width="90">
          <template #default="{ row }">
            <span style="color:#F56C6C;font-weight:600;">¥{{ row.seckillPrice.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="秒杀库存" width="90" />
        <el-table-column prop="limitPerUser" label="每人限购" width="90" />
        <el-table-column label="操作" width="80">
          <template #default="{ $index }">
            <el-button type="danger" size="small" text @click="seckillProducts.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="productDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveSeckillProducts">保存</el-button>
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

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未开始', type: 'info' },
  1: { label: '进行中', type: 'success' },
  2: { label: '已结束', type: 'warning' }
}
const statusLabel = (s: number) => statusMap[s]?.label || s
const statusType = (s: number) => (statusMap[s]?.type as any) || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/promotion/seckill/list')
    tableData.value = data || []
  } catch (e: any) {
    ElMessage.error(e.message || '获取秒杀活动列表失败')
  } finally {
    loading.value = false
  }
}

// 创建/编辑弹窗
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const form = reactive({ title: '', timeRange: [] as string[], description: '' })
const rules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择活动时间', trigger: 'change' }]
}

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  Object.assign(form, { title: '', timeRange: [], description: '' })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  Object.assign(form, { title: row.title, timeRange: [row.startTime, row.endTime], description: '' })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除活动"${row.name}"？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/promotion/seckill/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const payload = {
        name: form.title,
        startTime: form.timeRange[0],
        endTime: form.timeRange[1]
      }
      if (dialogType.value === 'add') {
        await request.post('/admin/promotion/seckill/create', payload)
        ElMessage.success('创建成功')
      } else {
        await request.put(`/admin/promotion/seckill/${editingId.value}`, payload)
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}

// 商品管理
const productDialogVisible = ref(false)
const seckillProducts = ref<any[]>([])

const handleManageProducts = async (row: any) => {
  try {
    const data: any = await request.get(`/admin/promotion/seckill/${row.id}`)
    seckillProducts.value = data?.items || []
  } catch (e: any) {
    ElMessage.error(e.message || '获取活动商品失败')
    seckillProducts.value = []
  }
  productDialogVisible.value = true
}

const addSeckillProduct = () => {
  seckillProducts.value.push({
    productName: '', originalPrice: 0, seckillPrice: 0, stock: 100, limitPerUser: 1
  })
}

const saveSeckillProducts = async () => {
  try {
    await request.post('/admin/promotion/seckill/save-items', {
      activityId: seckillProducts.value[0]?.activityId,
      items: seckillProducts.value
    })
    ElMessage.success('保存成功')
    productDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
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
