<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑商品' : '添加商品' }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="100px"
        class="product-form"
      >
        <!-- 基本信息 -->
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="商品名称" prop="name">
              <el-input v-model="formData.name" placeholder="请输入商品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品分类" prop="categoryId">
              <el-cascader
                v-model="formData.categoryId"
                :options="categoryOptions"
                :props="{ value: 'id', label: 'name', children: 'children' }"
                placeholder="请选择分类"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="品牌" prop="brandId">
              <el-select v-model="formData.brandId" placeholder="请选择品牌" style="width:100%">
                <el-option label="Apple" :value="1" />
                <el-option label="Sony" :value="2" />
                <el-option label="Nike" :value="3" />
                <el-option label="小米" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品状态" prop="status">
              <el-radio-group v-model="formData.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 商品图片 -->
        <el-divider content-position="left">商品图片</el-divider>
        <el-form-item label="主图">
          <ImageUpload v-model="formData.images" :limit="6" />
        </el-form-item>

        <!-- 商品描述 -->
        <el-divider content-position="left">商品描述</el-divider>
        <el-form-item label="详情描述">
          <RichEditor v-model="formData.description" />
        </el-form-item>

        <!-- SKU 规格 -->
        <el-divider content-position="left">SKU 规格</el-divider>
        <el-form-item label="规格配置">
          <SkuForm v-model="formData.skus" />
        </el-form-item>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '提交商品' }}
          </el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'
import request from '../../utils/request'
import ImageUpload from '../../components/ImageUpload.vue'
import RichEditor from '../../components/RichEditor.vue'
import SkuForm from '../../components/SkuForm.vue'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const formData = reactive({
  name: '',
  categoryId: [] as number[],
  brandId: undefined as number | undefined,
  status: 1,
  images: [] as string[],
  description: '',
  skus: [] as any[]
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  brandId: [{ required: true, message: '请选择品牌', trigger: 'change' }]
}

const categoryOptions = [
  { id: 1, name: '数码电子', children: [
    { id: 11, name: '手机' }, { id: 12, name: '耳机' }, { id: 13, name: '电脑' }
  ]},
  { id: 2, name: '服装鞋帽', children: [
    { id: 21, name: '运动鞋' }, { id: 22, name: 'T恤' }, { id: 23, name: '外套' }
  ]},
  { id: 3, name: '食品饮料', children: [
    { id: 31, name: '零食' }, { id: 32, name: '饮料' }
  ]},
  { id: 4, name: '家居家装', children: [
    { id: 41, name: '家具' }, { id: 42, name: '装饰' }
  ]}
]

// 编辑模式加载数据
const loadProduct = async () => {
  if (!route.params.id) return
  try {
    const data: any = await request.get(`/admin/product/${route.params.id}`)
    Object.assign(formData, data)
  } catch {
    // 编辑模式 mock 数据
    formData.name = 'Apple iPhone 15 Pro Max'
    formData.brandId = 1
    formData.status = 1
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await request.put(`/admin/product/${route.params.id}`, formData)
        ElMessage.success('修改成功')
      } else {
        await request.post('/admin/product', formData)
        ElMessage.success('添加成功')
      }
      router.push('/product/list')
    } catch {
      ElMessage.success(isEdit.value ? '修改成功（mock）' : '添加成功（mock）')
      router.push('/product/list')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(loadProduct)
</script>

<style scoped lang="scss">
.page-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .product-form {
    max-width: 900px;
  }
}
</style>
