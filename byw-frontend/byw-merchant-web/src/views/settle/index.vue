<template>
  <div class="page-container">
    <!-- 余额概览 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">可提现余额</div>
          <div class="stat-value primary">¥{{ fmt(balance.availableBalance) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">待入账（冷静期）</div>
          <div class="stat-value warning">¥{{ fmt(balance.pendingAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">提现冻结中</div>
          <div class="stat-value info">¥{{ fmt(balance.frozenAmount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">累计收入</div>
          <div class="stat-value success">¥{{ fmt(balance.totalIncome) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>结算与提现</span>
          <el-button type="primary" :disabled="Number(balance.availableBalance) <= 0" @click="openWithdraw">
            申请提现
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 结算单 -->
        <el-tab-pane label="结算单" name="record">
          <el-table :data="recordData" v-loading="recordLoading" stripe border>
            <el-table-column prop="settleNo" label="结算单号" min-width="180" />
            <el-table-column prop="orderNo" label="订单号" min-width="180" />
            <el-table-column label="订单金额" width="110">
              <template #default="{ row }">¥{{ fmt(row.orderAmount) }}</template>
            </el-table-column>
            <el-table-column label="佣金" width="100">
              <template #default="{ row }">¥{{ fmt(row.commissionAmount) }}</template>
            </el-table-column>
            <el-table-column label="结算金额" width="110">
              <template #default="{ row }">¥{{ fmt(row.settleAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'warning'">
                  {{ row.status === 1 ? '已入账' : '待入账' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="expectSettleTime" label="预计入账时间" width="180" />
            <el-table-column prop="settleTime" label="实际入账时间" width="180">
              <template #default="{ row }">{{ row.settleTime || '-' }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="recordPage"
              v-model:page-size="recordSize"
              :page-sizes="[10, 20, 50]"
              :total="recordTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchRecords"
              @current-change="fetchRecords"
            />
          </div>
        </el-tab-pane>

        <!-- 余额流水 -->
        <el-tab-pane label="余额流水" name="flow">
          <el-table :data="flowData" v-loading="flowLoading" stripe border>
            <el-table-column prop="flowNo" label="流水号" min-width="180" />
            <el-table-column prop="typeDesc" label="类型" width="130" />
            <el-table-column label="金额" width="120">
              <template #default="{ row }">
                <span :class="Number(row.amount) >= 0 ? 'amt-plus' : 'amt-minus'">
                  {{ Number(row.amount) >= 0 ? '+' : '' }}{{ fmt(row.amount) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="变动后可用余额" width="140">
              <template #default="{ row }">¥{{ fmt(row.balanceAfter) }}</template>
            </el-table-column>
            <el-table-column prop="refNo" label="关联单号" min-width="180">
              <template #default="{ row }">{{ row.refNo || '-' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="flowPage"
              v-model:page-size="flowSize"
              :page-sizes="[10, 20, 50]"
              :total="flowTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchFlows"
              @current-change="fetchFlows"
            />
          </div>
        </el-tab-pane>

        <!-- 提现记录 -->
        <el-tab-pane label="提现记录" name="withdraw">
          <el-table :data="withdrawData" v-loading="withdrawLoading" stripe border>
            <el-table-column prop="withdrawNo" label="提现单号" min-width="180" />
            <el-table-column label="金额" width="110">
              <template #default="{ row }">¥{{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="收款账户" min-width="200">
              <template #default="{ row }">
                {{ accountTypeLabel(row.accountType) }} / {{ row.accountName }} / {{ row.accountNo }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="withdrawStatusType(row.status)">{{ withdrawStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rejectReason" label="驳回原因" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
            </el-table-column>
            <el-table-column prop="applyTime" label="申请时间" width="180" />
            <el-table-column prop="auditTime" label="审核时间" width="180">
              <template #default="{ row }">{{ row.auditTime || '-' }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="withdrawPage"
              v-model:page-size="withdrawSize"
              :page-sizes="[10, 20, 50]"
              :total="withdrawTotal"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="fetchWithdraws"
              @current-change="fetchWithdraws"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 提现申请弹窗 -->
    <el-dialog v-model="withdrawVisible" title="申请提现" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="可提现">
          <span class="primary">¥{{ fmt(balance.availableBalance) }}</span>
        </el-form-item>
        <el-form-item label="提现金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :max="Number(balance.availableBalance)" :precision="2" :step="100" />
        </el-form-item>
        <el-form-item label="账户类型" prop="accountType">
          <el-select v-model="form.accountType" placeholder="请选择" style="width: 100%">
            <el-option label="银行卡" value="bank" />
            <el-option label="支付宝" value="alipay" />
            <el-option label="微信" value="wechat" />
          </el-select>
        </el-form-item>
        <el-form-item label="收款账号" prop="accountNo">
          <el-input v-model="form.accountNo" placeholder="请输入收款账号" />
        </el-form-item>
        <el-form-item label="收款人" prop="accountName">
          <el-input v-model="form.accountName" placeholder="请输入收款人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="withdrawVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWithdraw">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, FormInstance } from 'element-plus'
import request from '../../utils/request'

const fmt = (v: any) => Number(v ?? 0).toFixed(2)

// ===== 余额 =====
const balance = reactive<any>({
  availableBalance: 0, pendingAmount: 0, frozenAmount: 0, totalIncome: 0, withdrawnAmount: 0
})
const fetchBalance = async () => {
  try {
    const data: any = await request.get('/merchant/settle/balance')
    Object.assign(balance, data || {})
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取余额失败')
  }
}

// ===== Tabs =====
const activeTab = ref('record')
const onTabChange = (name: string) => {
  if (name === 'record') fetchRecords()
  else if (name === 'flow') fetchFlows()
  else if (name === 'withdraw') fetchWithdraws()
}

// ===== 结算单 =====
const recordData = ref<any[]>([])
const recordLoading = ref(false)
const recordPage = ref(1)
const recordSize = ref(10)
const recordTotal = ref(0)
const fetchRecords = async () => {
  recordLoading.value = true
  try {
    const data: any = await request.get('/merchant/settle/record/list', {
      params: { pageNum: recordPage.value, pageSize: recordSize.value }
    })
    recordData.value = data?.list || []
    recordTotal.value = data?.total || 0
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取结算单失败')
  } finally {
    recordLoading.value = false
  }
}

// ===== 余额流水 =====
const flowData = ref<any[]>([])
const flowLoading = ref(false)
const flowPage = ref(1)
const flowSize = ref(10)
const flowTotal = ref(0)
const fetchFlows = async () => {
  flowLoading.value = true
  try {
    const data: any = await request.get('/merchant/settle/flow/list', {
      params: { pageNum: flowPage.value, pageSize: flowSize.value }
    })
    flowData.value = data?.list || []
    flowTotal.value = data?.total || 0
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取余额流水失败')
  } finally {
    flowLoading.value = false
  }
}

// ===== 提现记录 =====
const withdrawData = ref<any[]>([])
const withdrawLoading = ref(false)
const withdrawPage = ref(1)
const withdrawSize = ref(10)
const withdrawTotal = ref(0)
const fetchWithdraws = async () => {
  withdrawLoading.value = true
  try {
    const data: any = await request.get('/merchant/settle/withdraw/list', {
      params: { pageNum: withdrawPage.value, pageSize: withdrawSize.value }
    })
    withdrawData.value = data?.list || []
    withdrawTotal.value = data?.total || 0
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取提现记录失败')
  } finally {
    withdrawLoading.value = false
  }
}

const accountTypeLabel = (t: string) =>
  ({ bank: '银行卡', alipay: '支付宝', wechat: '微信' } as Record<string, string>)[t] || t
const withdrawStatusMap: Record<number, { label: string; type: string }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已打款', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}
const withdrawStatusLabel = (s: number) => withdrawStatusMap[s]?.label || '未知'
const withdrawStatusType = (s: number) => (withdrawStatusMap[s]?.type || 'info') as any

// ===== 提现申请 =====
const withdrawVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ amount: 0, accountType: 'bank', accountNo: '', accountName: '' })
const rules = {
  amount: [{ required: true, message: '请输入提现金额', trigger: 'blur' }],
  accountType: [{ required: true, message: '请选择账户类型', trigger: 'change' }],
  accountNo: [{ required: true, message: '请输入收款账号', trigger: 'blur' }],
  accountName: [{ required: true, message: '请输入收款人姓名', trigger: 'blur' }]
}
const openWithdraw = () => {
  Object.assign(form, { amount: 0, accountType: 'bank', accountNo: '', accountName: '' })
  withdrawVisible.value = true
}
const submitWithdraw = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await request.post('/merchant/settle/withdraw/apply', {
        amount: form.amount,
        accountType: form.accountType,
        accountNo: form.accountNo,
        accountName: form.accountName
      })
      ElMessage.success('提现申请已提交，等待平台审核')
      withdrawVisible.value = false
      fetchBalance()
      activeTab.value = 'withdraw'
      fetchWithdraws()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '提现申请失败')
    }
  })
}

onMounted(() => {
  fetchBalance()
  fetchRecords()
})
</script>

<style scoped lang="scss">
.page-container {
  .stat-row {
    margin-bottom: 16px;
  }

  .stat-card {
    .stat-title {
      font-size: 13px;
      color: #909399;
      margin-bottom: 8px;
    }

    .stat-value {
      font-size: 24px;
      font-weight: 600;

      &.primary { color: #409eff; }
      &.warning { color: #e6a23c; }
      &.info { color: #909399; }
      &.success { color: #67c23a; }
    }
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .amt-plus { color: #67c23a; }
  .amt-minus { color: #f56c6c; }

  .primary { color: #409eff; font-weight: 600; }

  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
