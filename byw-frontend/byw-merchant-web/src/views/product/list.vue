<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="商品名称">
          <el-input v-model="searchForm.name" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 160px">
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="审核">
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
          <el-button type="success" @click="$router.push('/product/add')">
            <el-icon><Plus /></el-icon>发布商品
          </el-button>
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
        <el-table-column prop="subtitle" label="副标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="上架状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="statusType(row.status)"
              :style="row.auditStatus === 1 ? 'cursor: pointer;' : ''"
              @click="row.auditStatus === 1 && handleToggleStatus(row)"
              :title="row.auditStatus === 1 ? (row.status === 1 ? '点击下架' : '点击上架') : '审核通过后可上架'"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="140">
          <template #default="{ row }">
            <el-tag :type="auditType(row.auditStatus)">{{ auditLabel(row.auditStatus) }}</el-tag>
            <el-tooltip
              v-if="row.auditStatus === 2 && row.rejectReason"
              :content="row.rejectReason"
              placement="top"
            >
              <el-icon style="margin-left:4px;color:#F56C6C;vertical-align:middle;cursor:pointer;"><Warning /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="salesCount" label="销量" width="90">
          <template #default="{ row }">
            {{ row.salesCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="row.auditStatus === 2"
              type="warning"
              size="small"
              text
              @click="handleResubmit(row)"
            >重新提交</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const page = ref(Number(route.query.page) || 1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({
  name: '',
  status: undefined as number | undefined,
  auditStatus: undefined as number | undefined
})

const statusMap: Record<number, { label: string; type: string }> = {
  1: { label: '上架', type: 'success' },
  2: { label: '下架', type: 'warning' }
}
const statusLabel = (s: number) => statusMap[s]?.label || '未知'
const statusType = (s: number) => (statusMap[s]?.type || 'info') as any

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
    const data: any = await request.get('/merchant/product/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, keyword: searchForm.name, status: searchForm.status, auditStatus: searchForm.auditStatus }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取商品列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.name = ''
  searchForm.status = undefined
  searchForm.auditStatus = undefined
  handleSearch()
}

const handleEdit = (row: any) => {
  router.push({ path: `/product/add/${row.id}`, query: { page: page.value } })
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定要删除该商品吗？', '提示', { type: 'warning' })
  try {
    await request.delete(`/merchant/product/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '删除失败')
  }
}

const handleToggleStatus = async (row: any) => {
  const action = row.status === 1 ? '下架' : '上架'
  await ElMessageBox.confirm(`确定要${action}该商品吗？`, '提示', { type: 'warning' })
  try {
    await request.put(`/merchant/product/${row.id}/status`)
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || `${action}失败`)
  }
}

const handleResubmit = async (row: any) => {
  await ElMessageBox.confirm('确定要重新提交该商品进行审核吗？', '提示', { type: 'warning' })
  try {
    await request.put(`/merchant/product/${row.id}/submit`)
    ElMessage.success('已提交审核')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '提交失败')
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
