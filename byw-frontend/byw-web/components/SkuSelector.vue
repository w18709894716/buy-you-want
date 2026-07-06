<template>
  <div class="space-y-4">
    <div v-for="(group, index) in specGroups" :key="group.name">
      <div class="text-sm text-gray-600 mb-2">
        {{ group.name }}：<span class="text-gray-800 font-medium">{{ selectedSpecs[index] || '请选择' }}</span>
      </div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="option in group.options"
          :key="option"
          :class="[
            'px-4 py-2 border rounded text-sm transition-all',
            selectedSpecs[index] === option
              ? 'border-primary bg-primary-50 text-primary'
              : isOptionAvailable(index, option)
                ? 'border-gray-200 text-gray-600 hover:border-gray-300'
                : 'border-gray-100 text-gray-300 cursor-not-allowed bg-gray-50'
          ]"
          :disabled="!isOptionAvailable(index, option)"
          @click="selectSpec(index, option)"
        >
          {{ option }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface SpecGroup {
  name: string
  options: string[]
}

interface SkuItem {
  id: number
  specData: Record<string, string>
}

const props = defineProps<{
  specGroups: SpecGroup[]
  skuList: SkuItem[]
}>()

const emit = defineEmits<{
  change: [specs: Record<string, string>]
}>()

const selectedSpecs = ref<Record<number, string>>({})

/**
 * 判断某个规格值是否可选（存在对应的 SKU）
 * 逻辑：假设选中该值，检查是否有 SKU 满足所有已选规格
 */
function isOptionAvailable(groupIndex: number, option: string): boolean {
  // 构建假设选中后的规格组合
  const hypotheticalSelection: Record<string, string> = {}
  props.specGroups.forEach((group, idx) => {
    if (idx === groupIndex) {
      hypotheticalSelection[group.name] = option
    } else if (selectedSpecs.value[idx]) {
      hypotheticalSelection[group.name] = selectedSpecs.value[idx]
    }
  })

  // 检查是否有 SKU 满足条件
  return props.skuList.some(sku => {
    return Object.entries(hypotheticalSelection).every(([specName, specValue]) => {
      return sku.specData[specName] === specValue
    })
  })
}

function selectSpec(groupIndex: number, option: string) {
  // 如果不可选，不响应点击
  if (!isOptionAvailable(groupIndex, option)) return

  if (selectedSpecs.value[groupIndex] === option) {
    delete selectedSpecs.value[groupIndex]
  } else {
    selectedSpecs.value[groupIndex] = option
  }
  selectedSpecs.value = { ...selectedSpecs.value }
  emitChange()
}

function emitChange() {
  const result: Record<string, string> = {}
  props.specGroups.forEach((group, index) => {
    if (selectedSpecs.value[index]) {
      result[group.name] = selectedSpecs.value[index]
    }
  })
  emit('change', result)
}

/** 获取选中的规格字符串 */
function getSpecsString(): string {
  return Object.entries(selectedSpecs.value)
    .map(([index, value]) => `${props.specGroups[Number(index)].name}:${value}`)
    .join(' ')
}

defineExpose({ getSpecsString })
</script>
