<template>
  <div class="page-container">
    <!-- 顶部常驻统计条：排队 / 在线客服 / 挂起 / 离线池 -->
    <el-card shadow="never" class="mb-3">
      <div class="stats-bar">
        <div class="stat-item">
          <div class="stat-label">排队中</div>
          <div class="stat-value warn">{{ shown.queueTotal }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">在线客服</div>
          <div class="stat-value ok">{{ shown.onlineTotal }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">挂起客服</div>
          <div class="stat-value gray">{{ shown.suspendedTotal }}</div>
        </div>
        <div class="stat-item">
          <div class="stat-label">离线消息池</div>
          <div class="stat-value danger">{{ shown.offlinePoolTotal }}</div>
        </div>
        <div class="stat-filter">
          <span class="text-xs text-gray-400 mr-1">按组查看</span>
          <el-select v-model="filterGroupId" clearable placeholder="全部分组" style="width: 160px" @change="applyGroupFilter">
            <el-option v-for="g in groupOptions" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ========== 分流分组（职能组） ========== -->
      <el-tab-pane label="分流分组" name="group">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>分流分组</span>
              <el-button type="primary" @click="openGroupCreate">新增分流分组</el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" class="mb-3">
            分组仅表示职能（哪组客服处理哪块问题），不再承担匹配条件；匹配策略请在「分流规则」中配置。未命中任何规则的咨询由全店在线客服均衡分配。
          </el-alert>
          <el-table :data="groupList" v-loading="groupLoading" stripe border>
            <el-table-column prop="groupName" label="分组名称" min-width="140" />
            <el-table-column prop="maxConcurrent" label="最大接待人数" width="120" />
            <el-table-column prop="staffCount" label="客服数" width="80" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text @click="openGroupEdit(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="doGroupDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== 分流规则（策略） ========== -->
      <el-tab-pane label="分流规则" name="rule">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>分流规则</span>
              <el-button type="primary" @click="openRuleCreate">新增分流规则</el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" class="mb-3">
            规则按优先级从小到大依次匹配；命中后消息进入绑定分组，组内按客服权重均衡分配。全部规则都不在服务时间时进入非服务时间模式（机器人回复提示语）。
          </el-alert>
          <el-table :data="ruleList" v-loading="ruleLoading" stripe border>
            <el-table-column prop="ruleName" label="规则名称" min-width="130" />
            <el-table-column label="服务时间" width="130">
              <template #default="{ row }">
                {{ row.serviceStart && row.serviceEnd ? `${row.serviceStart} - ${row.serviceEnd}` : '全天' }}
              </template>
            </el-table-column>
            <el-table-column label="机器人优先" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.robotFirst === 1" type="warning" size="small">是</el-tag>
                <span v-else class="text-gray-400 text-xs">否</span>
              </template>
            </el-table-column>
            <el-table-column label="回头客" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.repeatCustomer === 1" type="success" size="small">{{ row.repeatWindowHours || 24 }}h</el-tag>
                <span v-else class="text-gray-400 text-xs">否</span>
              </template>
            </el-table-column>
            <el-table-column label="匹配条件" min-width="220">
              <template #default="{ row }">
                <template v-if="row.intents || row.orderStatuses">
                  <el-tag v-for="i in splitList(row.intents)" :key="'i' + i" size="small" type="primary" class="mr-1 mb-1">{{ intentLabel(i) }}</el-tag>
                  <el-tag v-for="s in splitList(row.orderStatuses)" :key="'s' + s" size="small" type="success" class="mr-1 mb-1">{{ statusLabel(s) }}</el-tag>
                </template>
                <span v-else class="text-gray-400 text-xs">无条件（全部命中）</span>
              </template>
            </el-table-column>
            <el-table-column prop="groupName" label="匹配分组" width="120">
              <template #default="{ row }">
                {{ row.groupName || `#${row.groupId}` }}
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled === 1" size="small" @change="(v: boolean) => doRuleToggle(row, v)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text @click="openRuleEdit(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="doRuleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ========== 离线消息池 ========== -->
      <el-tab-pane label="离线消息池" name="offline">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>离线消息池</span>
              <el-button size="small" text type="primary" @click="loadOfflinePool">刷新</el-button>
            </div>
          </template>
          <el-alert type="info" :closable="false" class="mb-3">
            当前无客服可接时用户消息进入此池，客服可手动认领；有客服上线或取消挂起时会自动尝试分配。
          </el-alert>
          <el-table :data="offlineRecords" v-loading="offlineLoading" stripe border>
            <el-table-column label="用户" min-width="120">
              <template #default="{ row }">
                {{ row.userNickname || `用户${row.userId}` }}
              </template>
            </el-table-column>
            <el-table-column label="进池时间" width="170">
              <template #default="{ row }">
                {{ formatTime(row.dispatchAt) }}
              </template>
            </el-table-column>
            <el-table-column label="意图" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.intent === 'default' ? 'info' : 'primary'">{{ intentLabel(row.intent) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="命中分组" width="130">
              <template #default="{ row }">
                <!-- 规则命中的分流分组；基础分流入池无分组，客服可自行判断认领 -->
                <el-tag v-if="row.groupName" size="small" type="success">{{ row.groupName }}</el-tag>
                <el-tag v-else size="small" type="info">基础分流</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="消息摘要" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.lastMessage || '（无文本消息）' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default>
                <el-tag type="warning" size="small">待认领</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="doClaim(row)">认领</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="offlineTotal > 0"
            class="mt-3 justify-end"
            layout="total, prev, pager, next"
            :total="offlineTotal"
            :page-size="offlinePageSize"
            :current-page="offlinePageNum"
            @current-change="onOfflinePageChange"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ========== 新增/编辑分组弹窗 ========== -->
    <el-dialog v-model="groupDialogVisible" :title="editGroupId ? '编辑分流分组' : '新增分流分组'" width="560px">
      <el-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-width="110px">
        <el-form-item label="分组名称" prop="groupName">
          <el-input v-model="groupForm.groupName" placeholder="如：售前、退款售后" maxlength="50" />
        </el-form-item>
        <el-form-item label="参与客服">
          <el-select v-model="groupStaffChecked" multiple filterable :teleported="false" placeholder="选择有客服工作台权限的账号（可搜索）" style="width: 100%">
            <!-- 同一客服只能在一个分组：已属他组的客服禁用并提示归属 -->
            <el-option v-for="s in staffOptions" :key="s.id" :value="s.id" :label="(s.realName || s.username)" :disabled="inOtherGroup(s.id)">
              <span>{{ s.realName || s.username }}</span>
              <span v-if="inOtherGroup(s.id)" style="font-size: 12px; color: #909399; margin-left: 6px">已属：{{ groupNameOf(staffGroupMap[s.id]) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="groupStaffChecked.length" label="接待权重">
          <div class="staff-weight-list">
            <div v-for="sid in groupStaffChecked" :key="sid" class="staff-weight-row">
              <span class="staff-weight-name">{{ staffName(sid) }}</span>
              <el-input-number v-model="groupStaffWeights[sid]" :min="1" :max="10" size="small" />
            </div>
          </div>
          <div class="text-xs text-gray-400 mt-1">权重越高分到越多会话，默认 1</div>
        </el-form-item>
        <el-form-item label="最大接待人数" prop="maxConcurrent">
          <el-input-number v-model="groupForm.maxConcurrent" :min="1" :max="99" />
          <div class="text-xs text-gray-400 mt-1">组内客服同时接待的最大会话数（按未结束服务计）</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="groupForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doGroupSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- ========== 新增/编辑规则弹窗 ========== -->
    <el-dialog v-model="ruleDialogVisible" :title="editRuleId ? '编辑分流规则' : '新增分流规则'" width="680px">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="120px">
        <el-form-item label="规则名称" prop="ruleName">
          <el-input v-model="ruleForm.ruleName" placeholder="如：售前咨询、退款售后" maxlength="50" />
        </el-form-item>
        <el-form-item label="优先智能机器人">
          <el-switch v-model="ruleForm.robotFirst" :active-value="1" :inactive-value="0" />
          <div class="text-xs text-gray-400 mt-1">服务时间内，用户文本消息先匹配 FAQ，命中即由机器人回复</div>
        </el-form-item>
        <el-form-item label="服务时间">
          <el-time-picker
            v-model="ruleForm.serviceStart"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="开始时间"
            :clearable="true"
          />
          <span class="mx-1">-</span>
          <el-time-picker
            v-model="ruleForm.serviceEnd"
            format="HH:mm"
            value-format="HH:mm"
            placeholder="结束时间"
            :clearable="true"
          />
          <div class="text-xs text-gray-400 mt-1">留空表示全天；结束时间小于开始时间表示跨天（如 22:00 - 08:00）</div>
        </el-form-item>
        <el-form-item label="非服务时间提示语">
          <el-input v-model="ruleForm.offHoursTip" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="留空使用默认文案" />
        </el-form-item>
        <el-form-item label="回头客优先">
          <el-switch v-model="ruleForm.repeatCustomer" :active-value="1" :inactive-value="0" />
          <div class="text-xs text-gray-400 mt-1">窗口内最近接待过该用户的客服（在线）优先直接接待</div>
        </el-form-item>
        <el-form-item v-if="ruleForm.repeatCustomer === 1" label="回头客窗口">
          <el-select v-model="ruleForm.repeatWindowHours" style="width: 140px">
            <el-option :value="24" label="24 小时" />
            <el-option :value="48" label="48 小时" />
            <el-option :value="72" label="72 小时" />
            <el-option :value="0" label="自定义" />
          </el-select>
          <el-input-number v-if="ruleForm.repeatWindowHours === 0" v-model="customWindowHours" :min="1" :max="720" class="ml-2" />
        </el-form-item>
        <el-form-item label="匹配条件">
          <div>
            <div class="text-xs text-gray-400 mb-1">入口意图（买家从哪个页面发起咨询）</div>
            <el-checkbox-group v-model="ruleForm.intents" @change="onIntentChange">
              <el-checkbox value="product">商品咨询</el-checkbox>
              <el-checkbox value="order">订单售后</el-checkbox>
              <el-checkbox value="default">普通咨询</el-checkbox>
            </el-checkbox-group>
          </div>
          <!-- 订单状态仅对订单售后进线生效（只有订单页进线才有订单真实状态），故勾选订单售后才展示 -->
          <div v-if="ruleForm.intents.includes('order')" class="mt-2 w-full">
            <div class="text-xs text-gray-400 mb-1">订单状态（可选，按订单真实状态匹配；不选则匹配全部状态）</div>
            <el-select v-model="ruleForm.orderStatuses" multiple :teleported="false" placeholder="选择订单状态" style="width: 100%">
              <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
          <div class="text-xs text-gray-400 mt-1">订单入口（订单卡片/订单页跳转）需同时满足入口意图与订单状态条件；非订单入口仅按入口意图判定；均不勾选表示全部消息命中</div>
        </el-form-item>
        <el-form-item label="匹配分组" prop="groupId">
          <el-select v-model="ruleForm.groupId" placeholder="选择本店启用的分组" style="width: 100%">
            <el-option v-for="g in enabledGroups" :key="g.id" :label="g.groupName" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="ruleForm.priority" :min="0" :max="99" />
          <div class="text-xs text-gray-400 mt-1">数字越小越优先匹配</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doRuleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'

interface DispatchGroup {
  id: number
  groupName: string
  maxConcurrent: number
  status: number
  staffCount?: number
}

interface DispatchRule {
  id: number
  ruleName: string
  robotFirst: number
  serviceStart?: string | null
  serviceEnd?: string | null
  offHoursTip?: string | null
  repeatCustomer: number
  repeatWindowHours?: number | null
  intents?: string | null
  orderStatuses?: string | null
  groupId: number
  groupName?: string
  priority: number
  enabled: number
}

interface DispatchStats {
  queueTotal: number
  queueByGroup: Record<number, number>
  onlineTotal: number
  onlineByGroup: Record<number, number>
  suspendedTotal: number
  suspendedByGroup: Record<number, number>
  offlinePoolTotal: number
}

interface OfflinePoolItem {
  conversationId: number
  userId: number
  userNickname?: string
  intent: string
  lastMessage?: string
  lastMessageType?: string
  dispatchAt?: string
}

const INTENT_LABELS: Record<string, string> = {
  product: '商品咨询',
  order: '订单售后',
  default: '普通咨询',
}

const activeTab = ref('group')
const submitting = ref(false)

// ========== 统计条 ==========
const stats = ref<DispatchStats>({ queueTotal: 0, queueByGroup: {}, onlineTotal: 0, onlineByGroup: {}, suspendedTotal: 0, suspendedByGroup: {}, offlinePoolTotal: 0 })
const filterGroupId = ref<number | null>(null)
const groupOptions = ref<DispatchGroup[]>([])

const shown = computed(() => {
  const gid = filterGroupId.value
  const pick = (map: Record<number, number>, total: number) => (gid != null ? (map[gid] || 0) : total)
  return {
    queueTotal: pick(stats.value.queueByGroup, stats.value.queueTotal),
    onlineTotal: pick(stats.value.onlineByGroup, stats.value.onlineTotal),
    suspendedTotal: pick(stats.value.suspendedByGroup, stats.value.suspendedTotal),
    offlinePoolTotal: stats.value.offlinePoolTotal,
  }
})

function applyGroupFilter() {
  // 切换筛选组时无需额外请求，实时按 Map 取值
}

async function loadStats() {
  try {
    const data: DispatchStats = await request.get('/im/dispatch/stats')
    if (data) stats.value = data
  } catch { /* ignore */ }
}

// ========== 分组 tab ==========
const groupList = ref<DispatchGroup[]>([])
const groupLoading = ref(false)
const groupDialogVisible = ref(false)
const editGroupId = ref<number | null>(null)
const groupFormRef = ref<any>(null)
const groupForm = ref({ groupName: '', maxConcurrent: 5, status: 1 })
const groupStaffChecked = ref<number[]>([])
const groupStaffWeights = ref<Record<number, number>>({})
const staffOptions = ref<any[]>([])
// 客服分组归属 map（staffId -> groupId）：分组弹窗禁用已属他组客服用
const staffGroupMap = ref<Record<number, number>>({})
const enabledGroups = computed(() => groupList.value.filter(g => g.status === 1))

const groupRules = {
  groupName: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
  maxConcurrent: [{ required: true, message: '请输入最大接待人数', trigger: 'blur' }],
}

async function loadGroupList() {
  groupLoading.value = true
  try {
    const data: DispatchGroup[] = await request.get('/im/dispatch/group/list')
    groupList.value = data || []
    groupOptions.value = data || []
  } catch { /* ignore */ } finally {
    groupLoading.value = false
  }
}

function openGroupCreate() {
  editGroupId.value = null
  groupForm.value = { groupName: '', maxConcurrent: 5, status: 1 }
  groupStaffChecked.value = []
  groupStaffWeights.value = {}
  loadStaffGroupMap()
  groupDialogVisible.value = true
}

async function openGroupEdit(row: DispatchGroup) {
  editGroupId.value = row.id
  groupForm.value = { groupName: row.groupName, maxConcurrent: row.maxConcurrent ?? 5, status: row.status ?? 1 }
  groupStaffChecked.value = []
  groupStaffWeights.value = {}
  loadStaffGroupMap()
  groupDialogVisible.value = true
  try {
    const data: any = await request.get(`/im/dispatch/${row.id}/staff`)
    groupStaffChecked.value = (data || []).map((x: any) => x.staffId)
    const weights: Record<number, number> = {}
    for (const x of data || []) {
      weights[x.staffId] = x.weight ?? 1
    }
    groupStaffWeights.value = weights
  } catch { /* ignore */ }
}

async function loadStaffGroupMap() {
  try {
    const data: any = await request.get('/im/dispatch/staff-group-map')
    staffGroupMap.value = data || {}
  } catch { /* ignore */ }
}

// 已属其他分组（编辑时属当前分组不算）→ 参与客服选择中禁用
function inOtherGroup(sid: number) {
  const gid = staffGroupMap.value[sid]
  return gid != null && gid !== editGroupId.value
}

function groupNameOf(gid: number) {
  return groupList.value.find(g => g.id === gid)?.groupName || String(gid)
}

async function doGroupSubmit() {
  const valid = await groupFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload: any = { ...groupForm.value }
    let groupId = editGroupId.value
    if (groupId) {
      await request.put('/im/dispatch/group', { ...payload, id: groupId })
    } else {
      const created: any = await request.post('/im/dispatch/group', payload)
      groupId = created?.id
    }
    if (groupId) {
      await request.post(`/im/dispatch/${groupId}/staff`, {
        items: groupStaffChecked.value.map(sid => ({ staffId: sid, weight: groupStaffWeights.value[sid] || 1 })),
      })
    }
    groupDialogVisible.value = false
    await Promise.all([loadGroupList(), loadStats()])
  } catch { /* ignore */ } finally {
    submitting.value = false
  }
}

async function doGroupDelete(row: DispatchGroup) {
  await ElMessageBox.confirm(`确定删除分组「${row.groupName}」吗？被启用规则引用的分组无法删除。`, '提示', { type: 'warning' })
  await request.delete(`/im/dispatch/group/${row.id}`)
  await Promise.all([loadGroupList(), loadStats()])
}

// ========== 规则 tab ==========
const ruleList = ref<DispatchRule[]>([])
const ruleLoading = ref(false)
const ruleDialogVisible = ref(false)
const editRuleId = ref<number | null>(null)
const ruleFormRef = ref<any>(null)
const customWindowHours = ref(24)
const ruleForm = ref({
  ruleName: '',
  robotFirst: 0,
  serviceStart: null as string | null,
  serviceEnd: null as string | null,
  offHoursTip: '',
  repeatCustomer: 0,
  repeatWindowHours: 24,
  intents: [] as string[],
  orderStatuses: [] as number[],
  groupId: null as number | null,
  priority: 0,
  enabled: 1,
})

const ruleRules = {
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  groupId: [{ required: true, message: '请选择匹配分组', trigger: 'change' }],
}

async function loadRuleList() {
  ruleLoading.value = true
  try {
    const data: DispatchRule[] = await request.get('/im/dispatch/rule/list')
    ruleList.value = data || []
  } catch { /* ignore */ } finally {
    ruleLoading.value = false
  }
}

function openRuleCreate() {
  editRuleId.value = null
  ruleForm.value = {
    ruleName: '', robotFirst: 0, serviceStart: null, serviceEnd: null, offHoursTip: '',
    repeatCustomer: 0, repeatWindowHours: 24, intents: [], orderStatuses: [], groupId: null,
    priority: 0, enabled: 1,
  }
  customWindowHours.value = 24
  ruleDialogVisible.value = true
}

function openRuleEdit(row: DispatchRule) {
  editRuleId.value = row.id
  const intents = splitList(row.intents)
  const orderStatuses = splitList(row.orderStatuses).map(Number)
  // 历史数据归一：配置了订单状态则必须含订单售后意图（否则状态选择块不展示）
  if (orderStatuses.length > 0 && !intents.includes('order')) {
    intents.push('order')
  }
  ruleForm.value = {
    ruleName: row.ruleName,
    robotFirst: row.robotFirst ?? 0,
    serviceStart: row.serviceStart || null,
    serviceEnd: row.serviceEnd || null,
    offHoursTip: row.offHoursTip || '',
    repeatCustomer: row.repeatCustomer ?? 0,
    repeatWindowHours: row.repeatWindowHours ?? 24,
    intents,
    orderStatuses,
    groupId: row.groupId,
    priority: row.priority ?? 0,
    enabled: row.enabled ?? 1,
  }
  customWindowHours.value = 24
  ruleDialogVisible.value = true
}

// 入口意图变更：取消勾选订单售后时清空订单状态（无订单意图时状态匹配无意义）
function onIntentChange(vals: (string | number | boolean)[]) {
  if (!vals.includes('order')) {
    ruleForm.value.orderStatuses = []
  }
}

async function doRuleSubmit() {
  const valid = await ruleFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const windowHours = ruleForm.value.repeatWindowHours === 0 ? customWindowHours.value : ruleForm.value.repeatWindowHours
    const payload: any = {
      ruleName: ruleForm.value.ruleName,
      robotFirst: ruleForm.value.robotFirst,
      serviceStart: ruleForm.value.serviceStart || null,
      serviceEnd: ruleForm.value.serviceEnd || null,
      offHoursTip: ruleForm.value.offHoursTip?.trim() || null,
      repeatCustomer: ruleForm.value.repeatCustomer,
      repeatWindowHours: ruleForm.value.repeatCustomer === 1 ? windowHours : null,
      intents: ruleForm.value.intents.join(',') || null,
      orderStatuses: ruleForm.value.orderStatuses.join(',') || null,
      groupId: ruleForm.value.groupId,
      priority: ruleForm.value.priority,
      enabled: ruleForm.value.enabled,
    }
    if (editRuleId.value) {
      await request.put('/im/dispatch/rule', { ...payload, id: editRuleId.value })
    } else {
      await request.post('/im/dispatch/rule', payload)
    }
    ruleDialogVisible.value = false
    await loadRuleList()
  } catch { /* ignore */ } finally {
    submitting.value = false
  }
}

async function doRuleToggle(row: DispatchRule, enabled: boolean) {
  try {
    await request.put(`/im/dispatch/rule/${row.id}/status`, null, { params: { enabled: enabled ? 1 : 0 } })
    row.enabled = enabled ? 1 : 0
    await Promise.all([loadStats()])
  } catch { /* ignore */ }
}

async function doRuleDelete(row: DispatchRule) {
  await ElMessageBox.confirm(`确定删除规则「${row.ruleName}」吗？`, '提示', { type: 'warning' })
  await request.delete(`/im/dispatch/rule/${row.id}`)
  await loadRuleList()
}

// ========== 离线消息池 tab ==========
const offlineRecords = ref<OfflinePoolItem[]>([])
const offlineLoading = ref(false)
const offlineTotal = ref(0)
const offlinePageNum = ref(1)
const offlinePageSize = 10

async function loadOfflinePool() {
  offlineLoading.value = true
  try {
    const data: any = await request.get('/im/dispatch/offline-pool', {
      params: { pageNum: offlinePageNum.value, pageSize: offlinePageSize },
    })
    offlineRecords.value = data?.list || []
    offlineTotal.value = data?.total || 0
  } catch { /* ignore */ } finally {
    offlineLoading.value = false
  }
}

function onOfflinePageChange(page: number) {
  offlinePageNum.value = page
  loadOfflinePool()
}

async function doClaim(row: OfflinePoolItem) {
  try {
    await request.post(`/im/dispatch/offline-pool/${row.conversationId}/claim`)
    ElMessage.success('已认领，请到客服工作台继续接待')
    await Promise.all([loadOfflinePool(), loadStats()])
  } catch { /* ignore */ }
}

// ========== 通用 ==========
function splitList(csv?: string | null) {
  return (csv || '').split(',').map(s => s.trim()).filter(Boolean)
}

function intentLabel(code: string) {
  return INTENT_LABELS[code] || code
}

function statusLabel(code: string) {
  const num = Number(code)
  return statusOptions.value.find(o => o.value === num)?.label || code
}

function staffName(sid: number) {
  const s = staffOptions.value.find(x => x.id === sid)
  return s ? (s.realName || s.username) : String(sid)
}

function formatTime(t?: string) {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

function onTabChange(name: string | number) {
  if (name === 'offline') loadOfflinePool()
}

async function loadStaffOptions() {
  try {
    const data: any = await request.get('/merchant/staff/options', { params: { permCode: 'm:im:workbench' } })
    staffOptions.value = data || []
  } catch { /* ignore */ }
}

const statusOptions = ref<{ value: number; label: string }[]>([])

async function loadStatusOptions() {
  try {
    const data: any = await request.get('/im/dispatch/order-status-options')
    statusOptions.value = data || []
  } catch { /* ignore */ }
}

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  loadGroupList()
  loadRuleList()
  loadStats()
  loadStaffOptions()
  loadStatusOptions()
  // 统计条 10s 轮询
  timer = setInterval(() => {
    loadStats()
    if (activeTab.value === 'offline') loadOfflinePool()
  }, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.page-container { padding: 16px; }
.mb-3 { margin-bottom: 12px; }
.mt-3 { margin-top: 12px; }
.mt-1 { margin-top: 4px; }
.mt-2 { margin-top: 8px; }
.mb-1 { margin-bottom: 4px; }
.mr-1 { margin-right: 4px; }
.ml-1 { margin-left: 4px; }
.ml-2 { margin-left: 8px; }
.mx-1 { margin: 0 4px; }
.w-full { width: 100%; }
.justify-end { justify-content: flex-end; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.stats-bar { display: flex; align-items: center; gap: 32px; flex-wrap: wrap; }
.stat-item { display: flex; flex-direction: column; align-items: center; min-width: 90px; }
.stat-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 600; }
.stat-value.warn { color: #e6a23c; }
.stat-value.ok { color: #67c23a; }
.stat-value.gray { color: #909399; }
.stat-value.danger { color: #f56c6c; }
.stat-filter { margin-left: auto; display: flex; align-items: center; }
.staff-weight-list { width: 100%; }
.staff-weight-row { display: flex; align-items: center; gap: 12px; margin-bottom: 6px; }
.staff-weight-name { width: 100px; }
.text-xs { font-size: 12px; }
.text-gray-400 { color: #909399; }
</style>
