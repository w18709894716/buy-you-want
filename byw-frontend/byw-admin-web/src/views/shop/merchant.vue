<template>
  <div class="page-container">
    <!-- 搜索表单 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="审核状态">
          <el-select v-model="auditStatus" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
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
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="merchantType" label="入驻类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.merchantType === 2 ? 'warning' : 'primary'">
              {{ row.merchantType === 2 ? '企业' : '个人' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="shopName" label="意向店铺名" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shopName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="companyName" label="企业名称" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.companyName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="username" label="商家账号" width="120" />
        <el-table-column prop="realName" label="联系人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column label="资质材料" width="150">
          <template #default="{ row }">
            <div class="material-imgs">
              <template v-if="materialList(row).length">
                <el-image
                  v-for="(m, i) in materialList(row)"
                  :key="i"
                  :src="m.url"
                  :preview-src-list="materialList(row).map(x => x.url)"
                  :initial-index="i"
                  preview-teleported
                  fit="cover"
                  class="material-img"
                  :title="m.label"
                />
              </template>
              <span v-else class="text-muted">未上传</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="agreementSigned" label="协议" width="80">
          <template #default="{ row }">
            <el-tag :type="row.agreementSigned === 1 ? 'success' : 'info'" size="small">
              {{ row.agreementSigned === 1 ? '已签署' : '未签署' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditStatus" label="审核状态" width="100">
          <template #default="{ row }">
            <el-tag :type="auditTag(row.auditStatus)">{{ auditLabel(row.auditStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="驳回原因" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" min-width="160" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 0">
              <el-button type="success" size="small" text @click="openApprove(row)">通过</el-button>
              <el-button type="danger" size="small" text @click="handleReject(row)">驳回</el-button>
            </template>
            <span v-else class="text-muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 审核通过弹窗（可修改开店店名） -->
    <el-dialog v-model="approveVisible" title="审核通过" width="420px">
      <el-form label-width="80px">
        <el-form-item label="店铺名称">
          <el-input v-model="approveShopName" maxlength="50" placeholder="审核通过后将以此名称开店" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="approving" @click="confirmApprove">确认通过并开店</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])
const auditStatus = ref<number | undefined>(undefined)

const approveVisible = ref(false)
const approving = ref(false)
const approveShopName = ref('')
const approveRow = ref<any>(null)

const auditLabel = (s: number) => ({ 0: '待审核', 1: '已通过', 2: '已驳回' }[s] || '未知')
const auditTag = (s: number) => (({ 0: 'warning', 1: 'success', 2: 'danger' }[s] || 'info') as any)

/** 按入驻类型聚合材料图片 */
const materialList = (row: any) => {
  const list: { label: string; url: string }[] = []
  if (row.idCardFront) list.push({ label: '身份证人像面', url: row.idCardFront })
  if (row.idCardBack) list.push({ label: '身份证国徽面', url: row.idCardBack })
  if (row.businessLicense) list.push({ label: '营业执照', url: row.businessLicense })
  return list
}

const fetchData = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/shop/admin/merchant/list', {
      params: { pageNum: page.value, pageSize: pageSize.value, auditStatus: auditStatus.value }
    })
    tableData.value = data.list || []
    total.value = data.total || 0
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '获取入驻申请失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const resetSearch = () => {
  auditStatus.value = undefined
  handleSearch()
}

const openApprove = (row: any) => {
  approveRow.value = row
  approveShopName.value = row.shopName || `${row.realName}的店铺`
  approveVisible.value = true
}

const confirmApprove = async () => {
  if (!approveRow.value) return
  approving.value = true
  try {
    await request.post(`/shop/admin/merchant/${approveRow.value.id}/approve`, null, {
      params: { shopName: approveShopName.value || undefined }
    })
    ElMessage.success('审核通过，店铺已创建')
    approveVisible.value = false
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '审核操作失败')
  } finally {
    approving.value = false
  }
}

const handleReject = async (row: any) => {
  const { value } = await ElMessageBox.prompt('请输入驳回原因（将展示给申请人）', '驳回申请', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '如：材料不清晰，请重新上传',
    inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空')
  })
  try {
    await request.post(`/shop/admin/merchant/${row.id}/reject`, null, {
      params: { rejectReason: value.trim() }
    })
    ElMessage.success('已驳回')
    fetchData()
  } catch (error: any) {
    if (!error._handled) ElMessage.error(error?.message || '驳回操作失败')
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
}

.material-imgs {
  display: flex;
  gap: 4px;

  .material-img {
    width: 40px;
    height: 40px;
    border-radius: 4px;
    cursor: pointer;
  }
}

.text-muted {
  color: #999;
  font-size: 12px;
}
</style>
