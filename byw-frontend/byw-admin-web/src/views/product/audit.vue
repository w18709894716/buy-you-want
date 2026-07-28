<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="商品名称">
          <el-input v-model="searchForm.keyword" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="searchForm.auditStatus" placeholder="请选择" clearable style="width: 160px">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
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

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image :src="row.mainImage" fit="cover" style="width:50px;height:50px;border-radius:4px;" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shopId" label="店铺ID" width="90" />
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="auditType(row.auditStatus)">{{ auditLabel(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="驳回原因" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.auditStatus !== 1"
              type="success"
              size="small"
              text
              @click="handleApprove(row)"
            >通过</el-button>
            <el-button
              v-if="row.auditStatus !== 2"
              type="danger"
              size="small"
              text
              @click="handleReject(row)"
            >驳回</el-button>
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

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回商品" width="480px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="驳回原因">
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请输入驳回原因，便于商家修改后重新提交"
          />
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

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({
  keyword: '',
  auditStatus: 0 as number | undefined
})

const auditMap: Record<number, { label: string; type: string }> = {
  0: { label: '待审核', type: 'info' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}
const auditLabel = (s: number) => auditMap[s]?.label || '待审核'
const auditType = (s: number) => (auditMap[s]?.type || 'info') as any

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/product/audit/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, keyword: searchForm.keyword, auditStatus: searchForm.auditStatus }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取审核列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.keyword = ''
  searchForm.auditStatus = 0
  handleSearch()
}

const handleApprove = async (row: any) => {
  await ElMessageBox.confirm(`确定审核通过商品「${row.name}」吗？`, '提示', { type: 'warning' })
  try {
    await request.put(`/admin/product/${row.id}/audit`, null, { params: { auditStatus: 1 } })
    ElMessage.success('审核通过')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '操作失败')
  }
}

// 驳回
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
    await request.put(`/admin/product/${rejectingRow.value.id}/audit`, null, {
      params: { auditStatus: 2, rejectReason: rejectForm.reason.trim() }
    })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '操作失败')
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
