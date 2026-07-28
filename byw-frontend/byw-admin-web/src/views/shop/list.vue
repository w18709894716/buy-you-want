<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="店铺状态">
          <el-select v-model="status" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option label="营业中" :value="1" />
            <el-option label="已关店" :value="0" />
            <el-option label="已封禁" :value="2" />
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
        <el-table-column label="Logo" width="70">
          <template #default="{ row }">
            <el-image v-if="row.logo" :src="row.logo" fit="cover" class="shop-logo" preview-teleported :preview-src-list="[row.logo]" />
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="店铺名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="selfOperated" label="店铺类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.selfOperated === 0 ? 'danger' : 'primary'">
              {{ row.selfOperated === 0 ? '平台自营' : '第三方商家' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="100">
          <template #default="{ row }">{{ row.contactName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="130">
          <template #default="{ row }">{{ row.contactPhone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="description" label="简介" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="开店时间" min-width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 1" type="success" size="small" text @click="changeStatus(row, 1)">恢复营业</el-button>
            <el-button v-if="row.status === 1" type="warning" size="small" text @click="changeStatus(row, 0)">关店</el-button>
            <el-button v-if="row.status !== 2" type="danger" size="small" text @click="changeStatus(row, 2)">封禁</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])
const status = ref<number | undefined>(undefined)

const statusLabel = (s: number) => ({ 0: '已关店', 1: '营业中', 2: '已封禁' }[s] || '未知')
const statusTag = (s: number) => (({ 0: 'info', 1: 'success', 2: 'danger' }[s] || 'info') as any)

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/shop/admin/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, status: status.value }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取店铺列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  status.value = undefined
  handleSearch()
}

const changeStatus = async (row: any, target: number) => {
  const actionMap: Record<number, string> = { 0: '关店', 1: '恢复营业', 2: '封禁' }
  const action = actionMap[target]
  await ElMessageBox.confirm(`确定要对店铺「${row.name}」执行${action}操作吗？`, '提示', { type: 'warning' })
  try {
    await request.put(`/shop/admin/${row.id}/status`, null, { params: { status: target } })
    ElMessage.success(`${action}成功`)
    row.status = target
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || `${action}失败`)
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

.shop-logo {
  width: 40px;
  height: 40px;
  border-radius: 4px;
}

.text-muted {
  color: #999;
  font-size: 12px;
}
</style>
