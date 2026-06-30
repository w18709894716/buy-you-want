<template>
  <div class="page-container">
    <el-row :gutter="20">
      <!-- 左侧分类树 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>分类树</span>
              <el-button type="primary" size="small" @click="handleAdd(null)">
                <el-icon><Plus /></el-icon>添加一级
              </el-button>
            </div>
          </template>
          <el-tree
            :data="treeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span>{{ data.name }}</span>
                <div class="tree-actions">
                  <el-button type="primary" size="small" text @click.stop="handleAdd(data)">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                  <el-button type="warning" size="small" text @click.stop="handleEdit(data)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button type="danger" size="small" text @click.stop="handleDelete(data)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>

      <!-- 右侧详情 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <span>{{ currentNode ? currentNode.name + ' - 详情' : '请选择分类' }}</span>
          </template>
          <div v-if="currentNode" class="detail-content">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="分类ID">{{ currentNode.id }}</el-descriptions-item>
              <el-descriptions-item label="分类名称">{{ currentNode.name }}</el-descriptions-item>
              <el-descriptions-item label="排序">{{ currentNode.sortOrder || 0 }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentNode.isShow === 1 ? 'success' : 'danger'">
                  {{ currentNode.isShow === 1 ? '启用' : '禁用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="子分类数">
                {{ currentNode.children?.length || 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ currentNode.createdAt }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="请在左侧选择一个分类" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '添加分类' : '编辑分类'"
      width="480px"
    >
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="80px">
        <el-form-item label="上级分类">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="dialogForm.sort" :min="0" :max="9999" />
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
import request from '../../utils/request'

const treeData = ref<any[]>([])

const fetchCategoryTree = async () => {
  try {
    const data: any = await request.get('/admin/product/category/tree')
    flatList.value = data || []
    treeData.value = buildTree(flatList.value)
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取分类列表失败')
  }
}

const buildTree = (list: any[]): any[] => {
  const map: Record<number, any> = {}
  const roots: any[] = []
  list.forEach(item => { item.children = []; map[item.id] = item })
  list.forEach(item => {
    if (item.parentId && item.parentId !== 0 && map[item.parentId]) {
      map[item.parentId].children.push(item)
    } else {
      roots.push(item)
    }
  })
  return roots
}

const flatList = ref<any[]>([])

const findNodeById = (id: number): any | null => {
  return flatList.value.find(item => item.id === id) || null
}

const currentNode = ref<any>(null)
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const parentNode = ref<any>(null)
const dialogFormRef = ref<FormInstance>()

const dialogForm = reactive({ name: '', sort: 0 })
const dialogRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const parentName = computed(() => parentNode.value?.name || '无（一级分类）')

const handleNodeClick = (data: any) => {
  currentNode.value = data
}

const handleAdd = (parent: any) => {
  parentNode.value = parent
  dialogType.value = 'add'
  dialogForm.name = ''
  dialogForm.sort = 0
  dialogVisible.value = true
}

const handleEdit = (data: any) => {
  // 根据 parentId 找到上级分类并展示
  parentNode.value = data.parentId ? findNodeById(data.parentId) : null
  dialogType.value = 'edit'
  dialogForm.name = data.name
  dialogForm.sort = data.sortOrder || 0
  currentNode.value = data
  dialogVisible.value = true
}

const handleDelete = async (data: any) => {
  await ElMessageBox.confirm(`确定要删除分类"${data.name}"吗？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/product/category/${data.id}`)
    ElMessage.success('删除成功')
    fetchCategoryTree()
    if (currentNode.value?.id === data.id) currentNode.value = null
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '删除失败')
  }
}

const submitForm = async () => {
  if (!dialogFormRef.value) return
  await dialogFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const payload = {
        name: dialogForm.name,
        parentId: parentNode.value?.id || 0,
        sortOrder: dialogForm.sort
      }
      if (dialogType.value === 'add') {
        await request.post('/admin/product/category/create', payload)
        ElMessage.success('添加成功')
      } else {
        await request.put(`/admin/product/category/${currentNode.value.id}`, payload)
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      fetchCategoryTree()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '操作失败')
    }
  })
}

onMounted(fetchCategoryTree)
</script>

<style scoped lang="scss">
.page-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .tree-node {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding-right: 8px;

    .tree-actions {
      display: none;
    }

    &:hover .tree-actions {
      display: flex;
      gap: 4px;
    }
  }

  .detail-content {
    padding: 8px 0;
  }
}
</style>
