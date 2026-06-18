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
              <el-descriptions-item label="排序">{{ currentNode.sort || 0 }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag type="success">启用</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="子分类数">
                {{ currentNode.children?.length || 0 }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">2025-01-01 00:00:00</el-descriptions-item>
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'

const treeData = ref<any[]>([
  {
    id: 1, name: '数码电子', sort: 1, children: [
      { id: 11, name: '手机', sort: 1, children: [] },
      { id: 12, name: '耳机', sort: 2, children: [] },
      { id: 13, name: '电脑', sort: 3, children: [] }
    ]
  },
  {
    id: 2, name: '服装鞋帽', sort: 2, children: [
      { id: 21, name: '运动鞋', sort: 1, children: [] },
      { id: 22, name: 'T恤', sort: 2, children: [] }
    ]
  },
  {
    id: 3, name: '食品饮料', sort: 3, children: [
      { id: 31, name: '零食', sort: 1, children: [] },
      { id: 32, name: '饮料', sort: 2, children: [] }
    ]
  },
  { id: 4, name: '家居家装', sort: 4, children: [] }
])

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
  parentNode.value = null
  dialogType.value = 'edit'
  dialogForm.name = data.name
  dialogForm.sort = data.sort || 0
  currentNode.value = data
  dialogVisible.value = true
}

const handleDelete = async (data: any) => {
  await ElMessageBox.confirm(`确定要删除分类"${data.name}"吗？`, '提示', { type: 'warning' })
  // 从树中删除节点
  const removeFromTree = (nodes: any[]): boolean => {
    const idx = nodes.findIndex(n => n.id === data.id)
    if (idx >= 0) { nodes.splice(idx, 1); return true }
    return nodes.some(n => n.children && removeFromTree(n.children))
  }
  removeFromTree(treeData.value)
  ElMessage.success('删除成功')
  if (currentNode.value?.id === data.id) currentNode.value = null
}

const submitForm = async () => {
  if (!dialogFormRef.value) return
  await dialogFormRef.value.validate((valid) => {
    if (!valid) return
    if (dialogType.value === 'add') {
      const newNode = { id: Date.now(), name: dialogForm.name, sort: dialogForm.sort, children: [] }
      if (parentNode.value) {
        if (!parentNode.value.children) parentNode.value.children = []
        parentNode.value.children.push(newNode)
      } else {
        treeData.value.push(newNode)
      }
      ElMessage.success('添加成功')
    } else {
      if (currentNode.value) {
        currentNode.value.name = dialogForm.name
        currentNode.value.sort = dialogForm.sort
      }
      ElMessage.success('修改成功')
    }
    dialogVisible.value = false
  })
}
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
