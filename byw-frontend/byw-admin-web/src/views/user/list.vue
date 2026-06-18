<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="searchForm.phone" placeholder="请输入手机号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable>
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="会员等级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.level === 'VIP' ? 'warning' : 'info'">
              {{ row.level || '普通' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created" label="注册时间" min-width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              size="small"
              text
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({
  username: '',
  phone: '',
  status: undefined as number | undefined
})

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/user/list', {
      params: { page: page.value, pageSize: pageSize.value, ...searchForm }
    })
    tableData.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 使用 mock 数据兜底
    tableData.value = [
      { id: 1, username: 'zhangsan', phone: '13800001111', nickname: '张三', status: 1, level: 'VIP', created: '2025-01-10 10:20:30' },
      { id: 2, username: 'lisi', phone: '13800002222', nickname: '李四', status: 1, level: '普通', created: '2025-02-15 14:05:12' },
      { id: 3, username: 'wangwu', phone: '13800003333', nickname: '王五', status: 0, level: '普通', created: '2025-03-22 09:18:45' },
      { id: 4, username: 'zhaoliu', phone: '13800004444', nickname: '赵六', status: 1, level: 'VIP', created: '2025-04-01 16:40:20' }
    ]
    total.value = 4
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  searchForm.username = ''
  searchForm.phone = ''
  searchForm.status = undefined
  handleSearch()
}

const toggleStatus = async (row: any) => {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定要${action}该用户吗？`, '提示', { type: 'warning' })
  try {
    await request.put(`/admin/user/${row.id}/status`, { status: row.status === 1 ? 0 : 1 })
    ElMessage.success(`${action}成功`)
    row.status = row.status === 1 ? 0 : 1
  } catch {
    // mock 切换
    row.status = row.status === 1 ? 0 : 1
    ElMessage.success(`${action}成功（mock）`)
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
