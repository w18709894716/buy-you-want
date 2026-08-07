<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>技能组管理</span>
          <el-button type="primary" @click="openCreate">新增技能组</el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column prop="groupName" label="技能组名称" width="140" />
        <el-table-column prop="keywords" label="路由关键词" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="kw in (row.keywords || '').split(',').filter(Boolean)" :key="kw" size="small" class="mr-1 mb-1">{{ kw }}</el-tag>
            <span v-if="!row.keywords" class="text-gray-400 text-xs">未设置</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="优先级" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑技能组' : '新增技能组'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="技能组名称" prop="groupName">
          <el-input v-model="form.groupName" placeholder="如：售前、售后、物流" maxlength="50" />
        </el-form-item>
        <el-form-item label="路由关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="逗号分隔，如：发货,物流,快递" maxlength="200" />
          <div class="text-xs text-gray-400 mt-1">用户消息首句命中关键词即路由到该组</div>
        </el-form-item>
        <el-form-item label="优先级" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="99" />
          <div class="text-xs text-gray-400 mt-1">数字越小越优先匹配</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../../utils/request'

interface SkillGroup {
  id: number
  shopId: number
  groupName: string
  keywords?: string
  sort: number
  status: number
}

const loading = ref(false)
const tableData = ref<SkillGroup[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)
const formRef = ref<any>(null)

const form = ref<SkillGroup>({
  id: 0,
  shopId: 0,
  groupName: '',
  keywords: '',
  sort: 0,
  status: 1,
})

const rules = {
  groupName: [{ required: true, message: '请输入技能组名称', trigger: 'blur' }],
}

async function loadList() {
  loading.value = true
  try {
    const data: SkillGroup[] = await request.get('/im/skill-group/list')
    tableData.value = data || []
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function openCreate() {
  editId.value = null
  form.value = { id: 0, shopId: 0, groupName: '', keywords: '', sort: 0, status: 1 }
  dialogVisible.value = true
}

function openEdit(row: SkillGroup) {
  editId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

async function doSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editId.value) {
      await request.put('/im/skill-group', form.value)
    } else {
      await request.post('/im/skill-group', form.value)
    }
    dialogVisible.value = false
    loadList()
  } catch { /* ignore */ } finally {
    submitting.value = false
  }
}

async function doDelete(row: SkillGroup) {
  await request.delete(`/im/skill-group/${row.id}`)
  loadList()
}

onMounted(() => loadList())
</script>

<style scoped>
.page-container { padding: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>