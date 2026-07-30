<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 搜索与筛选 -->
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="售后状态">
          <el-select
            v-model="searchForm.status"
            clearable
            placeholder="全部状态"
            style="width: 200px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="afterSaleNo" label="售后单号" width="200" />
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column label="售后商品" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="row.productName">
              <div>{{ row.productName }}</div>
              <div v-if="row.skuName" style="font-size:12px;color:#909399;">{{ row.skuName }}</div>
            </template>
            <el-tag v-else size="small" type="info">整单</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="售后类型" width="110">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="110">
          <template #default="{ row }">
            <span v-if="row.refundAmount != null" style="color:#F56C6C;font-weight:600;">¥{{ formatAmount(row.refundAmount) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 0"
              type="success"
              size="small"
              text
              @click="handleApprove(row)"
            >同意</el-button>
            <el-button
              v-if="row.status === 0"
              type="danger"
              size="small"
              text
              @click="handleReject(row)"
            >拒绝</el-button>
            <el-button
              v-if="row.status === 5"
              type="warning"
              size="small"
              text
              @click="handleConfirmReturn(row)"
            >确认收货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 售后详情弹窗 -->
    <el-dialog v-model="detailVisible" title="售后详情" width="680px">
      <template v-if="currentRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="售后单号">{{ currentRow.afterSaleNo }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ currentRow.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="售后商品" :span="2">
            <template v-if="currentRow.productName">
              {{ currentRow.productName }}<span v-if="currentRow.skuName" style="color:#909399;">（{{ currentRow.skuName }}）</span>
            </template>
            <el-tag v-else size="small" type="info">整单</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="售后类型">{{ typeLabel(currentRow.type) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(currentRow.status)">{{ statusLabel(currentRow.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="退款金额">
            {{ currentRow.refundAmount != null ? '¥' + formatAmount(currentRow.refundAmount) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ formatTime(currentRow.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ currentRow.reason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="问题描述" :span="2">{{ currentRow.description || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.rejectReason" label="拒绝原因" :span="2">{{ currentRow.rejectReason }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.approveTime" label="审核通过时间">{{ formatTime(currentRow.approveTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.returnTrackingNo" label="买家寄回">{{ currentRow.returnCompany }} {{ currentRow.returnTrackingNo }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.returnShipTime" label="寄回时间">{{ formatTime(currentRow.returnShipTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.receiveTime" label="确认收货时间">{{ formatTime(currentRow.receiveTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="currentRow.finishTime" label="完成时间">{{ formatTime(currentRow.finishTime) }}</el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 拒绝原因弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒绝售后申请" width="480px">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="90px">
        <el-form-item label="拒绝原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请填写拒绝原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({ status: undefined as number | undefined })

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '待买家寄回', type: 'primary' },
  2: { label: '已拒绝', type: 'danger' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已撤销', type: 'info' },
  5: { label: '待商家收货', type: 'primary' },
  6: { label: '退款中', type: 'warning' }
}
const statusLabel = (s: number) => statusMap[s]?.label || s
const statusType = (s: number) => (statusMap[s]?.type as any) || 'info'

const statusOptions = [
  { label: '待审核', value: 0 },
  { label: '待买家寄回', value: 1 },
  { label: '已拒绝', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已撤销', value: 4 },
  { label: '待商家收货', value: 5 },
  { label: '退款中', value: 6 }
]

const typeMap: Record<number, string> = {
  1: '仅退款', 2: '退货退款', 3: '换货', 4: '维修', 5: '补寄', 6: '价保'
}
const typeLabel = (t: number) => typeMap[t] || t

const formatAmount = (amount: any) => {
  if (amount === null || amount === undefined) return '0.00'
  return Number(amount).toFixed(2)
}

const formatTime = (time: any) => {
  if (!time) return '-'
  if (Array.isArray(time)) {
    const [year, month, day, hour, minute, second] = time
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour || 0).padStart(2, '0')}:${String(minute || 0).padStart(2, '0')}:${String(second || 0).padStart(2, '0')}`
  }
  return time
}

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: page.value, pageSize: pageSize.value }
    if (searchForm.status !== undefined && searchForm.status !== null) params.status = searchForm.status
    const data: any = await request.get('/merchant/after-sale/list', { params })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取售后列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchData() }
const resetSearch = () => { searchForm.status = undefined; handleSearch() }

// 详情
const detailVisible = ref(false)
const currentRow = ref<any>(null)
const showDetail = (row: any) => { currentRow.value = row; detailVisible.value = true }

// 同意
const handleApprove = (row: any) => {
  ElMessageBox.confirm(
    row.type === 2 ? '同意后将通知买家寄回商品，确认？' : '同意后将立即为买家退款，确认？',
    '同意售后',
    { type: 'warning' }
  ).then(async () => {
    try {
      await request.post(`/merchant/after-sale/${row.id}/approve`)
      ElMessage.success('已同意')
      fetchData()
    } catch (error: any) {
      if (!error._handled) ElMessage.error(error?.message || '操作失败')
    }
  }).catch(() => {})
}

// 拒绝
const rejectVisible = ref(false)
const rejectFormRef = ref<FormInstance>()
const rejectRow = ref<any>(null)
const rejectForm = reactive({ reason: '' })
const rejectRules = {
  reason: [{ required: true, message: '请填写拒绝原因', trigger: 'blur' }]
}
const handleReject = (row: any) => {
  rejectRow.value = row
  rejectForm.reason = ''
  rejectVisible.value = true
}
const submitReject = async () => {
  if (!rejectFormRef.value) return
  await rejectFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await request.post(`/merchant/after-sale/${rejectRow.value.id}/reject`, null, {
        params: { reason: rejectForm.reason }
      })
      ElMessage.success('已拒绝')
      rejectVisible.value = false
      fetchData()
    } catch (error: any) {
      if (!error._handled) ElMessage.error(error?.message || '操作失败')
    }
  })
}

// 确认收货
const handleConfirmReturn = (row: any) => {
  ElMessageBox.confirm('确认已收到买家寄回的商品？确认后将立即退款。', '确认收货', { type: 'warning' })
    .then(async () => {
      try {
        await request.post(`/merchant/after-sale/${row.id}/confirm-return`)
        ElMessage.success('已确认收货并退款')
        fetchData()
      } catch (error: any) {
        if (!error._handled) ElMessage.error(error?.message || '操作失败')
      }
    }).catch(() => {})
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-container {
  .search-form {
    margin-bottom: 16px;
    margin-top: 8px;
  }

  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
