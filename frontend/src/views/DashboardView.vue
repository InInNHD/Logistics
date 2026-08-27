<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Box, Goods, Van, Warning } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import { warehouseApi } from '@/api/warehouse'
import { recentDayLabels } from '@/utils/date'
import { hasAnyRole } from '@/utils/access'
import { useAuthStore } from '@/stores/auth'
import type { DashboardSummary } from '@/types'

const loading = ref(true)
const auth = useAuthStore()
const summary = ref<DashboardSummary>({
  skuCount: 0, inventoryQuantity: 0, todayInboundQuantity: 0, todayOutboundQuantity: 0,
  pendingInboundCount: 0, pendingOutboundCount: 0, lowStockCount: 0, expiringCount: 0,
})

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value || 0)
const maxTrend = computed(() => Math.max(...(summary.value.inboundTrend || [1]), ...(summary.value.outboundTrend || [1]), 1))
const inboundPoints = computed(() => points(summary.value.inboundTrend || [12, 20, 16, 34, 28, 42, 36]))
const outboundPoints = computed(() => points(summary.value.outboundTrend || [8, 17, 22, 18, 32, 27, 39]))
const trendLength = computed(() => Math.max(summary.value.inboundTrend?.length || 0, summary.value.outboundTrend?.length || 0, 7))
const trendDays = computed(() => recentDayLabels(trendLength.value))
const healthScore = computed(() => {
  if (!summary.value.skuCount) return 100
  const unhealthy = Math.min(summary.value.skuCount, summary.value.lowStockCount + summary.value.expiringCount)
  return Math.max(0, Math.round((1 - unhealthy / summary.value.skuCount) * 100))
})
const healthRingStyle = computed(() => ({
  background: `conic-gradient(#4aa584 0 ${healthScore.value}%, #edf1f3 ${healthScore.value}% 100%)`,
}))
const canInbound = computed(() => hasAnyRole(auth.user?.roles || [], ['WAREHOUSE_MANAGER', 'RECEIVER']))
const canOutbound = computed(() => hasAnyRole(auth.user?.roles || [], ['WAREHOUSE_MANAGER', 'PICKER']))

function points(values: number[]) {
  return values.map((value, index) => `${(index / Math.max(values.length - 1, 1)) * 600},${130 - (value / maxTrend.value) * 105}`).join(' ')
}

async function load() {
  loading.value = true
  try { summary.value = await warehouseApi.dashboard() } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-container">
    <PageHeader eyebrow="OPERATIONS CENTER" title="仓储运营总览" description="掌握今日仓内节奏与库存健康度">
      <el-button @click="load">刷新数据</el-button><el-button v-if="canInbound" type="primary" @click="$router.push('/inbound')">创建入库单</el-button>
    </PageHeader>

    <div class="stats-grid">
      <StatCard title="在库总量" :value="formatNumber(summary.inventoryQuantity)" caption="全部仓库现存数量" :icon="Goods" tone="navy" />
      <StatCard title="今日入库" :value="formatNumber(summary.todayInboundQuantity)" :caption="`${summary.pendingInboundCount} 单待收货`" :icon="Box" tone="green" />
      <StatCard title="今日出库" :value="formatNumber(summary.todayOutboundQuantity)" :caption="`${summary.pendingOutboundCount} 单待发运`" :icon="Van" tone="blue" />
      <StatCard title="库存预警" :value="summary.lowStockCount + summary.expiringCount" :caption="`${summary.lowStockCount} 个低库存 · ${summary.expiringCount} 个临期`" :icon="Warning" tone="amber" />
    </div>

    <div class="dashboard-grid">
      <section class="surface-card trend-card">
        <div class="card-heading"><div><span>近七日流量</span><h3>出入库趋势</h3></div><div class="legend"><i class="inbound" />入库<i class="outbound" />出库</div></div>
        <svg viewBox="0 0 600 150" preserveAspectRatio="none" role="img" aria-label="近七日出入库趋势">
          <defs><linearGradient id="inboundFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4aa584" stop-opacity=".22" /><stop offset="1" stop-color="#4aa584" stop-opacity="0" /></linearGradient></defs>
          <g class="grid-lines"><line v-for="y in [25,60,95,130]" :key="y" x1="0" :y1="y" x2="600" :y2="y" /></g>
          <polyline :points="`0,150 ${inboundPoints} 600,150`" fill="url(#inboundFill)" stroke="none" />
          <polyline :points="inboundPoints" fill="none" stroke="#4aa584" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
          <polyline :points="outboundPoints" fill="none" stroke="#536da7" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <div class="days"><span v-for="(day,index) in trendDays" :key="`${day}-${index}`">{{ day }}</span></div>
      </section>

      <section class="surface-card todo-card">
        <div class="card-heading"><div><span>WORK QUEUE</span><h3>待办作业</h3></div></div>
        <button v-if="canInbound" class="todo" @click="$router.push('/inbound')"><span class="todo-icon green"><el-icon><Box /></el-icon></span><span><strong>待收货入库单</strong><small>请安排卸货与验收</small></span><b>{{ summary.pendingInboundCount }}</b></button>
        <button v-if="canOutbound" class="todo" @click="$router.push('/outbound')"><span class="todo-icon blue"><el-icon><Van /></el-icon></span><span><strong>待处理出库单</strong><small>等待分配或发运</small></span><b>{{ summary.pendingOutboundCount }}</b></button>
        <button class="todo" @click="$router.push('/inventory')"><span class="todo-icon amber"><el-icon><Warning /></el-icon></span><span><strong>库存异常</strong><small>低库存和临期商品</small></span><b>{{ summary.lowStockCount + summary.expiringCount }}</b></button>
      </section>
    </div>

    <div class="dashboard-grid lower">
      <section class="surface-card activity-card">
        <div class="card-heading"><div><span>LIVE ACTIVITY</span><h3>最近动态</h3></div><el-button text @click="$router.push('/inventory')">查看库存流水</el-button></div>
        <el-empty v-if="!summary.recentActivities?.length" :image-size="70" description="暂无作业动态" />
        <div v-else class="timeline">
          <div v-for="activity in summary.recentActivities" :key="activity.id" class="activity">
            <span class="activity-dot" :class="activity.type" /><div><strong>{{ activity.title }}</strong><p>{{ activity.description }}</p></div><time>{{ activity.time }}</time>
          </div>
        </div>
      </section>
      <section class="surface-card health-card">
        <div class="card-heading"><div><span>INVENTORY HEALTH</span><h3>库存健康度</h3></div></div>
        <div class="health-ring" :style="healthRingStyle"><div><strong>{{ healthScore }}</strong><span>健康分</span></div></div>
        <div class="health-list"><span><i class="ok" />正常 SKU <b>{{ Math.max(summary.skuCount - summary.lowStockCount, 0) }}</b></span><span><i class="warn" />低库存 <b>{{ summary.lowStockCount }}</b></span><span><i class="expire" />临期批次 <b>{{ summary.expiringCount }}</b></span></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.stats-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 16px; }
.dashboard-grid { display: grid; grid-template-columns: 1.75fr .85fr; gap: 16px; margin-top: 16px; }.dashboard-grid.lower { grid-template-columns: 1.55fr .75fr; }
.trend-card,.todo-card,.activity-card,.health-card { padding: 22px; }.card-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }.card-heading span { color: #9a782e; font-size: 9px; font-weight: 750; letter-spacing: 1.5px; }.card-heading h3 { margin: 5px 0 0; color: #20394f; font-size: 17px; }.legend { display: flex; align-items: center; gap: 8px; color: #7f8c9b; font-size: 11px; }.legend i { width: 15px; height: 3px; margin-left: 8px; border-radius: 2px; }.legend .inbound { background: #4aa584; }.legend .outbound { background: #536da7; }
svg { width: 100%; height: 190px; overflow: visible; }.grid-lines line { stroke: #e8edf1; stroke-width: 1; }.days { display: flex; justify-content: space-between; color: #8b97a4; font-size: 10px; }
.todo { display: grid; width: 100%; grid-template-columns: 42px 1fr auto; align-items: center; gap: 12px; margin-top: 10px; padding: 13px; text-align: left; background: #f8fafb; border: 0; border-radius: 12px; cursor: pointer; }.todo:hover { background: #f1f5f6; }.todo-icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 10px; }.todo-icon.green { color:#347d65;background:#e5f3ee}.todo-icon.blue{color:#536da7;background:#e9eef9}.todo-icon.amber{color:#a07117;background:#fbf1d8}.todo > span:nth-child(2){display:flex;flex-direction:column}.todo strong{color:#30465a;font-size:12px}.todo small{margin-top:4px;color:#8a97a7;font-size:10px}.todo b{display:grid;min-width:28px;height:28px;place-items:center;color:#294157;background:#fff;border-radius:8px;font-size:12px}
.activity { display:grid;grid-template-columns:12px 1fr auto;gap:12px;align-items:flex-start;padding:13px 0;border-bottom:1px solid #edf0f3}.activity:last-child{border-bottom:0}.activity-dot{width:8px;height:8px;margin-top:5px;border-radius:50%;background:#8491a0}.activity-dot.inbound{background:#4aa584}.activity-dot.outbound{background:#536da7}.activity-dot.inventory{background:#d4a33b}.activity strong{color:#30465a;font-size:12px}.activity p{margin:4px 0 0;color:#8491a0;font-size:11px}.activity time{color:#9aa5b1;font-size:10px}
.health-card { display:grid;grid-template-columns:1fr 1fr;align-items:center}.health-card .card-heading{grid-column:1/-1}.health-ring{display:grid;width:130px;height:130px;place-items:center;border-radius:50%;background:#edf1f3}.health-ring>div{display:flex;width:98px;height:98px;align-items:center;justify-content:center;flex-direction:column;background:#fff;border-radius:50%}.health-ring strong{color:#20394f;font-size:28px}.health-ring span{color:#8a97a7;font-size:10px}.health-list{display:flex;flex-direction:column;gap:14px}.health-list span{display:grid;grid-template-columns:9px 1fr auto;gap:8px;align-items:center;color:#718095;font-size:11px}.health-list i{width:7px;height:7px;border-radius:50%}.health-list .ok{background:#4aa584}.health-list .warn{background:#d4a33b}.health-list .expire{background:#c96e62}.health-list b{color:#30465a}
@media(max-width:1350px){.stats-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:950px){.dashboard-grid,.dashboard-grid.lower{grid-template-columns:1fr}.health-card{grid-template-columns:140px 1fr}}
@media(max-width:600px){.stats-grid{grid-template-columns:1fr}.health-card{grid-template-columns:1fr}.health-ring{margin:auto}.legend{display:none}}
</style>
