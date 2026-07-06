<template>
  <div class="sku-form">
    <!-- 规格组管理 -->
    <div class="spec-groups">
      <el-card v-for="(spec, specIdx) in specs" :key="specIdx" shadow="never" class="spec-card">
        <template #header>
          <div class="spec-card-header">
            <el-input
              v-model="spec.name"
              placeholder="规格名称（如：颜色、尺码、内存）"
              style="width: 260px;"
              @change="generateSkuTable"
            />
            <el-button type="danger" size="small" text @click="removeSpec(specIdx)">
              <el-icon><Delete /></el-icon>删除规格
            </el-button>
          </div>
        </template>

        <div class="spec-values">
          <el-tag
            v-for="(val, valIdx) in spec.values"
            :key="valIdx"
            closable
            size="large"
            @close="removeSpecValue(specIdx, valIdx)"
            class="spec-tag"
          >
            {{ val }}
          </el-tag>
          <el-input
            v-if="spec.inputVisible"
            ref="inputRef"
            v-model="spec.inputValue"
            size="small"
            style="width:100px;"
            @keyup.enter="addSpecValue(specIdx)"
            @blur="addSpecValue(specIdx)"
          />
          <el-button v-else size="small" @click="showSpecInput(specIdx)">
            <el-icon><Plus /></el-icon>添加规格值
          </el-button>
        </div>
      </el-card>
    </div>

    <el-button type="primary" plain @click="addSpec" style="margin-bottom:16px;">
      <el-icon><Plus /></el-icon>添加规格组
    </el-button>

    <!-- SKU 表格 -->
    <template v-if="skuList.length > 0">
      <el-divider>SKU 列表（共 {{ skuList.length }} 个）</el-divider>
      <div style="margin-bottom: 12px;">
        <el-checkbox v-model="autoGenSkuCode" @change="onAutoGenChange">自动生成SKU编码</el-checkbox>
      </div>
      <el-table :data="skuList" stripe border size="small">
        <el-table-column
          v-for="(spec, specIdx) in specs"
          :key="specIdx"
          :label="spec.name || `规格${specIdx + 1}`"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.specs[specIdx] }}
          </template>
        </el-table-column>
        <el-table-column label="价格（元）" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.price" :min="0" :precision="2" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.stock" :min="0" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="SKU编码" width="180">
          <template #default="{ row, $index }">
            <el-input v-if="!autoGenSkuCode" v-model="row.skuCode" size="small" placeholder="可选" />
            <span v-else style="color: #909399; font-size: 12px;">{{ row.skuCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="" width="60" align="center">
          <template #default="{ $index }">
            <el-button type="danger" size="small" text @click="removeSku($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface SpecGroup {
  name: string
  values: string[]
  inputVisible: boolean
  inputValue: string
}

interface SkuItem {
  specs: string[]
  price: number
  stock: number
  skuCode: string
}

const props = defineProps<{
  modelValue?: SkuItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SkuItem[]]
}>()

const specs = ref<SpecGroup[]>([])
const skuList = ref<SkuItem[]>([])
const autoGenSkuCode = ref(true)
let specsBuilt = false

// 自动生成 SKU 编码（基于规格值拼接）
const generateSkuCode = (s: string[]): string => s.join('-') || ''

const onAutoGenChange = () => {
  if (autoGenSkuCode.value) {
    skuList.value.forEach(sku => {
      // 只填充空编码，不覆盖已有的原始编码
      if (!sku.skuCode) {
        sku.skuCode = generateSkuCode(sku.specs)
      }
    })
  }
}

// 从 SKU 数据反推规格组（仅首次）
const buildSpecsFromData = (data: SkuItem[]) => {
  if (specsBuilt || !data.length || !data[0].specs?.length) return
  const specCount = data[0].specs.length
  const newSpecs: SpecGroup[] = []
  for (let i = 0; i < specCount; i++) {
    const values = [...new Set(data.map(s => s.specs[i]).filter(Boolean))] as string[]
    newSpecs.push({ name: `规格${i + 1}`, values, inputVisible: false, inputValue: '' })
  }
  specs.value = newSpecs
  specsBuilt = true
}

// 监听外部数据变化（仅响应引用变化，不响应深层修改）
watch(() => props.modelValue, (val) => {
  if (val && val.length > 0) {
    skuList.value = val
    buildSpecsFromData(val)
  }
})

defineExpose({
  getData: () => skuList.value,
  getSpecNames: () => specs.value.filter(s => s.name && s.values.length > 0).map(s => s.name),
  setSpecNames: (names: string[]) => {
    specs.value = specs.value.map((s, i) => ({
      ...s,
      name: names[i] || s.name
    }))
  }
})

// 添加规格组
const addSpec = () => {
  specs.value.push({ name: '', values: [], inputVisible: false, inputValue: '' })
}

// 删除规格组
const removeSpec = (idx: number) => {
  specs.value.splice(idx, 1)
  generateSkuTable()
}

// 显示规格值输入框
const showSpecInput = (specIdx: number) => {
  specs.value[specIdx].inputVisible = true
}

// 添加规格值
const addSpecValue = (specIdx: number) => {
  const spec = specs.value[specIdx]
  const val = spec.inputValue.trim()
  if (val && !spec.values.includes(val)) {
    spec.values.push(val)
    generateSkuTable()
  }
  spec.inputVisible = false
  spec.inputValue = ''
}

// 删除规格值
const removeSpecValue = (specIdx: number, valIdx: number) => {
  specs.value[specIdx].values.splice(valIdx, 1)
  generateSkuTable()
}

// 删除 SKU 行
const removeSku = (index: number) => {
  skuList.value.splice(index, 1)
  emit('update:modelValue', skuList.value)
}

// 笛卡尔积生成 SKU 表格
const generateSkuTable = () => {
  const validSpecs = specs.value.filter(s => s.name && s.values.length > 0)
  if (validSpecs.length === 0) {
    skuList.value = []
    emit('update:modelValue', [])
    return
  }

  const cartesian = (arrays: string[][]): string[][] => {
    return arrays.reduce<string[][]>(
      (acc, curr) => {
        const result: string[][] = []
        acc.forEach(a => curr.forEach(c => result.push([...a, c])))
        return result
      },
      [[]]
    )
  }

  const valueArrays = validSpecs.map(s => s.values)
  const combinations = cartesian(valueArrays)

  // 保留已有的 price/stock（基于规格组合匹配）
  const oldMap = new Map<string, SkuItem>()
  skuList.value.forEach(sku => {
    oldMap.set(sku.specs.join('|'), sku)
  })

  // 只添加新的组合，不覆盖已删除的
  const newList: SkuItem[] = []
  combinations.forEach(combo => {
    const key = combo.join('|')
    const old = oldMap.get(key)
    // 如果已存在则保留，否则新增
    if (old) {
      newList.push(old)
    } else {
      // 检查是否是新增的组合（不在旧数据中）
      newList.push({
        specs: combo,
        price: 0,
        stock: 0,
        skuCode: autoGenSkuCode.value ? generateSkuCode(combo) : ''
      })
    }
  })

  // 原地更新数组，保持与父组件 formData.skus 的引用一致
  skuList.value.splice(0, skuList.value.length, ...newList)
  emit('update:modelValue', skuList.value)
}
</script>

<style scoped lang="scss">
.sku-form {
  width: 100%;

  .spec-groups {
    margin-bottom: 12px;
  }

  .spec-card {
    margin-bottom: 12px;

    .spec-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .spec-values {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: center;

      .spec-tag {
        margin: 0;
      }
    }
  }
}
</style>
