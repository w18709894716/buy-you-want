<template>
  <div class="grid grid-cols-3 gap-2">
    <div>
      <label class="block text-sm text-gray-600 mb-1">省</label>
      <select
        :value="province"
        class="w-full h-10 px-2 border rounded-lg text-sm bg-white focus:outline-none focus:border-primary"
        :class="province ? 'text-gray-800' : 'text-gray-400'"
        @change="onProvinceChange(($event.target as HTMLSelectElement).value)"
      >
        <option value="" disabled>请选择</option>
        <!-- 历史数据不在区划表中时兜底保留显示 -->
        <option v-if="province && !provinceOptions.some(p => p.label === province)" :value="province">{{ province }}</option>
        <option v-for="p in provinceOptions" :key="p.value" :value="p.label">{{ p.label }}</option>
      </select>
    </div>
    <div>
      <label class="block text-sm text-gray-600 mb-1">市</label>
      <select
        :value="city"
        class="w-full h-10 px-2 border rounded-lg text-sm bg-white focus:outline-none focus:border-primary disabled:bg-gray-50 disabled:cursor-not-allowed"
        :class="city ? 'text-gray-800' : 'text-gray-400'"
        :disabled="!province"
        @change="onCityChange(($event.target as HTMLSelectElement).value)"
      >
        <option value="" disabled>请选择</option>
        <option v-if="city && !cityOptions.some(c => c.label === city)" :value="city">{{ city }}</option>
        <option v-for="c in cityOptions" :key="c.value" :value="c.label">{{ c.label }}</option>
      </select>
    </div>
    <div>
      <label class="block text-sm text-gray-600 mb-1">区</label>
      <select
        :value="district"
        class="w-full h-10 px-2 border rounded-lg text-sm bg-white focus:outline-none focus:border-primary disabled:bg-gray-50 disabled:cursor-not-allowed"
        :class="district ? 'text-gray-800' : 'text-gray-400'"
        :disabled="!city"
        @change="emit('update:district', ($event.target as HTMLSelectElement).value)"
      >
        <option value="" disabled>请选择</option>
        <option v-if="district && !districtOptions.some(d => d.label === district)" :value="district">{{ district }}</option>
        <option v-for="d in districtOptions" :key="d.value" :value="d.label">{{ d.label }}</option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
// 省市区三级联动下拉：数据来自 element-china-area-data 静态区划包，
// 选项值直接使用中文名称，与后端地址表的文本存储方式保持一致（后端零改动）。
import { regionData } from 'element-china-area-data'

interface RegionNode {
  value: string
  label: string
  children?: RegionNode[]
}

const props = defineProps<{
  province: string
  city: string
  district: string
}>()

const emit = defineEmits<{
  (e: 'update:province', v: string): void
  (e: 'update:city', v: string): void
  (e: 'update:district', v: string): void
}>()

const provinceOptions = regionData as unknown as RegionNode[]

const cityOptions = computed<RegionNode[]>(() =>
  provinceOptions.find(p => p.label === props.province)?.children || []
)

const districtOptions = computed<RegionNode[]>(() =>
  cityOptions.value.find(c => c.label === props.city)?.children || []
)

// 换省清空市/区，换市清空区
function onProvinceChange(v: string) {
  emit('update:province', v)
  emit('update:city', '')
  emit('update:district', '')
}

function onCityChange(v: string) {
  emit('update:city', v)
  emit('update:district', '')
}
</script>
