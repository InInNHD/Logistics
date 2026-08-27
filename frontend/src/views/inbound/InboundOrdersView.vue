<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { warehouseApi } from '@/api/warehouse'
import { toLocalDateTimeValue } from '@/utils/date'
import { createPagination } from '@/utils/pagination'
import type { InboundOrder, OrderLineDraft, Partner, Product, Warehouse } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const status = ref('')
const orders = ref<InboundOrder[]>([])
const warehouses = ref<Warehouse[]>([])
const suppliers = ref<Partner[]>([])
const products = ref<Product[]>([])
const dialogVisible = ref(false)
const receiveDialogVisible = ref(false)
const receiveOrder = ref<InboundOrder>()
const receiveQuantities = reactive<Record<number, number>>({})
const createIntentKey = ref('')
const processingId = ref<number>()
const commandKeys = new Map<number, string>()
const pagination = reactive(createPagination())
const form = reactive({ supplierId: undefined as number | undefined, warehouseId: undefined as number | undefined, expectedAt: '', remark: '', items: [] as OrderLineDraft[] })
const pendingQuantity = computed(() => orders.value.reduce((sum, order) => sum + Math.max(order.totalQuantity - order.receivedQuantity, 0), 0))

async function load() {
  loading.value = true
  try {
    const [orderPage, warehousePage, partnerPage, productPage] = await Promise.all([
      warehouseApi.inboundOrders({ keyword: keyword.value || undefined, status: status.value || undefined, page: pagination.page, size: pagination.size }),
      warehouseApi.warehouses({ page: 1, size: 200 }), warehouseApi.partners({ type: 'SUPPLIER', page: 1, size: 200 }), warehouseApi.products({ page: 1, size: 200 }),
    ])
    orders.value = orderPage.records; warehouses.value = warehousePage.records
    suppliers.value = partnerPage.records.filter((item) => item.type === 'SUPPLIER'); products.value = productPage.records
    Object.assign(pagination, { total: orderPage.total, page: orderPage.page, size: orderPage.size })
  } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { supplierId: suppliers.value[0]?.id, warehouseId: warehouses.value[0]?.id, expectedAt: toLocalDateTimeValue(), remark: '', items: [{ productId: products.value[0]?.id, sku: products.value[0]?.sku || '', productName: products.value[0]?.name, quantity: 1, batchNo: '', expiryDate: '' }] })
  createIntentKey.value = crypto.randomUUID()
  dialogVisible.value = true
}

function clearCreateIntent() { if (!submitting.value) createIntentKey.value = '' }

function onProductChange(line: OrderLineDraft) {
  const product = products.value.find((item) => item.id === line.productId)
  if (product) { line.sku = product.sku; line.productName = product.name }
}

function addLine() { form.items.push({ productId: products.value[0]?.id, sku: products.value[0]?.sku || '', productName: products.value[0]?.name, quantity: 1, batchNo: '', expiryDate: '' }) }

async function createOrder() {
  if (!form.supplierId || !form.warehouseId || !form.items.length || form.items.some((item) => !item.productId || item.quantity <= 0)) return ElMessage.warning('请完整填写仓库、供应商与商品明细')
  submitting.value = true
  try {
    await warehouseApi.createInboundOrder({ supplierId: form.supplierId, warehouseId: form.warehouseId, expectedAt: form.expectedAt, remark: form.remark, items: form.items.map((item) => ({ productId: item.productId!, quantity: item.quantity, batchNo: item.batchNo, expiryDate: item.expiryDate })) }, createIntentKey.value)
    ElMessage.success('入库单已创建'); dialogVisible.value = false; createIntentKey.value = ''; await load()
  } finally { submitting.value = false }
}

function openReceive(order: InboundOrder) {
  receiveOrder.value = order
  for (const key of Object.keys(receiveQuantities)) delete receiveQuantities[Number(key)]
  for (const item of order.items || []) receiveQuantities[item.id] = Math.max(item.quantity - item.receivedQuantity, 0)
  receiveDialogVisible.value = true
}

async function receive() {
  const order = receiveOrder.value
  if (!order) return
  if (processingId.value !== undefined) return
  processingId.value = order.id
  try {
    const items = (order.items || []).map((item) => ({ itemId: item.id, quantity: receiveQuantities[item.id] || 0 })).filter((item) => item.quantity > 0)
    if (!items.length) return ElMessage.warning('请至少填写一项本次收货数量')
    if ((order.items || []).some((item) => (receiveQuantities[item.id] || 0) > item.quantity - item.receivedQuantity)) return ElMessage.warning('本次收货不能超过待收数量')
    const key = commandKeys.get(order.id) || crypto.randomUUID()
    commandKeys.set(order.id, key)
    await warehouseApi.receiveInbound(order.id, { items }, key)
    commandKeys.delete(order.id)
    receiveDialogVisible.value = false
    ElMessage.success('本次收货已入账'); await load()
  } finally { processingId.value = undefined }
}

function formatTime(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
async function search() { pagination.page = 1; await load() }
async function changePage(page: number) { pagination.page = page; await load() }
async function changeSize(size: number) { pagination.size = size; pagination.page = 1; await load() }
onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="INBOUND OPERATIONS" title="入库管理" description="管理预到货、收货确认与库存入账">
      <el-button type="primary" :icon="Plus" @click="openCreate">创建入库单</el-button>
    </PageHeader>
    <div class="summary-strip surface-card">
      <div><span>本页待收货</span><strong>{{ orders.filter(o=>!['RECEIVED','COMPLETED'].includes(o.status)).length }}</strong></div>
      <div><span>本页待收数量</span><strong>{{ pendingQuantity }}</strong></div>
      <div><span>本页已完成</span><strong>{{ orders.filter(o=>['RECEIVED','COMPLETED'].includes(o.status)).length }}</strong></div>
      <p>收货确认后，系统会写入库存流水并同步库存余额。</p>
    </div>
    <section class="surface-card table-card">
      <div class="toolbar"><div class="toolbar-left"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索入库单号或供应商" style="width:280px" @keyup.enter="search" /><el-select v-model="status" clearable placeholder="全部状态" style="width:140px"><el-option label="待收货" value="PENDING" /><el-option label="部分收货" value="PARTIALLY_RECEIVED" /><el-option label="已收货" value="RECEIVED" /></el-select><el-button @click="search">查询</el-button></div><span class="muted">共 {{ pagination.total }} 笔单据</span></div>
      <el-table v-loading="loading" :data="orders" stripe>
        <el-table-column prop="orderNo" label="入库单号" width="180"><template #default="s"><strong class="mono order-no">{{ s.row.orderNo }}</strong></template></el-table-column>
        <el-table-column prop="supplierName" label="供应商" min-width="190" /><el-table-column prop="warehouseName" label="入库仓库" min-width="160" />
        <el-table-column label="数量" width="130" align="right"><template #default="s"><span class="quantity">{{ s.row.receivedQuantity }} / {{ s.row.totalQuantity }}</span></template></el-table-column>
        <el-table-column label="预计到货" width="175"><template #default="s">{{ formatTime(s.row.expectedAt) }}</template></el-table-column><el-table-column label="状态" width="110"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
        <el-table-column label="操作" width="120" fixed="right"><template #default="s"><el-button v-if="!['RECEIVED','COMPLETED'].includes(s.row.status)" type="primary" link :loading="processingId===s.row.id" :disabled="processingId!==undefined&&processingId!==s.row.id" @click="openReceive(s.row)">登记收货</el-button><span v-else class="muted">已入账</span></template></el-table-column>
      </el-table>
      <div class="pagination-wrap"><el-pagination :current-page="pagination.page" :page-size="pagination.size" :page-sizes="[10,20,50,100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changeSize" /></div>
    </section>

    <el-dialog v-model="dialogVisible" title="创建入库单" width="820px" destroy-on-close :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting" @closed="clearCreateIntent">
      <el-form :model="form" label-position="top">
        <div class="header-form"><el-form-item label="供应商"><el-select v-model="form.supplierId" filterable style="width:100%"><el-option v-for="item in suppliers" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="入库仓库"><el-select v-model="form.warehouseId" style="width:100%"><el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="预计到货"><el-date-picker v-model="form.expectedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></el-form-item></div>
        <div class="line-heading"><strong>商品明细</strong><el-button size="small" :icon="Plus" @click="addLine">添加商品</el-button></div>
        <div v-for="(line,index) in form.items" :key="index" class="order-line"><el-select v-model="line.productId" filterable placeholder="选择商品" @change="onProductChange(line)"><el-option v-for="item in products" :key="item.id" :label="`${item.sku} · ${item.name}`" :value="item.id" /></el-select><el-input-number v-model="line.quantity" :min="1" controls-position="right" /><el-input v-model="line.batchNo" placeholder="批次号（选填）" /><el-date-picker v-model="line.expiryDate" type="date" value-format="YYYY-MM-DD" placeholder="有效期（选填）" style="width:100%" /><el-button text type="danger" :disabled="form.items.length===1" @click="form.items.splice(index,1)">移除</el-button></div>
        <el-form-item label="备注" class="remark"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="submitting" @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="createOrder">创建单据</el-button></template>
    </el-dialog>

    <el-dialog v-model="receiveDialogVisible" :title="`登记收货 · ${receiveOrder?.orderNo || ''}`" width="620px">
      <p class="receive-tip">按本次实际到货数量登记，可分多次完成；每次提交都会独立写入库存流水。</p>
      <el-table :data="receiveOrder?.items || []" size="small">
        <el-table-column prop="sku" label="SKU" width="130" />
        <el-table-column prop="productName" label="商品" min-width="150" />
        <el-table-column label="已收 / 应收" width="110"><template #default="s">{{ s.row.receivedQuantity }} / {{ s.row.quantity }}</template></el-table-column>
        <el-table-column label="本次收货" width="150"><template #default="s"><el-input-number v-model="receiveQuantities[s.row.id]" :min="0" :max="s.row.quantity-s.row.receivedQuantity" controls-position="right" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="receiveDialogVisible=false">取消</el-button><el-button type="primary" :loading="processingId!==undefined" @click="receive">确认入账</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-strip{display:grid;grid-template-columns:150px 150px 150px 1fr;align-items:center;margin-bottom:16px;padding:20px 24px}.summary-strip>div{display:flex;flex-direction:column;border-right:1px solid #e8edf1}.summary-strip span{color:#82909f;font-size:11px}.summary-strip strong{margin-top:6px;color:#19344d;font-size:23px}.summary-strip p{margin:0 0 0 26px;color:#8491a0;font-size:12px}.order-no{color:#254a68}.header-form{display:grid;grid-template-columns:1fr 1fr 1fr;gap:16px}.line-heading{display:flex;align-items:center;justify-content:space-between;margin:4px 0 12px;color:#2c465d}.order-line{display:grid;grid-template-columns:2fr .7fr 1fr 1.1fr auto;gap:10px;margin-bottom:10px}.remark{margin-top:20px}.receive-tip{margin:0 0 16px;color:#738195;font-size:12px}
@media(max-width:900px){.summary-strip{grid-template-columns:repeat(3,1fr)}.summary-strip p{grid-column:1/-1;margin:16px 0 0}.header-form{grid-template-columns:1fr}.order-line{grid-template-columns:1fr 1fr}.order-line>:first-child{grid-column:1/-1}}
@media(max-width:600px){.summary-strip{grid-template-columns:1fr}.summary-strip>div{padding:8px 0;border-right:0;border-bottom:1px solid #e8edf1}.order-line{grid-template-columns:1fr}.order-line>:first-child{grid-column:auto}}
</style>
