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
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({ orderNo: '', status: '' })

const statusMap: Record<string, { label: string; type: string }> = {
  COLLECTED: { label: '已揽收', type: 'info' },
  IN_TRANSIT: { label: '运输中', type: 'primary' },
  DELIVERING: { label: '派送中', type: 'warning' },
  SIGNED: { label: '已签收', type: 'success' }
}
const statusLabel = (s: string) => statusMap[s]?.label || s
const statusType = (s: string) => (statusMap[s]?.type as any) || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/logistics/list', {
      params: { page: page.value, pageSize: pageSize.value, ...searchForm }
    })
    tableData.value = data.records || []
    total.value = data.total || 0
  } catch {
    tableData.value = [
      { id: 1, orderNo: 'BYW20250601001', company: '顺丰速运', trackingNo: 'SF1234567890', status: 'SIGNED', latestInfo: '您的快件已由本人签收，感谢使用顺丰', updatedTime: '2025-06-03 15:20:30' },
      { id: 2, orderNo: 'BYW20250602002', company: '中通快递', trackingNo: 'ZT9876543210', status: 'IN_TRANSIT', latestInfo: '快件已到达【北京转运中心】', updatedTime: '2025-06-14 08:10:22' },
      { id: 3, orderNo: 'BYW20250603003', company: '京东物流', trackingNo: 'JD1122334455', status: 'DELIVERING', latestInfo: '快递员正在派送中，请保持电话畅通', updatedTime: '2025-06-15 10:05:18' },
      { id: 4, orderNo: 'BYW20250604004', company: '圆通速递', trackingNo: 'YT5566778899', status: 'COLLECTED', latestInfo: '快件已由深圳市南山区网点揽收', updatedTime: '2025-06-15 14:40:50' }
    ]
    total.value = 4
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

const showTracking = (row: any) => {
  currentLogistics.value = row
  trackingList.value = [
    { time: '2025-06-15 14:40:50', info: '快件已由发货地网点揽收' },
    { time: '2025-06-15 18:20:10', info: '快件已到达【深圳转运中心】' },
    { time: '2025-06-15 22:05:33', info: '快件已从【深圳转运中心】发出，下一站【北京转运中心】' },
    { time: '2025-06-16 08:10:22', info: '快件已到达【北京转运中心】' },
    { time: '2025-06-16 10:05:18', info: '快递员正在派送中，请保持电话畅通' }
  ]
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
