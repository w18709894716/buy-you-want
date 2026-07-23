<template>
  <div class="page-container">
    <!-- 搜索与筛选 -->
    <el-card shadow="never">
      <!-- 搜索表单 -->
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="订单状态">
          <el-select
            v-model="searchForm.status"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            placeholder="全部状态"
            style="width: 260px"
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
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="payAmount" label="订单金额" width="120">
          <template #default="{ row }">
            <span style="color:#F56C6C;font-weight:600;">¥{{ formatAmount(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 1 || row.status === 7"
              type="success"
              size="small"
              text
              @click="handleShip(row)"
            >发货</el-button>
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

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="680px">
      <template v-if="currentOrder">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ currentOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentOrder.userId }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ formatAmount(currentOrder.payAmount) }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusType(currentOrder.status)">{{ statusLabel(currentOrder.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatTime(currentOrder.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{ currentOrder.receiverAddress || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>订单商品</el-divider>
        <el-table :data="currentOrder.items || []" stripe border size="small">
          <el-table-column prop="productName" label="商品名称" min-width="180" />
          <el-table-column prop="skuName" label="规格" width="120" />
          <el-table-column prop="price" label="单价" width="100">
            <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="subtotal" label="小计" width="100">
            <template #default="{ row }">¥{{ (row.price * row.quantity)?.toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发货弹窗 -->
    <el-dialog v-model="shipVisible" title="订单发货" width="640px">
      <div v-loading="shipLoading">
        <el-alert
          v-if="shipItemsData.length && shipItemsData.every(i => i.shipStatus === 1)"
          title="该订单所有商品均已发货"
          type="info"
          :closable="false"
          style="margin-bottom:12px;"
        />
        <el-alert
          v-else
          title="勾选要发货的商品（同一批勾选的商品将合并为一个包裹）"
          type="warning"
          :closable="false"
          style="margin-bottom:12px;"
        />
        <el-table
          ref="shipTableRef"
          :data="shipItemsData"
          border
          size="small"
          row-key="id"
          @selection-change="handleShipSelectionChange"
        >
          <el-table-column type="selection" width="45" :selectable="isShipSelectable" />
          <el-table-column prop="productName" label="商品名称" min-width="160" />
          <el-table-column prop="skuName" label="规格" width="110" />
          <el-table-column prop="quantity" label="数量" width="60" />
          <el-table-column label="发货状态" width="200">
            <template #default="{ row }">
              <template v-if="row.shipStatus === 1">
                <el-tag type="success" size="small">已发货</el-tag>
                <div style="font-size:12px;color:#909399;margin-top:2px;">
                  {{ row.companyName }} {{ row.trackingNo }}
                </div>
              </template>
              <el-tag v-else type="info" size="small">未发货</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="90px" style="margin-top:16px;">
          <el-form-item label="物流公司" prop="company">
            <el-select v-model="shipForm.company" placeholder="请选择" style="width:100%">
              <el-option label="顺丰速运" value="顺丰速运" />
              <el-option label="中通快递" value="中通快递" />
              <el-option label="圆通速递" value="圆通速递" />
              <el-option label="韵达快递" value="韵达快递" />
              <el-option label="京东物流" value="京东物流" />
            </el-select>
          </el-form-item>
          <el-form-item label="物流单号" prop="trackingNo">
            <el-input v-model="shipForm.trackingNo" placeholder="留空则自动生成" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="shipVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShip">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, FormInstance } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({ orderNo: '', username: '', status: [] as number[] })

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待付款', type: 'info' },
  1: { label: '待发货', type: 'warning' },
  2: { label: '已发货', type: 'primary' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'danger' },
  7: { label: '部分发货', type: 'warning' }
}

const statusLabel = (s: number) => statusMap[s]?.label || s
const statusType = (s: number) => (statusMap[s]?.type as any) || 'info'

// 订单状态下拉选项
const statusOptions = [
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '已发货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '已取消', value: 4 },
  { label: '部分发货', value: 7 }
]

// 格式化金额
const formatAmount = (amount: any) => {
  if (amount === null || amount === undefined) return '0.00'
  return Number(amount).toFixed(2)
}

// 格式化时间（处理 LocalDateTime 数组格式或字符串格式）
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
    const params: any = { pageNum: page.value, pageSize: pageSize.value, orderNo: searchForm.orderNo }
    if (searchForm.status.length > 0) params.status = searchForm.status.join(',')
    const data: any = await request.get('/admin/order/list', { params })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取订单列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchData() }
const resetSearch = () => { searchForm.orderNo = ''; searchForm.username = ''; searchForm.status = []; handleSearch() }

// 详情
const detailVisible = ref(false)
const currentOrder = ref<any>(null)
const showDetail = (row: any) => { currentOrder.value = row; detailVisible.value = true }

// 发货
const shipVisible = ref(false)
const shipLoading = ref(false)
const shipFormRef = ref<FormInstance>()
const shipTableRef = ref<any>()
const shippingOrder = ref<any>(null)
const shipItemsData = ref<any[]>([])
const selectedItemIds = ref<number[]>([])
const shipForm = reactive({ company: '', trackingNo: '' })
const shipRules = {
  company: [{ required: true, message: '请选择物流公司', trigger: 'change' }]
}

const isShipSelectable = (row: any) => row.shipStatus !== 1
const handleShipSelectionChange = (rows: any[]) => {
  selectedItemIds.value = rows.map((r) => r.id)
}

const handleShip = async (row: any) => {
  shippingOrder.value = row
  shipForm.company = ''
  shipForm.trackingNo = ''
  selectedItemIds.value = []
  shipItemsData.value = []
  shipVisible.value = true
  shipLoading.value = true
  try {
    // 拉取最新订单详情，获取各商品发货状态
    const data: any = await request.get(`/admin/order/${row.orderNo}`)
    shipItemsData.value = data.items || []
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取订单商品失败')
  } finally {
    shipLoading.value = false
  }
}

const submitShip = async () => {
  if (!shipFormRef.value) return
  if (selectedItemIds.value.length === 0) {
    ElMessage.warning('请至少勾选一件要发货的商品')
    return
  }
  await shipFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await request.post(
        `/admin/order/${shippingOrder.value.orderNo}/ship-items`,
        selectedItemIds.value,
        { params: { company: shipForm.company, trackingNo: shipForm.trackingNo || undefined } }
      )
      ElMessage.success('发货成功')
      shipVisible.value = false
      fetchData()
    } catch (error: any) {
      if (!error._handled) ElMessage.error(error?.message || '发货失败')
    }
  })
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
