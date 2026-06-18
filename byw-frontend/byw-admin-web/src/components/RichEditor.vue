<template>
  <div class="rich-editor">
    <!--
      TODO: 替换为真实富文本编辑器
      推荐方案：
      1. wangEditor (https://www.wangeditor.com/) - 轻量中文富文本编辑器
      2. Quill - 通用富文本编辑器
      3. TinyMCE - 企业级编辑器
      安装示例: npm install @wangeditor/editor @wangeditor/editor-for-vue
      引入后替换下方 textarea 为 <Toolbar /> + <Editor /> 组件
    -->
    <el-input
      type="textarea"
      :model-value="modelValue"
      @update:model-value="$emit('update:modelValue', $event)"
      :rows="12"
      :placeholder="placeholder"
      class="editor-textarea"
    />
    <p class="editor-tip">当前为简易文本模式，正式环境请集成富文本编辑器</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
}>(), {
  modelValue: '',
  placeholder: '请输入商品详情描述（支持 HTML 格式）'
})

defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<style scoped lang="scss">
.rich-editor {
  width: 100%;

  .editor-textarea {
    :deep(textarea) {
      font-family: 'Courier New', monospace;
      font-size: 13px;
    }
  }

  .editor-tip {
    margin: 4px 0 0;
    font-size: 12px;
    color: #909399;
  }
}
</style>
