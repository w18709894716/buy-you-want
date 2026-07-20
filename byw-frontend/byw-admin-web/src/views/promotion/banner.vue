<template>
  <div class="page-container">
    <!-- 操作栏 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item>
          <el-button type="success" @click="handleAdd">
            <el-icon><Plus /></el-icon>添加轮播图
          </el-button>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="轮播图按排序值升序展示；上线/下线时间留空表示立即上线且永久有效，到点自动生效（无需手动操作）。"
          style="padding: 4px 12px;"
        />
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="图片" width="140">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              fit="cover"
              style="width:120px;height:48px;border-radius:4px;"
              :preview-src-list="[row.imageUrl]"
              preview-teleported
            />
            <span v-else class="text-gray-400 text-xs">无图(渐变兜底)</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column label="跳转类型" width="110">
          <template #default="{ row }">
            <el-tag :type="linkTypeTag(row.linkType)">{{ linkTypeText(row.linkType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="linkValue" label="跳转值" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column label="上线时间" width="160">
          <template #default="{ row }">{{ row.startTime || '立即' }}</template>
        </el-table-column>
        <el-table-column label="下线时间" width="160">
          <template #default="{ row }">{{ row.endTime || '永久' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="handleToggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
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
      :title="dialogType === 'add' ? '添加轮播图' : '编辑轮播图'"
      width="560px"
    >
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="dialogForm.title" placeholder="用于后台识别，无图时也会展示" />
        </el-form-item>
        <el-form-item label="轮播图片">
          <ImageUpload v-model="dialogForm.imageList" :limit="1" folder="banner" />
          <div class="text-xs text-gray-400 mt-1">建议尺寸 1200×400，留空则展示渐变+标题</div>
        </el-form-item>
        <el-form-item label="跳转类型" prop="linkType">
          <el-select v-model="dialogForm.linkType" style="width: 200px">
            <el-option :value="1" label="搜索关键词" />
            <el-option :value="2" label="商品详情" />
            <el-option :value="3" label="商品分类" />
            <el-option :value="4" label="自定义链接" />
          </el-select>
        </el-form-item>
        <el-form-item :label="linkValueLabel" prop="linkValue">
          <el-input v-model="dialogForm.linkValue" :placeholder="linkValuePlaceholder" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialogForm.sortOrder" :min="0" :max="9999" />
          <span class="text-xs text-gray-400 ml-2">数值越小越靠前</span>
        </el-form-item>
        <el-form-item label="上线时间">
          <el-date-picker
            v-model="dialogForm.startTime"
            type="datetime"
            placeholder="留空表示立即上线"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="下线时间">
          <el-date-picker
            v-model="dialogForm.endTime"
            type="datetime"
            placeholder="留空表示永久有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
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
const tableData = ref<any[]>([])

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/banner/list')
    tableData.value = data || []
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取轮播图列表失败')
  } finally {
    loading.value = false
  }
}

const linkTypeText = (type: number) =>
  ({ 1: '搜索关键词', 2: '商品详情', 3: '商品分类', 4: '自定义链接' } as Record<number, string>)[type] || '未知'

const linkTypeTag = (type: number) =>
  ({ 1: 'primary', 2: 'success', 3: 'warning', 4: 'info' } as Record<number, string>)[type] || 'info'

const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const dialogFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)

const dialogForm = reactive({
  title: '',
  imageList: [] as string[],
  linkType: 1,
  linkValue: '',
  sortOrder: 0,
  status: 1,
  startTime: '' as string | null,
  endTime: '' as string | null
})

const dialogRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  linkType: [{ required: true, message: '请选择跳转类型', trigger: 'change' }]
}

const linkValueLabel = computed(() =>
  ({ 1: '搜索关键词', 2: '商品ID', 3: '分类名称', 4: '链接地址' } as Record<number, string>)[dialogForm.linkType] || '跳转值'
)

const linkValuePlaceholder = computed(() =>
  ({
    1: '如：手机，点击后跳转搜索结果页',
    2: '如：1，点击后跳转该商品详情页',
    3: '如：数码电器，点击后跳转该分类搜索页',
    4: '如：/search?sort=hot 或 https://...'
  } as Record<number, string>)[dialogForm.linkType] || ''
)

const resetForm = () => {
  Object.assign(dialogForm, {
    title: '',
    imageList: [],
    linkType: 1,
    linkValue: '',
    sortOrder: 0,
    status: 1,
    startTime: null,
    endTime: null
  })
}

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  Object.assign(dialogForm, {
    title: row.title,
    imageList: row.imageUrl ? [row.imageUrl] : [],
    linkType: row.linkType ?? 1,
    linkValue: row.linkValue || '',
    sortOrder: row.sortOrder || 0,
    status: row.status ?? 1,
    startTime: row.startTime || null,
    endTime: row.endTime || null
  })
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定要删除轮播图"${row.title}"吗？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/banner/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '删除失败')
  }
}

const handleToggleStatus = async (row: any) => {
  try {
    await request.put(`/admin/banner/${row.id}/status`)
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
    // 校验时间区间
    if (dialogForm.startTime && dialogForm.endTime && dialogForm.startTime >= dialogForm.endTime) {
      ElMessage.error('下线时间必须晚于上线时间')
      return
    }
    try {
      const payload = {
        title: dialogForm.title,
        imageUrl: dialogForm.imageList[0] || '',
        linkType: dialogForm.linkType,
        linkValue: dialogForm.linkValue,
        sortOrder: dialogForm.sortOrder,
        status: dialogForm.status,
        startTime: dialogForm.startTime || null,
        endTime: dialogForm.endTime || null
      }
      if (dialogType.value === 'add') {
        await request.post('/admin/banner/create', payload)
        ElMessage.success('添加成功')
      } else {
        await request.put(`/admin/banner/${editingId.value}`, payload)
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
