<template>
  <div class="space-y-4">
    <div v-for="(group, index) in specGroups" :key="group.name">
      <div class="text-sm text-gray-600 mb-2">{{ group.name }}：<span class="text-gray-800 font-medium">{{ selectedSpecs[index] || '请选择' }}</span></div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="option in group.options"
          :key="option"
          :class="[
            'px-4 py-2 border rounded text-sm transition-all',
            selectedSpecs[index] === option
              ? 'border-primary bg-primary-50 text-primary'
              : 'border-gray-200 text-gray-600 hover:border-gray-300'
          ]"
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

const props = defineProps<{
  specGroups: SpecGroup[]
}>()

const emit = defineEmits<{
  change: [specs: Record<string, string>]
}>()

const selectedSpecs = ref<Record<number, string>>({})

function selectSpec(groupIndex: number, option: string) {
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
