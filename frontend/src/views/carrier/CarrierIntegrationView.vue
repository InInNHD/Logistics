<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Edit, Link, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { warehouseApi } from '@/api/warehouse'
import { createPagination } from '@/utils/pagination'
import type { CarrierAccount, CarrierOrder, CarrierSyncLog, Warehouse } from '@/types'

type TabName = 'accounts' | 'orders' | 'logs'
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
const warehouses = ref<Warehouse[]>([])
const dialogVisible = ref(false)
const saving = ref(false)
const pagination = reactive<Record<TabName, ReturnType<typeof createPagination>>>({
  accounts: createPagination(), orders: createPagination(), logs: createPagination(),
})
const form = reactive({
  id: 0, warehouseId: 0, carrierCode: 'SF', accountName: '', apiBaseUrl: 'mock://sf-express',
  credential: '', status: 'ACTIVE', tokenExpiresAt: '',
})

const carrierName = (code: string) => carriers.find(([value]) => value === code)?.[1] || code
const dateTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'

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
    } else {
      const result = await warehouseApi.carrierSyncLogs({ page: state.page, size: state.size })
      logs.value = result.records; Object.assign(state, { total: result.total, page: result.page, size: result.size })
    }
  } finally { loading.value = false }
}

function resetForm(account?: CarrierAccount) {
  Object.assign(form, account ? {
    id: account.id, warehouseId: account.warehouseId, carrierCode: account.carrierCode,
    accountName: account.accountName, apiBaseUrl: account.apiBaseUrl, credential: '', status: account.status,
    tokenExpiresAt: account.tokenExpiresAt || '',
  } : {
    id: 0, warehouseId: warehouses.value[0]?.id || 0, carrierCode: 'SF', accountName: '',
    apiBaseUrl: 'mock://sf-express', credential: '', status: 'ACTIVE', tokenExpiresAt: '',
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
      })
    } else {
      await warehouseApi.createCarrierAccount({
        warehouseId: form.warehouseId, carrierCode: form.carrierCode, accountName: form.accountName,
        apiBaseUrl: form.apiBaseUrl, credential: form.credential, status: form.status,
        tokenExpiresAt: form.tokenExpiresAt || undefined,
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
    <el-alert title="阶段一使用 Mock 适配器验证完整链路，不会访问真实快递网站；凭证已使用 AES-GCM 加密保存。" type="info" show-icon :closable="false" class="phase-alert" />
    <section class="surface-card table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="快递账号" name="accounts" />
        <el-tab-pane label="聚合订单" name="orders" />
        <el-tab-pane label="同步日志" name="logs" />
      </el-tabs>
      <div v-if="activeTab!=='logs'" class="toolbar">
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
      </el-table>

      <el-table v-else v-loading="loading" :data="logs" stripe>
        <el-table-column label="快递公司" width="130"><template #default="s">{{ carrierName(s.row.carrierCode) }}</template></el-table-column>
        <el-table-column prop="accountName" label="账号" min-width="170" />
        <el-table-column prop="triggerType" label="触发方式" width="110"><template #default>手动</template></el-table-column>
        <el-table-column label="结果" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
        <el-table-column prop="fetchedCount" label="订单数" width="90" align="right" />
        <el-table-column prop="message" label="说明" min-width="280" />
        <el-table-column label="开始时间" width="180"><template #default="s">{{ dateTime(s.row.startedAt) }}</template></el-table-column>
      </el-table>

      <div class="pagination-wrap">
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
          <el-form-item class="full" label="Token 到期时间"><el-date-picker v-model="form.tokenExpiresAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="可选" style="width:100%" /></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.phase-alert{margin-bottom:16px}.search-input{width:300px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.full{grid-column:1/-1}
@media(max-width:700px){.search-input{width:100%}.form-grid{grid-template-columns:1fr}.full{grid-column:auto}}
</style>
