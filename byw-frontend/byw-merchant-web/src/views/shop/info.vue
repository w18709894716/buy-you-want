<template>
  <div class="page-container">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>店铺设置</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
        class="shop-form"
      >
        <el-form-item label="店铺名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入店铺名称" />
        </el-form-item>

        <el-form-item label="店铺Logo">
          <ImageUpload v-model="logoList" :limit="1" folder="shop" />
        </el-form-item>

        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="formData.contactName" placeholder="请输入联系人" />
        </el-form-item>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="formData.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="店铺简介" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请输入店铺简介"
          />
        </el-form-item>

        <el-form-item label="经营类型">
          <el-tag :type="formData.selfOperated === 0 ? 'success' : 'primary'">
            {{ formData.selfOperated === 0 ? '自营' : '第三方商家' }}
          </el-tag>
        </el-form-item>

        <el-form-item label="店铺状态">
          <el-tag :type="statusType(formData.status)">{{ statusLabel(formData.status) }}</el-tag>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
          <el-button @click="fetchShop">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, FormInstance } from 'element-plus'
import request from '../../utils/request'
import ImageUpload from '../../components/ImageUpload.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)

const formData = reactive({
  name: '',
  logo: '',
  description: '',
  contactName: '',
  contactPhone: '',
  selfOperated: 1 as number | null,
  status: 1 as number | null
})

const logoList = computed<string[]>({
  get: () => (formData.logo ? [formData.logo] : []),
  set: (val) => { formData.logo = val[0] || '' }
})

const rules = {
  name: [{ required: true, message: '请输入店铺名称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }]
}

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '关店', type: 'info' },
  1: { label: '营业中', type: 'success' },
  2: { label: '封禁', type: 'danger' }
}
const statusLabel = (s: number | null) => (s != null && statusMap[s]?.label) || '未知'
const statusType = (s: number | null) => (s != null && statusMap[s]?.type || 'info') as any

const fetchShop = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/merchant/shop/info')
    if (data) {
      formData.name = data.name || ''
      formData.logo = data.logo || ''
      formData.description = data.description || ''
      formData.contactName = data.contactName || ''
      formData.contactPhone = data.contactPhone || ''
      formData.selfOperated = data.selfOperated ?? 1
      formData.status = data.status ?? 1
    }
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取店铺信息失败')
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.put('/merchant/shop/info', {
        name: formData.name,
        logo: formData.logo,
        description: formData.description,
        contactName: formData.contactName,
        contactPhone: formData.contactPhone
      })
      ElMessage.success('保存成功')
      fetchShop()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '保存失败')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(fetchShop)
</script>

<style scoped lang="scss">
.page-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .shop-form {
    max-width: 640px;
  }
}
</style>
