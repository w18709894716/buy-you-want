<template>
  <div class="dashboard">
    <el-card shadow="never" class="welcome-card">
      <div class="welcome">
        <div class="welcome-text">
          <h2>欢迎回来，{{ userStore.username || '商家' }}</h2>
          <p>这里是 BuyYouWant 商家后台，您可以管理店铺商品、订单、优惠券与评价。</p>
        </div>
        <el-icon class="welcome-icon"><Shop /></el-icon>
      </div>
    </el-card>

    <el-row :gutter="16" class="quick-row">
      <el-col :span="6" v-for="item in quickLinks" :key="item.path">
        <el-card shadow="hover" class="quick-card" @click="$router.push(item.path)">
          <el-icon :size="32" :color="item.color"><component :is="item.icon" /></el-icon>
          <div class="quick-title">{{ item.title }}</div>
          <div class="quick-desc">{{ item.desc }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="shop-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>店铺信息</span>
          <el-button type="primary" text @click="$router.push('/shop/info')">编辑店铺</el-button>
        </div>
      </template>
      <el-descriptions v-if="shop" :column="2" border>
        <el-descriptions-item label="店铺名称">{{ shop.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ shop.contactName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ shop.contactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="经营类型">{{ shop.selfOperated === 0 ? '自营' : '第三方商家' }}</el-descriptions-item>
        <el-descriptions-item label="店铺状态">
          <el-tag :type="statusType(shop.status)">{{ statusLabel(shop.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="店铺简介" :span="2">{{ shop.description || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无店铺信息" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { Goods, List, Present, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const loading = ref(false)
const shop = ref<any>(null)

const quickLinks = [
  { title: '商品管理', desc: '发布与维护商品', path: '/product/list', icon: markRaw(Goods), color: '#409EFF' },
  { title: '订单管理', desc: '查看与发货', path: '/order/list', icon: markRaw(List), color: '#67C23A' },
  { title: '店铺优惠券', desc: '创建营销活动', path: '/promotion/coupon', icon: markRaw(Present), color: '#E6A23C' },
  { title: '评价管理', desc: '回复买家评价', path: '/review/list', icon: markRaw(ChatDotRound), color: '#F56C6C' }
]

const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '关店', type: 'info' },
  1: { label: '营业中', type: 'success' },
  2: { label: '封禁', type: 'danger' }
}
const statusLabel = (s: number) => statusMap[s]?.label || '未知'
const statusType = (s: number) => (statusMap[s]?.type || 'info') as any

const fetchShop = async () => {
  loading.value = true
  try {
    shop.value = await request.get('/merchant/shop/info')
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取店铺信息失败')
  } finally {
    loading.value = false
  }
}

onMounted(fetchShop)
</script>

<style scoped lang="scss">
.dashboard {
  .welcome-card {
    margin-bottom: 16px;

    .welcome {
      display: flex;
      align-items: center;
      justify-content: space-between;

      .welcome-text {
        h2 {
          margin: 0 0 8px;
          color: #303133;
        }

        p {
          margin: 0;
          color: #909399;
        }
      }

      .welcome-icon {
        font-size: 64px;
        color: #409EFF;
      }
    }
  }

  .quick-row {
    margin-bottom: 16px;

    .quick-card {
      text-align: center;
      cursor: pointer;
      transition: transform 0.2s;

      &:hover {
        transform: translateY(-4px);
      }

      .quick-title {
        margin-top: 8px;
        font-size: 16px;
        font-weight: 600;
        color: #303133;
      }

      .quick-desc {
        margin-top: 4px;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .shop-card {
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
  }
}
</style>
