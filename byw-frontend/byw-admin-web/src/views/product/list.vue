<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="商品名称">
          <el-input v-model="searchForm.name" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable>
            <el-option label="数码电子" :value="1" />
            <el-option label="服装鞋帽" :value="2" />
            <el-option label="食品饮料" :value="3" />
            <el-option label="家居家装" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="$router.push('/product/add')">
            <el-icon><Plus /></el-icon>添加商品
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
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="salesCount" label="销量" width="90">
          <template #default="{ row }">
            {{ row.salesCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleEdit(row)">编辑</el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({
  name: '',
  status: undefined as number | undefined,
  categoryId: undefined as number | undefined
})

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '上架', type: 'success' },
  2: { label: '下架', type: 'warning' }
}
const statusLabel = (s: number) => statusMap[s]?.label || '未知'
const statusType = (s: number) => (statusMap[s]?.type || 'info') as any

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/product/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, keyword: searchForm.name, status: searchForm.status }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    ElMessage.error(error?.message || '获取商品列表失败')
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
  searchForm.categoryId = undefined
  handleSearch()
}

const handleEdit = (row: any) => {
  router.push(`/product/add/${row.id}`)
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定要删除该商品吗？', '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/product/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    ElMessage.error(error?.message || '删除失败')
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
