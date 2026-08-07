<template>
  <div class="page-container">
    <!-- 评分概览 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">综合评分</div>
          <div class="stat-value primary">{{ stats.avgRating.toFixed(1) }}</div>
          <div class="stat-sub">共 {{ stats.totalCount }} 条评价</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">5 星</div>
          <div class="stat-value success">{{ stats.rating5Count }}</div>
          <div class="stat-sub">占比 {{ stats.totalCount > 0 ? (stats.rating5Count / stats.totalCount * 100).toFixed(1) : 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">4 星</div>
          <div class="stat-value info">{{ stats.rating4Count }}</div>
          <div class="stat-sub">占比 {{ stats.totalCount > 0 ? (stats.rating4Count / stats.totalCount * 100).toFixed(1) : 0 }}%</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-title">3 星及以下</div>
          <div class="stat-value" :class="stats.rating3Count + stats.rating2Count + stats.rating1Count > 0 ? 'warning' : ''">
            {{ stats.rating3Count + stats.rating2Count + stats.rating1Count }}
          </div>
          <div class="stat-sub">需关注改进</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>服务评价</span>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column label="评分" width="140">
          <template #default="{ row }">
            <span class="text-yellow-400">{{ '★'.repeat(row.rating) }}{{ '☆'.repeat(5 - row.rating) }}</span>
            <span class="text-xs text-gray-400 ml-1">{{ row.rating }}分</span>
          </template>
        </el-table-column>
        <el-table-column prop="staffName" label="客服" width="120" />
        <el-table-column label="评价标签" min-width="160">
          <template #default="{ row }">
            <el-tag v-for="tag in (row.tags || '').split(',').filter(Boolean)" :key="tag" size="small" class="mr-1 mb-1">{{ tag }}</el-tag>
            <span v-if="!row.tags" class="text-gray-400 text-xs">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="留言" min-width="200">
          <template #default="{ row }">
            <span v-if="row.comment" class="text-gray-700">{{ row.comment }}</span>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="会话ID" width="100">
          <template #default="{ row }">{{ row.conversationId }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="评价时间" width="170" />
      </el-table>

      <div v-if="total > pageSize" class="page-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'

interface Satisfaction {
  id: number
  shopId: number
  conversationId: number
  userId: number
  staffId?: number
  staffName?: string
  rating: number
  tags?: string
  comment?: string
  createdAt: string
}

interface SatisfactionStats {
  avgRating: number
  totalCount: number
  rating5Count: number
  rating4Count: number
  rating3Count: number
  rating2Count: number
  rating1Count: number
}

const loading = ref(false)
const tableData = ref<Satisfaction[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const stats = ref<SatisfactionStats>({
  avgRating: 0,
  totalCount: 0,
  rating5Count: 0,
  rating4Count: 0,
  rating3Count: 0,
  rating2Count: 0,
  rating1Count: 0,
})

async function loadStats() {
  try {
    const data: SatisfactionStats = await request.get('/im/satisfaction/stats')
    stats.value = data
  } catch { /* ignore */ }
}

async function loadList() {
  loading.value = true
  try {
    const data: any = await request.get('/im/satisfaction/list', {
      page: page.value,
      pageSize: pageSize.value,
    })
    tableData.value = data.records || []
    total.value = data.total || 0
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  loadList()
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<style scoped>
.page-container { padding: 16px; }
.stat-row { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 20px 16px; }
.stat-title { font-size: 13px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.1; }
.stat-sub { font-size: 12px; color: #c0c4cc; margin-top: 6px; }
.primary { color: #409eff; }
.success { color: #67c23a; }
.warning { color: #e6a23c; }
.info { color: #909399; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.page-bar { display: flex; justify-content: center; margin-top: 16px; }
</style>