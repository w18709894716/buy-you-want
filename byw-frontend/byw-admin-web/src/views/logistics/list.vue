<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
        </el-form-item>
        <el-form-item label="物流状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="已揽收" value="COLLECTED" />
            <el-option label="运输中" value="IN_TRANSIT" />
            <el-option label="派送中" value="DELIVERING" />
            <el-option label="已签收" value="SIGNED" />
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

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="orderNo" label="订单号" width="180" />
        <el-table-column prop="company" label="物流公司" width="120" />
        <el-table-column prop="trackingNo" label="物流单号" width="180" />
        <el-table-column prop="status" label="物流状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latestInfo" label="最新物流信息" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="showTracking(row)">
              <el-icon><View /></el-icon>轨迹
            </el-button>
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

    <!-- 物流轨迹弹窗 -->
    <el-dialog v-model="trackingVisible" title="物流轨迹" width="600px">
      <template v-if="currentLogistics">
        <el-descriptions :column="2" border style="margin-bottom:16px;">
          <el-descriptions-item label="订单号">{{ currentLogistics.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="物流公司">{{ currentLogistics.company }}</el-descriptions-item>
          <el-descriptions-item label="物流单号">{{ currentLogistics.trackingNo }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="statusType(currentLogistics.status)">{{ statusLabel(currentLogistics.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in trackingList"
            :key="index"
            :timestamp="item.time"
            :type="index === 0 ? 'primary' : ''"
            placement="top"
          >
            {{ item.info }}
          </el-timeline-item>
        </el-timeline>
      </template>
      <template #footer>
        <el-button @click="trackingVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({ orderNo: '', status: '' })

const statusMap: Record<string, { label: string; type: string }> = {
  0: { label: '已揽收', type: 'info' },
  1: { label: '运输中', type: 'primary' },
  2: { label: '派送中', type: 'warning' },
  3: { label: '已签收', type: 'success' }
}
const statusLabel = (s: number | string) => statusMap[s]?.label || s
const statusType = (s: number | string) => (statusMap[s]?.type as any) || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/logistics/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, ...searchForm }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '获取物流列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchData() }
const resetSearch = () => { searchForm.orderNo = ''; searchForm.status = ''; handleSearch() }

// 物流轨迹
const trackingVisible = ref(false)
const currentLogistics = ref<any>(null)
const trackingList = ref<any[]>([])

const showTracking = async (row: any) => {
  currentLogistics.value = row
  try {
    const data: any = await request.get(`/admin/logistics/${row.id}/trace`)
    trackingList.value = (data || []).map((item: any) => ({
      time: item.traceTime || item.createdAt,
      info: item.description
    }))
  } catch (e: any) {
    ElMessage.error(e.message || '获取物流轨迹失败')
    trackingList.value = []
  }
  trackingVisible.value = true
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
