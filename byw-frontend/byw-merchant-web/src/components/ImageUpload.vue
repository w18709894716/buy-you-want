<template>
  <el-upload
    class="image-upload"
    :class="{ 'is-full': isFull }"
    :action="uploadAction"
    :headers="headers"
    :file-list="fileList"
    :limit="limit"
    :on-success="handleSuccess"
    :on-remove="handleRemove"
    :on-exceed="handleExceed"
    :before-upload="beforeUpload"
    list-type="picture-card"
    accept="image/*"
  >
    <el-icon :size="28"><Plus /></el-icon>

    <template #file="{ file }">
      <img class="el-upload-list__item-image" :src="file.url" fit="cover" />
      <span class="el-upload-list__item-actions">
        <span class="el-upload-list__item-preview" @click="handlePreview(file)">
          <el-icon><ZoomIn /></el-icon>
        </span>
        <span class="el-upload-list__item-delete" @click="handleRemove(file)">
          <el-icon><Delete /></el-icon>
        </span>
      </span>
    </template>
  </el-upload>

  <!-- 图片预览 -->
  <el-dialog v-model="previewVisible" title="图片预览" width="600px">
    <img :src="previewUrl" style="width:100%;" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'

const props = withDefaults(defineProps<{
  modelValue?: string[]
  limit?: number
  folder?: string
  /** 上传接口地址，默认商家BFF；免登录场景（如入驻申请）可指向公开的 /api/file/upload */
  action?: string
}>(), {
  modelValue: () => [],
  limit: 6,
  folder: 'default',
  action: '/api/merchant/upload'
})

const uploadAction = computed(() => `${props.action}?folder=${props.folder}`)

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const headers = computed(() => {
  const token = localStorage.getItem('merchant_token')
  return token ? { Authorization: `Bearer ${token}` } : {}
})

const fileList = ref<UploadUserFile[]>(
  props.modelValue.map(url => ({ name: '', url }))
)

// 已达上限时隐藏上传触发框（el-upload 默认只禁用点击、不隐藏）
const isFull = computed(() => fileList.value.length >= props.limit)

watch(() => props.modelValue, (val) => {
  fileList.value = val.map(url => ({ name: '', url }))
})

const previewVisible = ref(false)
const previewUrl = ref('')

const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const handleSuccess = (response: any, _file: UploadFile) => {
  // R<String> 响应: { code: 200, data: "url", message: "ok" }
  const url = typeof response.data === 'string'
    ? response.data
    : response.data?.url || response.url || ''
  if (!url) {
    ElMessage.error('上传成功但未获取到文件地址')
    return
  }
  const urls = fileList.value.map(f => f.url || '').filter(Boolean)
  urls.push(url)
  emit('update:modelValue', urls)
}

const handleRemove = (file: UploadFile | UploadUserFile) => {
  const url = (file as any).url
  const urls = fileList.value.map(f => (f as any).url).filter(u => u !== url)
  emit('update:modelValue', urls)
}

const handleExceed = () => {
  ElMessage.warning(`最多只能上传 ${props.limit} 张图片`)
}

const handlePreview = (file: UploadFile | UploadUserFile) => {
  previewUrl.value = (file as any).url || ''
  previewVisible.value = true
}
</script>

<style scoped lang="scss">
.image-upload {
  :deep(.el-upload--picture-card) {
    width: 100px;
    height: 100px;
  }

  // 满额后隐藏“+”触发框
  &.is-full :deep(.el-upload--picture-card) {
    display: none;
  }

  :deep(.el-upload-list--picture-card .el-upload-list__item) {
    width: 100px;
    height: 100px;
  }
}
</style>
