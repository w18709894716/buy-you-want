<template>
  <div class="page-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="店铺ID">
          <el-input v-model.number="searchForm.shopId" placeholder="全部" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="待审核" :value="0" />
            <el-option label="已打款" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="withdrawNo" label="提现单号" min-width="180" />
        <el-table-column label="店铺" min-width="140">
          <template #default="{ row }">{{ row.shopName || ('#' + row.shopId) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="收款账户" min-width="220">
          <template #default="{ row }">
            {{ accountTypeLabel(row.accountType) }} / {{ row.accountName }} / {{ row.accountNo }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="驳回原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="success" size="small" text @click="handlePass(row)">通过打款</el-button>
              <el-button type="danger" size="small" text @click="handleReject(row)">驳回</el-button>
            </template>
            <span v-else style="color:#909399;">-</span>
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回提现" width="480px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="4" placeholder="请输入驳回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const fmt = (v: any) => Number(v ?? 0).toFixed(2)

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive<{ shopId?: number; status?: number }>({ shopId: undefined, status: undefined })

const accountTypeLabel = (t: string) =>
  ({ bank: '银行卡', alipay: '支付宝', wechat: '微信' } as Record<string, string>)[t] || t
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已打款', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}
const statusLabel = (s: number) => statusMap[s]?.label || '未知'
const statusType = (s: number) => (statusMap[s]?.type || 'info') as any

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/settle/withdraw/list', {
      params: {
        pageNum: page.value,
        pageSize: pageSize.value,
        shopId: searchForm.shopId || undefined,
        status: searchForm.status
      }
    })
    tableData.value = data?.list || []
    total.value = data?.total || 0
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取提现列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.shopId = undefined
  searchForm.status = undefined
  handleSearch()
}

const handlePass = async (row: any) => {
  await ElMessageBox.confirm(`确定通过并打款提现单「${row.withdrawNo}」（¥${fmt(row.amount)}）？`, '提示', { type: 'warning' })
  try {
    await request.post('/admin/settle/withdraw/audit', null, {
      params: { withdrawId: row.id, pass: true }
    })
    ElMessage.success('已通过打款')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '操作失败')
  }
}

const rejectVisible = ref(false)
const rejectingRow = ref<any>(null)
const rejectForm = reactive({ reason: '' })

const handleReject = (row: any) => {
  rejectingRow.value = row
  rejectForm.reason = ''
  rejectVisible.value = true
}

const submitReject = async () => {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  try {
    await request.post('/admin/settle/withdraw/audit', null, {
      params: { withdrawId: rejectingRow.value.id, pass: false, rejectReason: rejectForm.reason.trim() }
    })
    ElMessage.success('已驳回，冻结金额已退回商家可用余额')
    rejectVisible.value = false
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '操作失败')
  }
}

onMounted(fetchData)
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
