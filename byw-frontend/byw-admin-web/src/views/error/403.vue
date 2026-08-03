<template>
  <div class="forbidden">
    <el-result icon="warning" title="403" sub-title="抱歉，您没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="goHome">返回控制台</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const goHome = () => router.push('/dashboard')
</script>

<style scoped lang="scss">
.forbidden {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  min-height: 400px;
}
</style>
