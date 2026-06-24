<template>
  <div class="page-container">
    <!-- 状态标签页 -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待付款" name="PENDING" />
        <el-tab-pane label="待发货" name="PAID" />
        <el-tab-pane label="已发货" name="SHIPPED" />
        <el-tab-pane label="已完成" name="COMPLETED" />
        <el-tab-pane label="已取消" name="CANCELLED" />
      </el-tabs>

      <!-- 搜索表单 -->
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
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
        <el-table-column prop="username" label="下单用户" width="120" />
        <el-table-column prop="amount" label="订单金额" width="120">
          <template #default="{ row }">
            <span style="color:#F56C6C;font-weight:600;">¥{{ row.amount.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="payType" label="支付方式" width="100" />
        <el-table-column prop="created" label="下单时间" min-width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PAID'"
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
          <el-descriptions-item label="下单用户">{{ currentOrder.username }}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{ currentOrder.amount.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="statusType(currentOrder.status)">{{ statusLabel(currentOrder.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ currentOrder.payType }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ currentOrder.createdAt }}</el-descriptions-item>
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
    <el-dialog v-model="shipVisible" title="订单发货" width="480px">
      <el-form ref="shipFormRef" :model="shipForm" :rules="shipRules" label-width="90px">
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
          <el-input v-model="shipForm.trackingNo" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
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
const activeTab = ref('all')
const tableData = ref<any[]>([])

const searchForm = reactive({ orderNo: '', username: '' })

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待付款', type: 'info' },
  1: { label: '待发货', type: 'warning' },
  2: { label: '已发货', type: 'primary' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'danger' }
}

const statusLabel = (s: number) => statusMap[s]?.label || s
const statusType = (s: number) => (statusMap[s]?.type as any) || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { pageNum: page.value, pageSize: pageSize.value, orderNo: searchForm.orderNo }
    if (activeTab.value !== 'all') params.status = parseInt(activeTab.value)
    const data: any = await request.get('/admin/order/list', { params })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    ElMessage.error(error?.message || '获取订单列表失败')
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => { page.value = 1; fetchData() }
const handleSearch = () => { page.value = 1; fetchData() }
const resetSearch = () => { searchForm.orderNo = ''; searchForm.username = ''; handleSearch() }

// 详情
const detailVisible = ref(false)
const currentOrder = ref<any>(null)
const showDetail = (row: any) => { currentOrder.value = row; detailVisible.value = true }

// 发货
const shipVisible = ref(false)
const shipFormRef = ref<FormInstance>()
const shippingOrder = ref<any>(null)
const shipForm = reactive({ company: '', trackingNo: '' })
const shipRules = {
  company: [{ required: true, message: '请选择物流公司', trigger: 'change' }],
  trackingNo: [{ required: true, message: '请输入物流单号', trigger: 'blur' }]
}

const handleShip = (row: any) => {
  shippingOrder.value = row
  shipForm.company = ''
  shipForm.trackingNo = ''
  shipVisible.value = true
}

const submitShip = async () => {
  if (!shipFormRef.value) return
  await shipFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await request.put(`/admin/order/${shippingOrder.value.orderNo}/ship`, null, { params: shipForm })
      ElMessage.success('发货成功')
      shippingOrder.value.status = 2
      fetchData()
    } catch (error: any) {
      ElMessage.error(error?.message || '发货失败')
    }
    shipVisible.value = false
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
