<template>
  <div class="apply-container">
    <el-card class="apply-card">
      <template #header>
        <div class="card-header">
          <div class="card-title">
            <h2>商家入驻</h2>
            <p>入驻 BuyYouWant 平台，开启您的店铺经营之旅</p>
          </div>
          <div class="card-actions">
            <el-button link type="primary" @click="showQuery = true">查询申请进度</el-button>
            <el-divider direction="vertical" />
            <el-button link type="primary" @click="router.push('/login')">已有账号？去登录</el-button>
          </div>
        </div>
      </template>

      <!-- 审核中 -->
      <el-result v-if="myApply && myApply.auditStatus === 0" icon="info" title="入驻申请审核中"
        :sub-title="`您提交的「${myApply.shopName}」入驻申请正在审核，请耐心等待`">
        <template #extra>
          <div class="apply-summary">
            <p>入驻类型：{{ myApply.merchantType === 2 ? '企业入驻' : '个人入驻' }}</p>
            <p>商家账号：{{ myApply.username }}</p>
            <p>联系人：{{ myApply.realName }}（{{ myApply.phone }}）</p>
          </div>
        </template>
      </el-result>

      <!-- 已入驻成功 -->
      <el-result v-else-if="myApply && myApply.auditStatus === 1" icon="success" title="您已入驻成功"
        :sub-title="`店铺「${myApply.shopName}」已开通，请使用商家账号 ${myApply.username} 登录商家后台管理店铺`">
        <template #extra>
          <el-button type="primary" @click="router.push('/login')">去登录商家后台</el-button>
        </template>
      </el-result>

      <!-- 申请表单（首次申请 / 被驳回后重新提交） -->
      <template v-else>
        <el-alert v-if="myApply && myApply.auditStatus === 2" type="error" :closable="false" class="mb-alert"
          :title="`您上次的入驻申请已被驳回${myApply.rejectReason ? '：' + myApply.rejectReason : ''}，请修改后重新提交`" />
        <el-alert type="info" :closable="false" class="mb-alert"
          title="支持个人和企业入驻，提交材料并签署入驻协议后，平台将在 1-3 个工作日内完成审核" />

        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="apply-form">
          <el-form-item label="入驻类型">
            <el-radio-group v-model="form.merchantType">
              <el-radio-button :value="1">个人入驻</el-radio-button>
              <el-radio-button :value="2">企业入驻</el-radio-button>
            </el-radio-group>
            <span class="type-tip">{{ form.merchantType === 1 ? '适合个体经营者，需上传身份证' : '适合公司主体，需上传营业执照' }}</span>
          </el-form-item>

          <el-form-item label="店铺名称" prop="shopName">
            <el-input v-model.trim="form.shopName" maxlength="50" placeholder="审核通过后作为店铺名" />
          </el-form-item>

          <el-form-item v-if="form.merchantType === 2" label="企业名称" prop="companyName">
            <el-input v-model.trim="form.companyName" maxlength="64" placeholder="与营业执照一致" />
          </el-form-item>

          <el-form-item label="联系人姓名" prop="realName">
            <el-input v-model.trim="form.realName" maxlength="30" placeholder="真实姓名/企业联系人" />
          </el-form-item>

          <el-form-item label="联系电话" prop="phone">
            <el-input v-model.trim="form.phone" maxlength="20" placeholder="常用手机号" />
          </el-form-item>

          <el-form-item label="登录账号" prop="username">
            <el-input v-model.trim="form.username" maxlength="30" placeholder="4-30位字母/数字/下划线，审核通过后用于登录商家后台" />
          </el-form-item>

          <el-form-item label="登录密码" prop="password">
            <el-input v-model="form.password" type="password" maxlength="30" show-password placeholder="至少6位" />
          </el-form-item>

          <!-- 资质材料：个人上传身份证两面，企业上传营业执照 -->
          <el-form-item v-if="form.merchantType === 1" label="身份证照片" required>
            <div class="material-row">
              <div class="material-item">
                <ImageUpload v-model="idCardFrontList" :limit="1" folder="merchant" action="/api/file/upload" />
                <p>人像面</p>
              </div>
              <div class="material-item">
                <ImageUpload v-model="idCardBackList" :limit="1" folder="merchant" action="/api/file/upload" />
                <p>国徽面</p>
              </div>
            </div>
            <p class="material-tip">支持 JPG/PNG，单张不超过 5MB，请确保证件信息清晰可见</p>
          </el-form-item>
          <el-form-item v-else label="营业执照" required>
            <ImageUpload v-model="businessLicenseList" :limit="1" folder="merchant" action="/api/file/upload" />
            <p class="material-tip">支持 JPG/PNG，单张不超过 5MB，请确保证件信息清晰可见</p>
          </el-form-item>

          <el-form-item label-width="0" class="agreement-item">
            <el-checkbox v-model="agreed">我已阅读并同意</el-checkbox>
            <el-button link type="primary" @click="showAgreement = true">《商家入驻协议》</el-button>
          </el-form-item>

          <el-form-item label-width="0">
            <el-button type="primary" size="large" :loading="submitting" class="submit-btn" @click="submitApply">
              {{ myApply && myApply.auditStatus === 2 ? '重新提交申请' : '提交入驻申请' }}
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </el-card>

    <!-- 入驻协议弹窗 -->
    <el-dialog v-model="showAgreement" title="商家入驻协议" width="600px">
      <div class="agreement-content">
        <p>欢迎申请入驻 BuyYouWant 平台。在提交入驻申请前，请仔细阅读以下条款：</p>
        <p><strong>一、入驻资质</strong><br />申请人须为具有完全民事行为能力的自然人或依法设立的企业，并保证提交的身份证件、营业执照等材料真实、合法、有效。</p>
        <p><strong>二、经营规范</strong><br />商家须遵守国家法律法规及平台规则，销售的商品须来源合法、质量合格，不得销售假冒伪劣、违禁商品。</p>
        <p><strong>三、平台服务</strong><br />平台为商家提供店铺管理、商品发布、订单处理、结算等服务。平台有权按规则对商家经营行为进行监督管理。</p>
        <p><strong>四、结算与费用</strong><br />订单交易款按平台结算规则进行分账与结算，具体费率以平台公示为准。</p>
        <p><strong>五、违约处理</strong><br />商家违反本协议或平台规则的，平台有权视情节采取警告、下架商品、关闭店铺等措施。</p>
        <p><strong>六、协议生效</strong><br />勾选"我已阅读并同意"并提交申请即视为签署本协议，本协议自平台审核通过之日起生效。</p>
      </div>
      <template #footer>
        <el-button @click="showAgreement = false">关闭</el-button>
        <el-button type="primary" @click="agreed = true; showAgreement = false">同意并继续</el-button>
      </template>
    </el-dialog>

    <!-- 查询申请进度弹窗 -->
    <el-dialog v-model="showQuery" title="查询申请进度" width="420px">
      <p class="query-tip">请输入申请时设置的商家登录账号和密码</p>
      <el-form label-width="0" @keyup.enter="doQuery">
        <el-form-item>
          <el-input v-model.trim="queryForm.username" maxlength="30" placeholder="商家登录账号" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="queryForm.password" type="password" maxlength="30" show-password placeholder="商家登录密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showQuery = false">取消</el-button>
        <el-button type="primary" :loading="querying" @click="doQuery">查询</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import request from '../../utils/request'
import ImageUpload from '../../components/ImageUpload.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const myApply = ref<any>(null)
const submitting = ref(false)
const showAgreement = ref(false)
const agreed = ref(false)
const showQuery = ref(false)
const querying = ref(false)
const queryForm = reactive({ username: '', password: '' })

const form = reactive({
  merchantType: 1,
  shopName: '',
  companyName: '',
  realName: '',
  phone: '',
  username: '',
  password: ''
})

// 资质材料（ImageUpload 以数组绑定，均限 1 张）
const idCardFrontList = ref<string[]>([])
const idCardBackList = ref<string[]>([])
const businessLicenseList = ref<string[]>([])

const rules: FormRules = {
  shopName: [{ required: true, message: '请填写店铺名称', trigger: 'blur' }],
  companyName: [{ required: true, message: '请填写企业名称', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写联系人姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^1\d{10}$/, message: '请填写正确的手机号', trigger: 'blur' }],
  username: [{ required: true, pattern: /^[a-zA-Z0-9_]{4,30}$/, message: '登录账号需为4-30位字母/数字/下划线', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '登录密码至少6位', trigger: 'blur' }]
}

/** 凭申请账号+密码查询入驻进度（驳回时预填表单便于修改重提） */
const doQuery = async () => {
  if (!queryForm.username || !queryForm.password) {
    ElMessage.warning('请输入申请时设置的账号和密码')
    return
  }
  querying.value = true
  try {
    const data: any = await request.post('/shop/merchant/apply-query', {
      username: queryForm.username,
      password: queryForm.password
    })
    if (!data) {
      ElMessage.error('未查询到申请记录，或账号密码不正确')
      return
    }
    myApply.value = data
    showQuery.value = false
    if (data.auditStatus === 2) {
      form.merchantType = data.merchantType || 1
      form.shopName = data.shopName || ''
      form.companyName = data.companyName || ''
      form.realName = data.realName || ''
      form.phone = data.phone || ''
      form.username = data.username || ''
      // 驳回重提需原密码验证归属，直接复用刚验证通过的查询密码
      form.password = queryForm.password
      idCardFrontList.value = data.idCardFront ? [data.idCardFront] : []
      idCardBackList.value = data.idCardBack ? [data.idCardBack] : []
      businessLicenseList.value = data.businessLicense ? [data.businessLicense] : []
    }
  } catch (e: any) {
    if (!e._handled) ElMessage.error(e.message || '查询失败，请重试')
  } finally {
    querying.value = false
  }
}

/** 资质材料与协议的补充校验（el-form rules 之外的部分） */
const validateExtra = (): string | null => {
  if (form.merchantType === 1 && (!idCardFrontList.value.length || !idCardBackList.value.length)) {
    return '请上传身份证人像面和国徽面照片'
  }
  if (form.merchantType === 2 && !businessLicenseList.value.length) {
    return '请上传营业执照照片'
  }
  if (!agreed.value) return '请先阅读并同意《商家入驻协议》'
  return null
}

/** 提交入驻申请 */
const submitApply = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const err = validateExtra()
    if (err) {
      ElMessage.warning(err)
      return
    }
    submitting.value = true
    try {
      await request.post('/shop/merchant/apply', {
        merchantType: form.merchantType,
        shopName: form.shopName,
        companyName: form.merchantType === 2 ? form.companyName : undefined,
        realName: form.realName,
        phone: form.phone,
        username: form.username,
        password: form.password,
        idCardFront: form.merchantType === 1 ? idCardFrontList.value[0] : undefined,
        idCardBack: form.merchantType === 1 ? idCardBackList.value[0] : undefined,
        businessLicense: form.merchantType === 2 ? businessLicenseList.value[0] : undefined,
        agreementSigned: 1
      })
      await ElMessageBox.alert(
        '平台将在 1-3 个工作日内完成审核，可随时回到本页凭申请账号密码查询审核进度',
        '申请提交成功',
        { confirmButtonText: '我知道了', type: 'success' }
      ).catch(() => {})
      // 本地切换到审核中视图（免登录场景无自动回显，凭账号密码可随时查询）
      myApply.value = {
        auditStatus: 0,
        shopName: form.shopName,
        username: form.username,
        realName: form.realName,
        phone: form.phone,
        merchantType: form.merchantType
      }
    } catch (e: any) {
      if (!e._handled) ElMessage.error(e.message || '提交失败，请重试')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.apply-container {
  min-height: 100vh;
  padding: 40px 16px;
  background: linear-gradient(160deg, #fff1f0 0%, #ffddd9 60%, #ffc1ba 100%);
  overflow-y: auto;
}

.apply-card {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  box-shadow: 0 8px 24px rgba(255, 45, 33, 0.08);

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .card-title {
      h2 {
        margin: 0 0 6px;
        color: var(--el-color-primary);
        font-size: 20px;
      }

      p {
        margin: 0;
        color: #909399;
        font-size: 13px;
      }
    }
  }
}

.mb-alert {
  margin-bottom: 16px;
}

.apply-form {
  margin-top: 8px;

  .type-tip {
    margin-left: 12px;
    color: #909399;
    font-size: 12px;
  }

  .material-row {
    display: flex;
    gap: 24px;
  }

  .material-item {
    text-align: center;

    p {
      margin: 4px 0 0;
      color: #606266;
      font-size: 12px;
    }
  }

  .material-tip {
    width: 100%;
    margin: 4px 0 0;
    color: #909399;
    font-size: 12px;
  }

  .agreement-item {
    :deep(.el-form-item__content) {
      gap: 2px;
    }
  }

  .submit-btn {
    width: 100%;
  }
}

.apply-summary {
  display: inline-block;
  padding: 12px 24px;
  background: #f5f7fa;
  border-radius: 8px;
  text-align: left;

  p {
    margin: 4px 0;
    color: #606266;
    font-size: 13px;
  }
}

.agreement-content {
  max-height: 50vh;
  overflow-y: auto;
  color: #606266;
  font-size: 14px;
  line-height: 1.7;

  p {
    margin: 0 0 12px;
  }
}

.query-tip {
  margin: 0 0 12px;
  color: #909399;
  font-size: 13px;
}
</style>
