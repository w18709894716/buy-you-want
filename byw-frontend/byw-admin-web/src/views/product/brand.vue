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
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="90" />
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
          <ImageUpload v-model="dialogForm.logoList" :limit="1" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import ImageUpload from '../../components/ImageUpload.vue'

const loading = ref(false)
const searchForm = reactive({ name: '' })

const tableData = ref<any[]>([
  { id: 1, logo: 'https://via.placeholder.com/60x40', name: 'Apple', firstLetter: 'A', sort: 1, status: 1, productCount: 128 },
  { id: 2, logo: 'https://via.placeholder.com/60x40', name: 'Sony', firstLetter: 'S', sort: 2, status: 1, productCount: 85 },
  { id: 3, logo: 'https://via.placeholder.com/60x40', name: 'Nike', firstLetter: 'N', sort: 3, status: 1, productCount: 210 },
  { id: 4, logo: 'https://via.placeholder.com/60x40', name: '小米', firstLetter: 'X', sort: 4, status: 1, productCount: 96 },
  { id: 5, logo: 'https://via.placeholder.com/60x40', name: '华为', firstLetter: 'H', sort: 5, status: 1, productCount: 152 },
  { id: 6, logo: 'https://via.placeholder.com/60x40', name: 'Adidas', firstLetter: 'A', sort: 6, status: 0, productCount: 73 }
])

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

const handleSearch = () => { /* computed handles filtering */ }

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
    firstLetter: row.firstLetter,
    logoList: row.logo ? [row.logo] : [],
    sort: row.sort,
    status: row.status
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定要删除品牌"${row.name}"吗？`, '提示', { type: 'warning' })
  tableData.value = tableData.value.filter(item => item.id !== row.id)
  ElMessage.success('删除成功')
}

const submitForm = async () => {
  if (!dialogFormRef.value) return
  await dialogFormRef.value.validate((valid) => {
    if (!valid) return
    if (dialogType.value === 'add') {
      tableData.value.push({
        id: Date.now(),
        logo: dialogForm.logoList[0] || 'https://via.placeholder.com/60x40',
        name: dialogForm.name,
        firstLetter: dialogForm.firstLetter,
        sort: dialogForm.sort,
        status: dialogForm.status,
        productCount: 0
      })
      ElMessage.success('添加成功')
    } else {
      const target = tableData.value.find(item => item.id === editingId.value)
      if (target) {
        Object.assign(target, {
          name: dialogForm.name,
          firstLetter: dialogForm.firstLetter,
          logo: dialogForm.logoList[0] || target.logo,
          sort: dialogForm.sort,
          status: dialogForm.status
        })
      }
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
  })
}
</script>

<style scoped lang="scss">
.page-container {
  .search-card {
    margin-bottom: 16px;
  }
}
</style>
