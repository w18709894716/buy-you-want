<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="商品名称">
          <el-input v-model="searchForm.productName" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="searchForm.rating" placeholder="请选择" clearable style="width: 160px">
            <el-option label="5星" :value="5" />
            <el-option label="4星" :value="4" />
            <el-option label="3星" :value="3" />
            <el-option label="2星" :value="2" />
            <el-option label="1星" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.visible" placeholder="请选择" clearable style="width: 160px">
            <el-option label="显示" :value="true" />
            <el-option label="隐藏" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="review-detail">
              <div class="detail-block">
                <div class="detail-label">评价内容</div>
                <div class="detail-text">{{ row.content || '（无文字）' }}</div>
                <div v-if="row.images?.length" class="detail-images">
                  <el-image
                    v-for="(img, i) in row.images"
                    :key="i"
                    :src="img"
                    :preview-src-list="row.images"
                    :initial-index="i"
                    fit="cover"
                    class="detail-img"
                    preview-teleported
                  />
                </div>
              </div>
              <div v-if="row.hasAppend" class="detail-block append">
                <div class="detail-label">追评</div>
                <div class="detail-text">{{ row.appendContent }}</div>
                <div v-if="row.appendImages?.length" class="detail-images">
                  <el-image
                    v-for="(img, i) in row.appendImages"
                    :key="i"
                    :src="img"
                    :preview-src-list="row.appendImages"
                    :initial-index="i"
                    fit="cover"
                    class="detail-img"
                    preview-teleported
                  />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户" width="100" />
        <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="rating" label="评分" width="140">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评论内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            <div v-if="row.images?.length" class="thumb-cell">
              <el-image
                :src="row.images[0]"
                :preview-src-list="row.images"
                fit="cover"
                class="thumb"
                preview-teleported
              />
              <span class="thumb-count">{{ row.images.length }}张</span>
            </div>
            <span v-else style="color:#909399;">无</span>
          </template>
        </el-table-column>
        <el-table-column label="追评" width="110">
          <template #default="{ row }">
            <el-popover v-if="row.hasAppend" placement="top" :width="320" trigger="click">
              <template #reference>
                <el-tag type="warning" size="small" style="cursor: pointer">有 · 查看</el-tag>
              </template>
              <div class="append-pop">
                <div style="color:#606266; line-height:1.6; white-space:pre-wrap; word-break:break-word;">{{ row.appendContent || '（无文字）' }}</div>
                <div v-if="row.appendImages?.length" style="display:flex; flex-wrap:wrap; gap:6px; margin-top:8px;">
                  <el-image
                    v-for="(img, i) in row.appendImages"
                    :key="i"
                    :src="img"
                    :preview-src-list="row.appendImages"
                    :initial-index="i"
                    fit="cover"
                    style="width:64px; height:64px; border-radius:4px;"
                    preview-teleported
                  />
                </div>
              </div>
            </el-popover>
            <span v-else style="color:#909399;">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="created" label="评论时间" width="170" />
        <el-table-column prop="visible" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.visible ? 'success' : 'info'">
              {{ row.visible ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              :type="row.visible ? 'warning' : 'success'"
              size="small"
              text
              @click="toggleVisible(row)"
            >
              {{ row.visible ? '隐藏' : '显示' }}
            </el-button>
            <el-button type="danger" size="small" text @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])

const searchForm = reactive({
  productName: '',
  rating: undefined as number | undefined,
  visible: undefined as boolean | undefined
})

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/admin/review/list', {
      params: { page: page.value, pageSize: pageSize.value, ...searchForm }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '获取评论列表失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { page.value = 1; fetchData() }

const resetSearch = () => {
  searchForm.productName = ''
  searchForm.rating = undefined
  searchForm.visible = undefined
  handleSearch()
}

const toggleVisible = async (row: any) => {
  const action = row.visible ? '隐藏' : '显示'
  try {
    await request.put(`/admin/review/${row.id}/visible`, null, { params: { visible: !row.visible } })
    row.visible = !row.visible
    ElMessage.success(`${action}成功`)
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || `${action}失败`)
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定要删除该评论吗？', '提示', { type: 'warning' })
  try {
    await request.delete(`/admin/review/${row.id}`)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '删除失败')
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.page-container {
  .search-card {
    margin-bottom: 16px;
  }

  .pagination-wrapper {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }

  .thumb-cell {
    display: flex;
    align-items: center;
    gap: 6px;

    .thumb {
      width: 40px;
      height: 40px;
      border-radius: 4px;
      flex-shrink: 0;
    }

    .thumb-count {
      font-size: 12px;
      color: #909399;
    }
  }

  .review-detail {
    padding: 8px 16px;

    .detail-block {
      margin-bottom: 12px;

      &:last-child {
        margin-bottom: 0;
      }

      &.append {
        padding-top: 12px;
        border-top: 1px dashed #dcdfe6;
      }

      .detail-label {
        font-weight: 600;
        color: #303133;
        margin-bottom: 6px;
      }

      .detail-text {
        color: #606266;
        line-height: 1.6;
        white-space: pre-wrap;
      }

      .detail-images {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 8px;

        .detail-img {
          width: 80px;
          height: 80px;
          border-radius: 4px;
        }
      }
    }
  }
}
</style>
