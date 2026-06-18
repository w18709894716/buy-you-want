<template>
  <div class="dashboard">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #409EFF;">
              <el-icon :size="28"><User /></el-icon>
            </div>
            <div class="stat-info">
              <p class="stat-label">用户总数</p>
              <h3 class="stat-value">{{ stats.totalUsers.toLocaleString() }}</h3>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #67C23A;">
              <el-icon :size="28"><List /></el-icon>
            </div>
            <div class="stat-info">
              <p class="stat-label">订单总数</p>
              <h3 class="stat-value">{{ stats.totalOrders.toLocaleString() }}</h3>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #E6A23C;">
              <el-icon :size="28"><Coin /></el-icon>
            </div>
            <div class="stat-info">
              <p class="stat-label">总收入（元）</p>
              <h3 class="stat-value">{{ stats.totalRevenue.toLocaleString() }}</h3>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-item">
            <div class="stat-icon" style="background-color: #F56C6C;">
              <el-icon :size="28"><ShoppingCart /></el-icon>
            </div>
            <div class="stat-info">
              <p class="stat-label">今日订单</p>
              <h3 class="stat-value">{{ stats.todayOrders.toLocaleString() }}</h3>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">订单趋势</span>
          </template>
          <v-chart :option="orderTrendOption" style="height: 360px;" autoresize />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">分类销售占比</span>
          </template>
          <v-chart :option="categoryPieOption" style="height: 360px;" autoresize />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
])

const stats = reactive({
  totalUsers: 12680,
  totalOrders: 35420,
  totalRevenue: 2856000,
  todayOrders: 128
})

// 订单趋势折线图
const orderTrendOption = {
  tooltip: {
    trigger: 'axis',
    formatter: '{b}<br/>{a}: {c} 单'
  },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
  },
  yAxis: { type: 'value', name: '订单数' },
  series: [
    {
      name: '订单数',
      type: 'line',
      smooth: true,
      areaStyle: { color: 'rgba(64, 158, 255, 0.2)' },
      lineStyle: { color: '#409EFF', width: 2 },
      itemStyle: { color: '#409EFF' },
      data: [2200, 1800, 3100, 2700, 3500, 4100, 3800, 4600, 4200, 5100, 4800, 5600]
    }
  ]
}

// 分类销售饼图
const categoryPieOption = {
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'center' },
  series: [
    {
      name: '销售占比',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%' },
      data: [
        { value: 3500, name: '数码电子' },
        { value: 2800, name: '服装鞋帽' },
        { value: 2100, name: '食品饮料' },
        { value: 1800, name: '家居家装' },
        { value: 1200, name: '美妆个护' },
        { value: 900, name: '运动户外' }
      ]
    }
  ]
}
</script>

<style scoped lang="scss">
.dashboard {
  .stat-row {
    margin-bottom: 20px;
  }

  .stat-card {
    .stat-item {
      display: flex;
      align-items: center;
      gap: 16px;

      .stat-icon {
        width: 60px;
        height: 60px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #fff;
        flex-shrink: 0;
      }

      .stat-info {
        flex: 1;

        .stat-label {
          color: #909399;
          font-size: 13px;
          margin: 0 0 4px;
        }

        .stat-value {
          margin: 0;
          font-size: 24px;
          color: #303133;
          font-weight: 700;
        }
      }
    }
  }

  .chart-row {
    margin-bottom: 20px;
  }

  .card-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}
</style>
