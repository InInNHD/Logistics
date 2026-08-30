<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Edit, Link, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { warehouseApi } from '@/api/warehouse'
import { createPagination } from '@/utils/pagination'
import type { CarrierAccount, CarrierOrder, CarrierQuote, CarrierReconciliation, CarrierSyncLog, CarrierTracking, Warehouse } from '@/types'

type TabName = 'accounts' | 'orders' | 'logs' | 'quote' | 'reconciliation'
const carriers = [
  ['SF', '顺丰速运'], ['ZTO', '中通快递'], ['YTO', '圆通速递'], ['YUNDA', '韵达速递'],
  ['STO', '申通快递'], ['EMS', '中国邮政 EMS'], ['JD', '京东物流'], ['DEPPON', '德邦快递'],
]
const activeTab = ref<TabName>('accounts')
const loading = ref(false)
const keyword = ref('')
const carrierCode = ref('')
const accounts = ref<CarrierAccount[]>([])
const orders = ref<CarrierOrder[]>([])
const logs = ref<CarrierSyncLog[]>([])
const reconciliation = ref<CarrierReconciliation[]>([])
const quoteResult = ref<CarrierQuote>()
const tracking = ref<CarrierTracking>()
const trackingVisible = ref(false)
const warehouses = ref<Warehouse[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const pagination = reactive<Record<TabName, ReturnType<typeof createPagination>>>({
  accounts: createPagination(), orders: createPagination(), logs: createPagination(),
  quote: createPagination(), reconciliation: createPagination(),
})
const quoteForm = reactive({ carrierCode: 'SF', destination: '新疆 乌鲁木齐', weightKg: 1 })
const form = reactive({
  id: 0, warehouseId: 0, carrierCode: 'SF', accountName: '', apiBaseUrl: 'mock://sf-express',
  credential: '', status: 'ACTIVE', tokenExpiresAt: '', syncEnabled: false, syncIntervalMinutes: 30,
})

const carrierName = (code: string) => carriers.find(([value]) => value === code)?.[1] || code
const dateTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
const triggerName = (value: string) => value === 'SCHEDULED' ? '定时' : '手动'

async function load() {
  loading.value = true
  const state = pagination[activeTab.value]
  try {
    if (activeTab.value === 'accounts') {
      const result = await warehouseApi.carrierAccounts({ keyword: keyword.value || undefined, carrierCode: carrierCode.value || undefined, page: state.page, size: state.size })
      accounts.value = result.records; Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else if (activeTab.value === 'orders') {
      const result = await warehouseApi.carrierOrders({ keyword: keyword.value || undefined, page: state.page, size: state.size })
      orders.value = result.records; Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else if (activeTab.value === 'logs') {
      const result = await warehouseApi.carrierSyncLogs({ page: state.page, size: state.size })
      logs.value = result.records; Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else if (activeTab.value === 'reconciliation') {
      reconciliation.value = await warehouseApi.carrierReconciliation()
    }
  } finally { loading.value = false }
}

function resetForm(account?: CarrierAccount) {
  Object.assign(form, account ? {
    id: account.id, warehouseId: account.warehouseId, carrierCode: account.carrierCode,
    accountName: account.accountName, apiBaseUrl: account.apiBaseUrl, credential: '', status: account.status,
    tokenExpiresAt: account.tokenExpiresAt || '', syncEnabled: account.syncEnabled,
    syncIntervalMinutes: account.syncIntervalMinutes,
  } : {
    id: 0, warehouseId: warehouses.value[0]?.id || 0, carrierCode: 'SF', accountName: '',
    apiBaseUrl: 'mock://sf-express', credential: '', status: 'ACTIVE', tokenExpiresAt: '',
    syncEnabled: false, syncIntervalMinutes: 30,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.warehouseId || !form.accountName.trim() || !form.apiBaseUrl.trim()) return ElMessage.warning('请填写仓库、账号名称和接口地址')
  if (!form.id && !form.credential.trim()) return ElMessage.warning('新账号必须填写访问凭证')
  saving.value = true
  try {
    if (form.id) {
      await warehouseApi.updateCarrierAccount(form.id, {
        accountName: form.accountName, apiBaseUrl: form.apiBaseUrl,
        credential: form.credential || undefined, status: form.status, tokenExpiresAt: form.tokenExpiresAt || undefined,
        syncEnabled: form.syncEnabled, syncIntervalMinutes: form.syncIntervalMinutes,
      })
    } else {
      await warehouseApi.createCarrierAccount({
        warehouseId: form.warehouseId, carrierCode: form.carrierCode, accountName: form.accountName,
        apiBaseUrl: form.apiBaseUrl, credential: form.credential, status: form.status,
        tokenExpiresAt: form.tokenExpiresAt || undefined,
        syncEnabled: form.syncEnabled, syncIntervalMinutes: form.syncIntervalMinutes,
      })
    }
    ElMessage.success(form.id ? '快递账号已更新' : '快递账号已创建')
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}

async function testAccount(account: CarrierAccount) {
  await warehouseApi.testCarrierAccount(account.id)
  ElMessage.success(`${carrierName(account.carrierCode)} Mock 连接测试通过`)
  await load()
}

async function syncAccount(account: CarrierAccount) {
  const result = await warehouseApi.syncCarrierAccount(account.id)
  ElMessage.success(`同步完成：获取 ${result.fetchedCount} 张订单`)
  await load()
}

async function calculateQuote() {
  quoteResult.value = await warehouseApi.quoteCarrier(quoteForm)
}

async function showTracking(order: CarrierOrder) {
  tracking.value = await warehouseApi.carrierTracking(order.id)
  trackingVisible.value = true
}

async function search() { pagination[activeTab.value].page = 1; await load() }
async function changePage(page: number) { pagination[activeTab.value].page = page; await load() }
async function changeSize(size: number) { Object.assign(pagination[activeTab.value], { page: 1, size }); await load() }

watch(activeTab, async () => { keyword.value = ''; carrierCode.value = ''; await load() })
watch(() => form.carrierCode, (value) => { if (!form.id) form.apiBaseUrl = `mock://${value.toLowerCase()}` })
onMounted(async () => {
  warehouses.value = (await warehouseApi.warehouses({ page: 1, size: 200 })).records
  await load()
})
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="CARRIER HUB" title="快递集成" description="统一管理快递账号、外部订单与同步运行记录">
      <el-button v-if="activeTab==='accounts'" type="primary" :icon="Plus" @click="resetForm()">新增快递账号</el-button>
    </PageHeader>
    <el-alert title="阶段三已提供新疆演示计价、物流轨迹和周期对账；规则与轨迹为可替换的 Mock 数据，不访问真实快递网站。" type="info" show-icon :closable="false" class="phase-alert" />
    <section class="surface-card table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="快递账号" name="accounts" />
        <el-tab-pane label="聚合订单" name="orders" />
        <el-tab-pane label="同步日志" name="logs" />
        <el-tab-pane label="运费试算" name="quote" />
        <el-tab-pane label="周期对账" name="reconciliation" />
      </el-tabs>
      <div v-if="activeTab==='accounts'||activeTab==='orders'" class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索账号、订单或运单号" class="search-input" @keyup.enter="search" />
          <el-select v-if="activeTab==='accounts'" v-model="carrierCode" clearable placeholder="全部快递" style="width:150px">
            <el-option v-for="item in carriers" :key="item[0]" :label="item[1]" :value="item[0]" />
          </el-select>
          <el-button @click="search">查询</el-button>
        </div>
      </div>

      <el-table v-if="activeTab==='accounts'" v-loading="loading" :data="accounts" stripe>
        <el-table-column prop="warehouseName" label="仓库" min-width="150" />
        <el-table-column label="快递公司" width="130"><template #default="s">{{ carrierName(s.row.carrierCode) }}</template></el-table-column>
        <el-table-column prop="accountName" label="账号名称" min-width="170" />
        <el-table-column prop="credentialHint" label="凭证" width="110"><template #default="s"><span class="mono">{{ s.row.credentialHint }}</span></template></el-table-column>
        <el-table-column label="账号状态" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
        <el-table-column label="连接状态" width="110"><template #default="s"><StatusTag :status="s.row.connectionStatus" /></template></el-table-column>
        <el-table-column label="自动同步" width="110"><template #default="s">{{ s.row.syncEnabled ? `${s.row.syncIntervalMinutes} 分钟` : '关闭' }}</template></el-table-column>
        <el-table-column label="连续失败" width="90" prop="consecutiveFailures" align="right" />
        <el-table-column label="最近同步" width="180"><template #default="s">{{ dateTime(s.row.lastSyncedAt) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="240">
          <template #default="s">
            <el-button link type="primary" :icon="Edit" @click="resetForm(s.row)">编辑</el-button>
            <el-button link type="primary" :icon="Link" @click="testAccount(s.row)">测试</el-button>
            <el-button link type="primary" :icon="Refresh" @click="syncAccount(s.row)">同步</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="activeTab==='orders'" v-loading="loading" :data="orders" stripe>
        <el-table-column prop="externalOrderNo" label="外部订单号" min-width="210"><template #default="s"><span class="mono">{{ s.row.externalOrderNo }}</span></template></el-table-column>
        <el-table-column label="快递公司" width="130"><template #default="s">{{ carrierName(s.row.carrierCode) }}</template></el-table-column>
        <el-table-column prop="accountName" label="来源账号" min-width="160" />
        <el-table-column prop="trackingNo" label="运单号" min-width="190"><template #default="s"><span class="mono">{{ s.row.trackingNo }}</span></template></el-table-column>
        <el-table-column prop="recipientRegion" label="收件区域" min-width="150" />
        <el-table-column label="状态" width="110"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
        <el-table-column prop="amount" label="运费（元）" width="110" align="right" />
        <el-table-column label="同步时间" width="180"><template #default="s">{{ dateTime(s.row.syncedAt) }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="100"><template #default="s"><el-button link type="primary" @click="showTracking(s.row)">查看轨迹</el-button></template></el-table-column>
      </el-table>

      <el-table v-else-if="activeTab==='logs'" v-loading="loading" :data="logs" stripe>
        <el-table-column label="快递公司" width="130"><template #default="s">{{ carrierName(s.row.carrierCode) }}</template></el-table-column>
        <el-table-column prop="accountName" label="账号" min-width="170" />
        <el-table-column prop="triggerType" label="触发方式" width="110"><template #default="s">{{ triggerName(s.row.triggerType) }}</template></el-table-column>
        <el-table-column label="结果" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
        <el-table-column prop="fetchedCount" label="订单数" width="90" align="right" />
        <el-table-column prop="message" label="说明" min-width="280" />
        <el-table-column label="开始时间" width="180"><template #default="s">{{ dateTime(s.row.startedAt) }}</template></el-table-column>
      </el-table>

      <section v-else-if="activeTab==='quote'" class="quote-panel">
        <el-form :model="quoteForm" label-position="top" class="quote-form">
          <el-form-item label="快递公司"><el-select v-model="quoteForm.carrierCode" style="width:100%"><el-option v-for="item in carriers" :key="item[0]" :label="item[1]" :value="item[0]" /></el-select></el-form-item>
          <el-form-item label="新疆目的地"><el-select v-model="quoteForm.destination" style="width:100%"><el-option label="乌鲁木齐" value="新疆 乌鲁木齐" /><el-option label="伊犁" value="新疆 伊犁" /><el-option label="喀什" value="新疆 喀什" /><el-option label="和田" value="新疆 和田" /><el-option label="阿勒泰" value="新疆 阿勒泰" /></el-select></el-form-item>
          <el-form-item label="计费重量（kg）"><el-input-number v-model="quoteForm.weightKg" :min="0.1" :max="1000" :precision="1" style="width:100%" /></el-form-item>
          <el-button type="primary" @click="calculateQuote">立即试算</el-button>
        </el-form>
        <div v-if="quoteResult" class="quote-result"><small>{{ quoteResult.serviceLevel }}</small><strong>¥ {{ quoteResult.totalFee.toFixed(2) }}</strong><span>基础运费 ¥{{ quoteResult.baseFee.toFixed(2) }} + 偏远附加 ¥{{ quoteResult.remoteSurcharge.toFixed(2) }}</span><span>预计 {{ quoteResult.estimatedDays }} 天送达</span></div>
      </section>

      <el-table v-else v-loading="loading" :data="reconciliation" stripe>
        <el-table-column label="快递公司" min-width="150"><template #default="s">{{ carrierName(s.row.carrierCode) }}</template></el-table-column>
        <el-table-column prop="orderCount" label="订单数" width="100" align="right" />
        <el-table-column prop="expectedAmount" label="内部试算（元）" width="150" align="right" />
        <el-table-column prop="billedAmount" label="快递账单（元）" width="150" align="right" />
        <el-table-column prop="differenceAmount" label="差异（元）" width="130" align="right" />
        <el-table-column label="结果" width="120"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
      </el-table>

      <div v-if="['accounts','orders','logs'].includes(activeTab)" class="pagination-wrap">
        <el-pagination :current-page="pagination[activeTab].page" :page-size="pagination[activeTab].size" :page-sizes="[10,20,50]" :total="pagination[activeTab].total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changeSize" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id?'编辑快递账号':'新增快递账号'" width="620px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <div class="form-grid">
          <el-form-item label="所属仓库" required><el-select v-model="form.warehouseId" :disabled="Boolean(form.id)" style="width:100%"><el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="快递公司" required><el-select v-model="form.carrierCode" :disabled="Boolean(form.id)" style="width:100%"><el-option v-for="item in carriers" :key="item[0]" :label="item[1]" :value="item[0]" /></el-select></el-form-item>
          <el-form-item label="账号名称" required><el-input v-model="form.accountName" placeholder="例如：新疆直营网点账号" /></el-form-item>
          <el-form-item label="账号状态"><el-select v-model="form.status" style="width:100%"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select></el-form-item>
          <el-form-item class="full" label="接口地址" required><el-input v-model="form.apiBaseUrl" /></el-form-item>
          <el-form-item class="full" :label="form.id?'访问凭证（留空则不修改）':'访问凭证'" :required="!form.id"><el-input v-model="form.credential" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="自动同步"><el-switch v-model="form.syncEnabled" inline-prompt active-text="启" inactive-text="关" /></el-form-item>
          <el-form-item label="同步间隔（分钟）"><el-input-number v-model="form.syncIntervalMinutes" :min="1" :max="1440" :disabled="!form.syncEnabled" style="width:100%" /></el-form-item>
          <el-form-item class="full" label="Token 到期时间"><el-date-picker v-model="form.tokenExpiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可选" style="width:100%" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="trackingVisible" title="物流轨迹" width="620px">
      <div v-if="tracking" class="tracking-head"><span class="mono">{{ tracking.trackingNo }}</span><StatusTag :status="tracking.currentStatus" /></div>
      <el-timeline v-if="tracking"><el-timeline-item v-for="event in tracking.events" :key="event.status" :timestamp="dateTime(event.occurredAt)" placement="top"><strong>{{ event.description }}</strong><p>{{ event.location }}</p></el-timeline-item></el-timeline>
    </el-dialog>
  </div>
</template>

<style scoped>
.phase-alert{margin-bottom:16px}.search-input{width:300px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.full{grid-column:1/-1}.quote-panel{display:flex;gap:48px;padding:24px 8px 36px}.quote-form{width:420px}.quote-result{display:flex;min-width:280px;flex-direction:column;justify-content:center;gap:10px;padding:24px;border-radius:16px;background:#f5f8fb}.quote-result strong{font-size:34px;color:#0b2942}.quote-result small{color:#a46b00}.tracking-head{display:flex;justify-content:space-between;margin-bottom:24px}.el-timeline p{margin:6px 0;color:#6f8091}
@media(max-width:700px){.search-input{width:100%}.form-grid{grid-template-columns:1fr}.full{grid-column:auto}}
</style>
