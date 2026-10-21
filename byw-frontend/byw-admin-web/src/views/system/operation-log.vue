<template>
  <div class="page-container">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <el-input v-model="query.module" placeholder="模块（商品/订单/权限…）" clearable style="width: 190px"
                  @keyup.enter="handleSearch" />
        <el-input v-model="query.operatorId" placeholder="操作人ID" clearable style="width: 130px"
                  @keyup.enter="handleSearch" />
        <el-select v-model="query.success" placeholder="结果" clearable style="width: 110px">
          <el-option label="成功" :value="1" />
          <el-option label="失败" :value="0" />
        </el-select>
        <el-date-picker v-model="timeRange" type="datetimerange" range-separator="~"
                        start-placeholder="开始时间" end-placeholder="结束时间"
                        value-format="YYYY-MM-DD HH:mm:ss" style="width: 380px" />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="module" label="模块" width="90" />
        <el-table-column prop="operation" label="操作" width="110" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作人" width="170">
          <template #default="{ row }">
            <span v-if="row.operatorId">
              {{ row.operatorName || '-' }}（{{ row.operatorType || '-' }}:{{ row.operatorId }}）
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.costMs != null ? row.costMs + ' ms' : '-' }}</template>
        </el-table-column>
        <el-table-column prop="clientIp" label="客户端IP" width="140" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="操作日志详情" width="680px">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块 / 操作">
          {{ detail.module || '-' }} / {{ detail.operation || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="描述">{{ detail.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作人">
          {{ detail.operatorName || '-' }}（{{ detail.operatorType || '-' }}:{{ detail.operatorId ?? '-' }}）
        </el-descriptions-item>
        <el-descriptions-item label="方法">{{ detail.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求">
          {{ detail.requestMethod || '-' }} {{ detail.requestUri || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ detail.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果 / 耗时">
          {{ detail.success === 1 ? '成功' : '失败' }} / {{ detail.costMs != null ? detail.costMs + ' ms' : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ detail.errorMsg || '-' }}</el-descriptions-item>
        <el-descriptions-item label="链路ID">{{ detail.traceId || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const timeRange = ref<[string, string] | null>(null)

const query = reactive<{ module?: string; operatorId?: string; success?: number }>({
  module: '',
  operatorId: '',
  success: undefined
})

const detailVisible = ref(false)
const detail = ref<any>({})

const loadData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/system/operation-log', {
      params: {
        pageNum: page.value,
        pageSize: pageSize.value,
        module: query.module || undefined,
        operatorId: query.operatorId || undefined,
        success: query.success,
        startTime: timeRange.value?.[0],
        endTime: timeRange.value?.[1]
      }
    })
    tableData.value = data?.list || []
    total.value = data?.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取操作日志失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handleReset = () => {
  query.module = ''
  query.operatorId = ''
  query.success = undefined
  timeRange.value = null
  handleSearch()
}

const openDetail = (row: any) => {
  detail.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
