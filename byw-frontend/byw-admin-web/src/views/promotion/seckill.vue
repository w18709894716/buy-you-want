<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>秒杀活动管理</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>创建活动
          </el-button>
        </div>
      </template>

      <el-table :data="tableData" v-loading="loading" stripe border @expand-change="onExpandChange">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap">
              <el-table :data="row.items || []" size="small" border>
                <el-table-column label="商品" min-width="180">
                  <template #default="{ row: item }">{{ productName(item.productId) }}</template>
                </el-table-column>
                <el-table-column label="规格" min-width="140">
                  <template #default="{ row: item }">{{ skuName(item.productId, item.skuId) }}</template>
                </el-table-column>
                <el-table-column label="秒杀价" width="100">
                  <template #default="{ row: item }">
                    <span style="color:#F56C6C;font-weight:600;">¥{{ item.seckillPrice }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="库存" width="110">
                  <template #default="{ row: item }">{{ item.availableStock }} / {{ item.totalStock }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="活动名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="商品数" width="90">
          <template #default="{ row }">{{ row.items?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="活动时间" min-width="220">
          <template #default="{ row }">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
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

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '创建秒杀活动' : '编辑秒杀活动'"
      width="880px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" placeholder="如：618限时秒杀专场" />
        </el-form-item>

        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:100%"
          />
        </el-form-item>

        <el-form-item label="活动商品" required>
          <div class="items-wrap">
            <div class="items-head">
              <span class="col-product">商品</span>
              <span class="col-sku">规格</span>
              <span class="col-price">秒杀价(元)</span>
              <span class="col-stock">秒杀库存(件)</span>
              <span class="col-op">操作</span>
            </div>

            <div v-if="!form.items.length" class="items-empty">
              尚未添加商品，请点击下方“添加商品”按钮
            </div>

            <div v-for="(item, index) in form.items" :key="index" class="item-row">
              <div class="col-product prod-cell">
                <el-image :src="item.mainImage" fit="cover" class="prod-thumb">
                  <template #error><div class="thumb-err">无图</div></template>
                </el-image>
                <span class="prod-name" :title="item.productName">{{ item.productName }}</span>
              </div>
              <span class="col-sku sku-name" :title="item.skuName">{{ item.skuName }}</span>
              <div class="col-price">
                <el-input-number
                  v-model="item.seckillPrice"
                  :min="0.01"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  style="width:130px"
                />
              </div>
              <div class="col-stock">
                <el-input-number
                  v-model="item.totalStock"
                  :min="1"
                  :max="itemMaxStock(item)"
                  :step="10"
                  controls-position="right"
                  style="width:120px"
                />
                <span class="stock-hint">可用 {{ item.skuStock }}</span>
              </div>
              <div class="col-op">
                <el-button type="danger" text @click="removeItem(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="items-footer">
              <el-button
                type="primary"
                plain
                size="small"
                :disabled="form.items.length >= MAX_ITEMS"
                @click="openPicker"
              >
                <el-icon><Plus /></el-icon>添加商品
              </el-button>
              <span class="items-count">{{ form.items.length }} / {{ MAX_ITEMS }} 个商品</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 商品选择弹框 -->
    <el-dialog
      v-model="pickerVisible"
      title="选择秒杀商品"
      width="640px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="80px">
        <el-form-item label="选择商品">
          <el-select
            v-model="pickerProductId"
            filterable
            placeholder="请选择商品"
            style="width:100%"
            :loading="productLoading"
            @change="onPickProduct"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-if="pickerProduct" class="sku-picker">
        <div class="picker-product">
          <el-image :src="pickerProduct.mainImage" fit="cover" class="picker-img">
            <template #error><div class="thumb-err">无图</div></template>
          </el-image>
          <div class="picker-info">
            <div class="picker-name">{{ pickerProduct.name }}</div>
            <div class="picker-meta">
              最低价：<span class="picker-price">{{ pickerProduct.minPrice != null ? '¥' + pickerProduct.minPrice : '-' }}</span>
            </div>
          </div>
        </div>

        <div class="sku-picker-title">商品规格：</div>
        <el-table :data="pickerSkus" size="small" border v-loading="pickerSkuLoading">
          <el-table-column prop="skuName" label="规格" min-width="150" show-overflow-tooltip />
          <el-table-column label="售价" width="100">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column label="库存" width="90">
            <template #default="{ row }">
              <span :class="{ 'no-stock': (row.stock || 0) <= 0 }">{{ row.stock || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button
                type="primary"
                size="small"
                text
                :disabled="(row.stock || 0) <= 0 || isSkuAdded(row.id)"
                @click="confirmAddSku(row)"
              >
                {{ (row.stock || 0) <= 0 ? '无库存' : (isSkuAdded(row.id) ? '已添加' : '添加') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="pickerVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import request from '../../utils/request'

/** 每个活动最多可配置的商品数（与后端 SeckillService.MAX_ITEMS_PER_ACTIVITY 保持一致） */
const MAX_ITEMS = 50

const loading = ref(false)
const tableData = ref<any[]>([])

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '未开始', type: 'info' },
  1: { label: '进行中', type: 'success' },
  2: { label: '已结束', type: 'warning' }
}
const statusLabel = (s: number) => statusMap[s]?.label || s
const statusType = (s: number) => (statusMap[s]?.type as any) || 'info'

const formatTime = (t: string) => t ? t.replace('T', ' ').substring(0, 16) : '-'

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/promotion/seckill/list')
    tableData.value = data || []
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取秒杀活动列表失败')
  } finally {
    loading.value = false
  }
}

// ========== 商品数据预加载 ==========
const productLoading = ref(false)
const productOptions = ref<any[]>([])
// productId -> 商品详情（含 skus），用于列表展开与表单 SKU 名称展示
const productDetailCache = reactive<Record<number, any>>({})

const loadProducts = async () => {
  if (productOptions.value.length) return
  productLoading.value = true
  try {
    const res: any = await request.get('/admin/product/list', { pageSize: 100 })
    productOptions.value = res?.list || []
  } catch {
    productOptions.value = []
  } finally {
    productLoading.value = false
  }
}

const loadProductDetail = async (productId: number) => {
  if (productDetailCache[productId]) return productDetailCache[productId]
  try {
    const product: any = await request.get(`/admin/product/${productId}`)
    if (product) productDetailCache[productId] = product
    return product
  } catch {
    return null
  }
}

const productName = (productId: number) => {
  const p = productDetailCache[productId] || productOptions.value.find((x: any) => x.id === productId)
  return p?.name || `商品 ${productId}`
}

const skuName = (productId: number, skuId: number) => {
  const skus = productDetailCache[productId]?.skus || []
  const sku = skus.find((s: any) => s.id === skuId)
  return sku?.skuName || `SKU ${skuId}`
}

// 展开行时补齐商品/SKU名称
const onExpandChange = (row: any, expandedRows: any[]) => {
  if (!expandedRows.includes(row)) return
  loadProducts()
  for (const item of row.items || []) {
    loadProductDetail(item.productId)
  }
}

// ========== 创建/编辑弹窗 ==========
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit'>('add')
const formRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const submitting = ref(false)

interface ItemRow {
  productId: number
  skuId: number
  seckillPrice: number
  totalStock: number
  skuStock: number
  skuName: string
  productName: string
  mainImage: string
}

const form = reactive({
  name: '',
  timeRange: [] as string[],
  items: [] as ItemRow[]
})

const rules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  timeRange: [{ required: true, message: '请选择活动时间', trigger: 'change' }]
}

const resetForm = () => {
  Object.assign(form, { name: '', timeRange: [], items: [] as ItemRow[] })
}

const removeItem = (index: number) => {
  form.items.splice(index, 1)
}

// 库存输入上限：取 SKU 库存与已填写秒杀库存的较大值，且不低于 1（避免 min>max 报错）
const itemMaxStock = (item: ItemRow) => Math.max(item.skuStock || 0, item.totalStock || 0, 1)

// ========== 商品选择弹框 ==========
const pickerVisible = ref(false)
const pickerProductId = ref<number | null>(null)
const pickerProduct = ref<any>(null)
const pickerSkus = ref<any[]>([])
const pickerSkuLoading = ref(false)

const openPicker = async () => {
  if (form.items.length >= MAX_ITEMS) {
    ElMessage.warning(`每个活动最多配置 ${MAX_ITEMS} 个商品`)
    return
  }
  pickerProductId.value = null
  pickerProduct.value = null
  pickerSkus.value = []
  pickerVisible.value = true
  await loadProducts()
}

const onPickProduct = async (productId: number) => {
  const row = productOptions.value.find((p: any) => p.id === productId)
  if (!row) return
  pickerProduct.value = row
  pickerSkus.value = []
  pickerSkuLoading.value = true
  const product = await loadProductDetail(row.id)
  pickerSkus.value = product?.skus || []
  pickerSkuLoading.value = false
}

const isSkuAdded = (skuId: number) => form.items.some(i => i.skuId === skuId)

const confirmAddSku = (sku: any) => {
  if (form.items.length >= MAX_ITEMS) {
    ElMessage.warning(`每个活动最多配置 ${MAX_ITEMS} 个商品`)
    return
  }
  if ((sku.stock || 0) <= 0) {
    ElMessage.warning('该规格已无库存')
    return
  }
  if (isSkuAdded(sku.id)) {
    ElMessage.warning('该规格已添加')
    return
  }
  form.items.push({
    productId: pickerProduct.value.id,
    skuId: sku.id,
    seckillPrice: Math.round(sku.price * 0.8 * 100) / 100,
    totalStock: Math.min(100, sku.stock),
    skuStock: sku.stock,
    skuName: sku.skuName,
    productName: pickerProduct.value.name,
    mainImage: pickerProduct.value.mainImage
  })
  ElMessage.success(`已添加 ${sku.skuName}`)
}

const handleAdd = () => {
  dialogType.value = 'add'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  loadProducts()
}

const handleEdit = async (row: any) => {
  dialogType.value = 'edit'
  editingId.value = row.id
  resetForm()
  loadProducts()
  try {
    const detail: any = await request.get(`/admin/promotion/seckill/${row.id}`)
    if (detail) {
      form.name = detail.name || ''
      form.timeRange = [detail.startTime, detail.endTime]
      const items: ItemRow[] = []
      for (const it of detail.items || []) {
        const product = await loadProductDetail(it.productId)
        const sku = (product?.skus || []).find((s: any) => s.id === it.skuId)
        items.push({
          productId: it.productId,
          skuId: it.skuId,
          seckillPrice: it.seckillPrice,
          totalStock: it.totalStock,
          skuStock: sku?.stock ?? it.totalStock,
          skuName: sku?.skuName || `SKU ${it.skuId}`,
          productName: product?.name || `商品 ${it.productId}`,
          mainImage: product?.mainImage || ''
        })
      }
      form.items = items
    }
  } catch (e: any) {
    if (!e._handled) ElMessage.error('获取活动详情失败')
  }
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除活动"${row.name}"？`, '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/promotion/seckill/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '删除失败')
  }
}

const validateItems = (): boolean => {
  if (!form.items.length) {
    ElMessage.error('请至少配置一个秒杀商品')
    return false
  }
  const skuIds = new Set<number>()
  for (let i = 0; i < form.items.length; i++) {
    const item = form.items[i]
    if (!item.seckillPrice || item.seckillPrice <= 0) {
      ElMessage.error(`第 ${i + 1} 行：请输入秒杀价格`)
      return false
    }
    if (!item.totalStock || item.totalStock <= 0) {
      ElMessage.error(`第 ${i + 1} 行：请输入秒杀库存`)
      return false
    }
    if (item.totalStock > item.skuStock) {
      ElMessage.error(`第 ${i + 1} 行：秒杀库存不能超过可用库存 ${item.skuStock}`)
      return false
    }
    if (skuIds.has(item.skuId)) {
      ElMessage.error(`第 ${i + 1} 行：同一规格商品不能重复配置`)
      return false
    }
    skuIds.add(item.skuId)
  }
  return true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validateItems()) return
    submitting.value = true
    try {
      const payload = {
        name: form.name,
        startTime: form.timeRange[0],
        endTime: form.timeRange[1],
        items: form.items.map(item => ({
          productId: item.productId,
          skuId: item.skuId,
          seckillPrice: item.seckillPrice,
          totalStock: item.totalStock
        }))
      }
      if (dialogType.value === 'add') {
        await request.post('/admin/promotion/seckill', payload)
        ElMessage.success('创建成功')
      } else {
        await request.put(`/admin/promotion/seckill/${editingId.value}`, payload)
        ElMessage.success('修改成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-container {
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
}
.expand-wrap {
  padding: 8px 40px;
}
.prod-thumb {
  width: 44px;
  height: 44px;
  border-radius: 4px;
  flex-shrink: 0;
  .thumb-err {
    width: 44px;
    height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #bbb;
    background: #f5f5f5;
  }
}
.no-stock {
  color: #f56c6c;
}
.items-wrap {
  width: 100%;
  .items-head {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 4px 8px;
    font-size: 13px;
    color: #909399;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 8px;
  }
  .items-empty {
    padding: 16px 0;
    text-align: center;
    color: #c0c4cc;
    font-size: 13px;
  }
  .item-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
  }
  .col-product { width: 240px; }
  .col-sku { width: 130px; }
  .col-price { width: 150px; }
  .col-stock { width: 190px; }
  .col-op { width: 50px; text-align: center; }

  .prod-cell {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .prod-name {
    font-size: 13px;
    line-height: 1.3;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }
  .sku-name {
    font-size: 13px;
    color: #606266;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .col-stock {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .stock-hint {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
  }
  .items-footer {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 4px;
  }
  .items-count {
    font-size: 12px;
    color: #999;
  }
}
.sku-picker {
  margin-top: 16px;
  .picker-product {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px;
    background: #f7f8fa;
    border-radius: 6px;
    margin-bottom: 12px;
    .picker-img {
      width: 60px;
      height: 60px;
      border-radius: 4px;
      flex-shrink: 0;
    }
    .picker-info {
      overflow: hidden;
    }
    .picker-name {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 6px;
    }
    .picker-meta {
      font-size: 13px;
      color: #909399;
      .picker-price {
        color: #f56c6c;
        font-weight: 600;
      }
      .picker-gap {
        margin-left: 16px;
      }
    }
  }
  .sku-picker-title {
    font-size: 13px;
    color: #303133;
    margin-bottom: 8px;
    font-weight: 600;
  }
}
</style>
