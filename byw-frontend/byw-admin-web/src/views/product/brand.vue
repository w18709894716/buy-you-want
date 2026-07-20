<template>
  <div class="page-container">
    <!-- 搜索和操作 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="品牌名称">
          <el-input v-model="searchForm.name" placeholder="请输入品牌名称" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button type="success" @click="handleAdd">
            <el-icon><Plus /></el-icon>添加品牌
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="filteredData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="品牌Logo" width="100">
          <template #default="{ row }">
            <el-image :src="row.logo" fit="contain" style="width:60px;height:40px;" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="品牌名称" width="160" />
        <el-table-column prop="firstLetter" label="首字母" width="80" />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="90" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" min-width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" text @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '添加品牌' : '编辑品牌'"
      width="500px"
    >
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="90px">
        <el-form-item label="品牌名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="请输入品牌名称" />
        </el-form-item>
        <el-form-item label="首字母" prop="firstLetter">
          <el-input v-model="dialogForm.firstLetter" placeholder="如：A" maxlength="1" style="width:100px" />
        </el-form-item>
        <el-form-item label="品牌Logo">
          <ImageUpload v-model="dialogForm.logoList" :limit="1" folder="brand" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialogForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dialogForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import ImageUpload from '../../components/ImageUpload.vue'
import request from '../../utils/request'

const loading = ref(false)
const searchForm = reactive({ name: '' })

const tableData = ref<any[]>([])

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/product/brand/list', {
      params: { name: searchForm.name || undefined }
    })
    tableData.value = data || []
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取品牌列表失败')
  } finally {
    loading.value = false
  }
}

const filteredData = computed(() => {
  if (!searchForm.name) return tableData.value
  return tableData.value.filter(item =>
    item.name.toLowerCase().includes(searchForm.name.toLowerCase())
  )
})

const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const dialogFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const dialogForm = reactive({
  name: '',
  firstLetter: '',
  logoList: [] as string[],
  sort: 0,
  status: 1
})

const dialogRules = {
  name: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }],
  firstLetter: [{ required: true, message: '请输入首字母', trigger: 'blur' }]
}

const handleSearch = () => { fetchData() }

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  Object.assign(dialogForm, { name: '', firstLetter: '', logoList: [], sort: 0, status: 1 })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  Object.assign(dialogForm, {
    name: row.name,
    firstLetter: row.firstLetter || '',
    logoList: row.logo ? [row.logo] : [],
    sort: row.sortOrder || 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定要删除品牌“${row.name}”吗？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/product/brand/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '删除失败')
  }
}

const handleToggleStatus = async (row: any) => {
  try {
    await request.put(`/admin/product/brand/${row.id}/status`)
    ElMessage.success('状态更新成功')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '状态更新失败')
  }
}

const submitForm = async () => {
  if (!dialogFormRef.value) return
  await dialogFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const payload = {
        name: dialogForm.name,
        firstLetter: dialogForm.firstLetter,
        logo: dialogForm.logoList[0] || '',
        sortOrder: dialogForm.sort,
        status: dialogForm.status
      }
      if (dialogType.value === 'add') {
        await request.post('/admin/product/brand/create', payload)
        ElMessage.success('添加成功')
      } else {
        await request.put(`/admin/product/brand/${editingId.value}`, payload)
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '操作失败')
    }
  })
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-container {
  .search-card {
    margin-bottom: 16px;
  }
}
</style>
