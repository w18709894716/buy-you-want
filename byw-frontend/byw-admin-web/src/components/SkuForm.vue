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
      <el-table :data="skuList" stripe border size="small">
        <el-table-column
          v-for="(spec, specIdx) in specs"
          :key="specIdx"
          :label="spec.name || `规格${specIdx + 1}`"
          width="140"
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
        <el-table-column label="SKU编码" width="160">
          <template #default="{ row }">
            <el-input v-model="row.skuCode" size="small" placeholder="可选" />
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

  // 保留已有的 price/stock
  const oldMap = new Map<string, SkuItem>()
  skuList.value.forEach(sku => {
    oldMap.set(sku.specs.join('|'), sku)
  })

  skuList.value = combinations.map(combo => {
    const key = combo.join('|')
    const old = oldMap.get(key)
    return {
      specs: combo,
      price: old?.price ?? 0,
      stock: old?.stock ?? 0,
      skuCode: old?.skuCode ?? ''
    }
  })

  emit('update:modelValue', skuList.value)
}

watch(skuList, (val) => {
  emit('update:modelValue', val)
}, { deep: true })

// 从 modelValue 初始化
if (props.modelValue && props.modelValue.length > 0) {
  skuList.value = props.modelValue
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
