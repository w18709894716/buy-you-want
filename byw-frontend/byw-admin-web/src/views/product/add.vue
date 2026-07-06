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
          <el-col :span="24">
            <el-form-item label="副标题" prop="subtitle">
              <el-input v-model="formData.subtitle" placeholder="请输入商品副标题" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="品牌" prop="brandId">
              <el-select v-model="formData.brandId" placeholder="请选择品牌" style="width:100%">
                <el-option v-for="brand in brandOptions" :key="brand.id" :label="brand.name" :value="brand.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品状态" prop="status">
              <el-radio-group v-model="formData.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="2">下架</el-radio>
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
          <SkuForm ref="skuFormRef" v-model="formData.skus" />
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'
import request from '../../utils/request'
import ImageUpload from '../../components/ImageUpload.vue'
import RichEditor from '../../components/RichEditor.vue'
import SkuForm from '../../components/SkuForm.vue'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const skuFormRef = ref<InstanceType<typeof SkuForm>>()
const submitting = ref(false)
const loading = ref(false)
const specNames = ref<string[]>([])

const isEdit = computed(() => !!route.params.id)

const formData = reactive({
  name: '',
  subtitle: '',
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

const categoryOptions = ref<any[]>([])
const brandOptions = ref<any[]>([])

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

const loadCategories = async () => {
  try {
    const data: any = await request.get('/admin/product/category/tree')
    categoryOptions.value = buildTree(data || [])
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取分类失败')
  }
}

const loadBrands = async () => {
  try {
    const data: any = await request.get('/admin/product/brand/list')
    brandOptions.value = data || []
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取品牌失败')
  }
}

// 在分类树中查找完整路径
const findCategoryPath = (tree: any[], targetId: number, path: number[] = []): number[] | null => {
  for (const node of tree) {
    const currentPath = [...path, node.id]
    if (node.id === targetId) return currentPath
    if (node.children && node.children.length > 0) {
      const result = findCategoryPath(node.children, targetId, currentPath)
      if (result) return result
    }
  }
  return null
}

// 编辑模式加载数据
const loadProduct = async () => {
  if (!route.params.id) return
  loading.value = true
  try {
    const data: any = await request.get(`/admin/product/${route.params.id}`)
    // 转换 categoryId 为完整路径（cascader 需要 [parentId, childId]）
    const path = findCategoryPath(categoryOptions.value, data.categoryId)
    formData.categoryId = path || (data.categoryId ? [data.categoryId] : [])
    formData.name = data.name || ''
    formData.subtitle = data.subtitle || ''
    formData.brandId = data.brandId
    formData.status = data.status ?? 1
    // 转换图片：mainImage + subImages -> images 数组
    const images: string[] = []
    if (data.mainImage) images.push(data.mainImage)
    if (data.subImages) {
      data.subImages.split(',').forEach((s: string) => s.trim() && images.push(s.trim()))
    }
    formData.images = images
    formData.description = data.detailHtml || ''
    // 加载 SKU
    if (data.skus && data.skus.length > 0) {
      // 从所有 SKU 的 specData 合并提取规格组名称（避免单个 SKU 数据不一致）
      const specNameSet = new Set<string>()
      const skus = data.skus.map((sku: any) => {
        let specs: string[] = []
        try {
          const parsed = JSON.parse(sku.specData || '{}')
          Object.keys(parsed).forEach(k => {
            // 过滤掉脏数据 key（如 spec1、spec2）
            if (k && !/^spec\d+$/.test(k)) specNameSet.add(k)
          })
          specs = Object.values(parsed) as string[]
        } catch (e) { /* ignore */ }
        return {
          specs,
          price: sku.price || 0,
          stock: sku.stock || 0,
          skuCode: sku.skuCode || ''
        }
      })
      const specNames = [...specNameSet]
      formData.skus = skus
      specNames.value = specNames
      // 等 SkuForm 渲染后设置规格组名称
      await nextTick()
      skuFormRef.value?.setSpecNames(specNames)
    } else {
      formData.skus = []
    }
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '加载商品信息失败')
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
      // 构建提交数据：categoryId 从数组取最后一个值
      const payload = {
        ...formData,
        categoryId: Array.isArray(formData.categoryId)
          ? formData.categoryId[formData.categoryId.length - 1]
          : formData.categoryId,
        // 图片：第一张为主图，其余为副图
        mainImage: formData.images[0] || '',
        subImages: formData.images.slice(1).join(','),
        detailHtml: formData.description,
        // SKU：从 SkuForm 获取数据，用实时规格组名称作 specData 的 key
        skus: (() => {
          const names = skuFormRef.value?.getSpecNames() || specNames.value
          return (skuFormRef.value?.getData() || formData.skus).map(sku => ({
            skuCode: sku.skuCode || '',
            price: sku.price || 0,
            stock: sku.stock || 0,
            specData: JSON.stringify(
              Object.fromEntries((sku.specs || []).map((v: string, i: number) => [names[i] || `规格${i + 1}`, v]))
            )
          }))
        })()
      }
      if (isEdit.value) {
        await request.put(`/admin/product/${route.params.id}`, payload)
        ElMessage.success('修改成功')
      } else {
        await request.post('/admin/product', payload)
        ElMessage.success('添加成功')
      }
      // 保持当前页码（编辑场景）
      const returnPage = route.query.page
      router.push(returnPage ? { path: '/product/list', query: { page: returnPage } } : '/product/list')
    } catch (error: any) {
      if (!error._handled) ElMessage.error(error?.message || (isEdit.value ? '修改失败' : '添加失败'))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadBrands()])
  await loadProduct()
})
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
