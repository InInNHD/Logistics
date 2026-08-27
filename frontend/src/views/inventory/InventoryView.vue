<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh, Search, Sort, Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { warehouseApi } from '@/api/warehouse'
import { createPagination } from '@/utils/pagination'
import { hasAnyRole } from '@/utils/access'
import { useAuthStore } from '@/stores/auth'
import type { InventoryItem, InventoryMovement, Location, Product, Warehouse } from '@/types'

const loading = ref(false)
const submitting = ref(false)
const auth = useAuthStore()
const keyword = ref('')
const warehouseId = ref<number>()
const inventory = ref<InventoryItem[]>([])
const warehouses = ref<Warehouse[]>([])
const locations = ref<Location[]>([])
const products = ref<Product[]>([])
const pagination = reactive(createPagination(50))
const movementDrawerVisible = ref(false)
const movementLoading = ref(false)
const movements = ref<InventoryMovement[]>([])
const movementKeyword = ref('')
const movementWarehouseId = ref<number>()
const movementType = ref('')
const movementPagination = reactive(createPagination(20))
const dialogVisible = ref(false)
const dialogType = ref<'adjust'|'transfer'>('adjust')
const intentKey = ref('')
const adjustment = reactive({ warehouseId: undefined as number|undefined, locationCode: '', productId: undefined as number|undefined, quantity: 0, batchNo: '', expiryDate: '', reason: '' })
const transfer = reactive({ inventoryId: undefined as number|undefined, sourceLocationCode: '', targetLocationCode: '', quantity: 1, reason: '' })
const totalQuantity = computed(() => inventory.value.reduce((sum,item)=>sum+item.quantity,0))
const availableQuantity = computed(() => inventory.value.reduce((sum,item)=>sum+item.availableQuantity,0))
const allocatedQuantity = computed(() => inventory.value.reduce((sum,item)=>sum+item.allocatedQuantity,0))
const adjustmentLocations = computed(() => locations.value.filter((item) => item.warehouseId === adjustment.warehouseId && item.status === 'ACTIVE'))
const transferSource = computed(() => inventory.value.find((item) => item.id === transfer.inventoryId))
const transferLocations = computed(() => locations.value.filter((item) => item.warehouseId === transferSource.value?.warehouseId && item.code !== transfer.sourceLocationCode && item.status === 'ACTIVE'))
const canManageInventory = computed(() => hasAnyRole(auth.user?.roles || [], ['WAREHOUSE_MANAGER']))

async function load() {
  loading.value=true
  try {
    const [stockPage, warehousePage, locationPage, productPage] = await Promise.all([warehouseApi.inventory({ keyword: keyword.value || undefined, warehouseId: warehouseId.value, page: pagination.page, size: pagination.size }), warehouseApi.warehouses({ page: 1, size: 200 }), warehouseApi.locations({ page: 1, size: 200 }), warehouseApi.products({ page: 1, size: 200 })])
    inventory.value=stockPage.records;warehouses.value=warehousePage.records;locations.value=locationPage.records;products.value=productPage.records
    Object.assign(pagination,{total:stockPage.total,page:stockPage.page,size:stockPage.size})
  } finally { loading.value=false }
}
async function loadMovements(){movementLoading.value=true;try{const result=await warehouseApi.inventoryMovements({keyword:movementKeyword.value||undefined,warehouseId:movementWarehouseId.value,type:movementType.value||undefined,page:movementPagination.page,size:movementPagination.size});movements.value=result.records;Object.assign(movementPagination,{total:result.total,page:result.page,size:result.size})}finally{movementLoading.value=false}}
async function openMovements(){movementWarehouseId.value=warehouseId.value;movementDrawerVisible.value=true;movementPagination.page=1;await loadMovements()}
async function searchMovements(){movementPagination.page=1;await loadMovements()}
async function changeMovementPage(page:number){movementPagination.page=page;await loadMovements()}
async function changeMovementSize(size:number){movementPagination.size=size;movementPagination.page=1;await loadMovements()}
function openAdjust(){const firstWarehouse=warehouses.value[0];const firstLocation=locations.value.find(item=>item.warehouseId===firstWarehouse?.id&&item.status==='ACTIVE');Object.assign(adjustment,{warehouseId:firstWarehouse?.id,locationCode:firstLocation?.code||'',productId:products.value[0]?.id,quantity:0,batchNo:'',expiryDate:'',reason:''});dialogType.value='adjust';intentKey.value=crypto.randomUUID();dialogVisible.value=true}
function openTransfer(row?:InventoryItem){const item=row||inventory.value[0];if(!item){ElMessage.warning('当前没有可移库的库存明细');return}Object.assign(transfer,{inventoryId:item.id,sourceLocationCode:item.locationCode,targetLocationCode:'',quantity:1,reason:''});dialogType.value='transfer';intentKey.value=crypto.randomUUID();dialogVisible.value=true}
function closeDialog(){if(submitting.value)return;dialogVisible.value=false;intentKey.value=''}
async function submitAdjust(){if(submitting.value)return;if(!adjustment.warehouseId||!adjustment.productId||!adjustment.locationCode||!adjustment.quantity)return ElMessage.warning('请完整填写调整信息且调整数量不能为 0');submitting.value=true;try{await warehouseApi.adjustInventory({warehouseId:adjustment.warehouseId,productId:adjustment.productId,locationCode:adjustment.locationCode,quantity:adjustment.quantity,batchNo:adjustment.batchNo,expiryDate:adjustment.expiryDate||undefined,reason:adjustment.reason},intentKey.value);ElMessage.success('库存调整已完成');dialogVisible.value=false;intentKey.value='';await load()}finally{submitting.value=false}}
async function submitTransfer(){if(submitting.value)return;if(!transfer.inventoryId||!transfer.sourceLocationCode||!transfer.targetLocationCode||transfer.quantity<=0)return ElMessage.warning('请选择目标库位与移动数量');submitting.value=true;try{await warehouseApi.transferInventory({inventoryId:transfer.inventoryId,sourceLocationCode:transfer.sourceLocationCode,targetLocationCode:transfer.targetLocationCode,quantity:transfer.quantity,reason:transfer.reason},intentKey.value);ElMessage.success('库存移库已完成');dialogVisible.value=false;intentKey.value='';await load()}finally{submitting.value=false}}
function expiryTone(date?:string){if(!date)return '';const days=(new Date(date).getTime()-Date.now())/86400000;return days<0?'expired':days<30?'warning':''}
function movementTypeLabel(type:string){return {INBOUND_RECEIPT:'入库收货',OUTBOUND_SHIPMENT:'出库发运',ADJUSTMENT:'库存调整',TRANSFER_IN:'移库入',TRANSFER_OUT:'移库出'}[type]||type}
function formatTime(value?:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'—'}
async function search(){pagination.page=1;await load()}
async function changePage(page:number){pagination.page=page;await load()}
async function changeSize(size:number){pagination.size=size;pagination.page=1;await load()}
watch(()=>adjustment.warehouseId,()=>{if(!adjustmentLocations.value.some(item=>item.code===adjustment.locationCode)){adjustment.locationCode=adjustmentLocations.value[0]?.code||''}})
watch(()=>transfer.inventoryId,()=>{transfer.sourceLocationCode=transferSource.value?.locationCode||'';transfer.targetLocationCode=''})
onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="INVENTORY CONTROL" title="库存管理" description="按仓库、货位、SKU 与批次查看实时库存">
      <el-button :icon="Tickets" @click="openMovements">库存流水</el-button><el-button v-if="canManageInventory" :icon="Sort" @click="openTransfer()">库存移库</el-button><el-button v-if="canManageInventory" type="primary" @click="openAdjust">库存调整</el-button>
    </PageHeader>
    <div class="inventory-stats"><div class="surface-card"><span>本页现存数量</span><strong>{{ totalQuantity.toLocaleString() }}</strong></div><div class="surface-card"><span>本页可用数量</span><strong>{{ availableQuantity.toLocaleString() }}</strong></div><div class="surface-card"><span>本页已分配</span><strong>{{ allocatedQuantity.toLocaleString() }}</strong></div><div class="surface-card"><span>库存明细总数</span><strong>{{ pagination.total }}</strong><small>SKU / 批次 / 货位组合</small></div></div>
    <section class="surface-card table-card">
      <div class="toolbar"><div class="toolbar-left"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索 SKU、商品或批次" style="width:270px" @keyup.enter="search" /><el-select v-model="warehouseId" clearable placeholder="全部仓库" style="width:180px"><el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" /></el-select><el-button :icon="Refresh" @click="search">刷新</el-button></div><span class="muted">库存数量以余额表为准，所有变化同步记录流水</span></div>
      <el-table v-loading="loading" :data="inventory" stripe>
        <el-table-column prop="sku" label="SKU" width="145"><template #default="s"><strong class="mono sku">{{ s.row.sku }}</strong></template></el-table-column><el-table-column prop="productName" label="商品名称" min-width="200" /><el-table-column prop="warehouseName" label="仓库" min-width="150" /><el-table-column prop="locationCode" label="库位" width="130"><template #default="s"><span class="location">{{ s.row.locationCode }}</span></template></el-table-column><el-table-column prop="batchNo" label="批次" width="135"><template #default="s">{{ s.row.batchNo||'—' }}</template></el-table-column>
        <el-table-column label="现存" width="100" align="right"><template #default="s"><span class="quantity">{{ s.row.quantity }}</span></template></el-table-column><el-table-column label="可用" width="100" align="right"><template #default="s"><span class="quantity available">{{ s.row.availableQuantity }}</span></template></el-table-column><el-table-column prop="allocatedQuantity" label="已分配" width="100" align="right" /><el-table-column prop="lockedQuantity" label="冻结" width="90" align="right" />
        <el-table-column label="有效期" width="125"><template #default="s"><span :class="expiryTone(s.row.expiryDate)">{{ s.row.expiryDate||'—' }}</span></template></el-table-column><el-table-column v-if="canManageInventory" label="操作" width="90" fixed="right"><template #default="s"><el-button link type="primary" @click="openTransfer(s.row)">移库</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-wrap"><el-pagination :current-page="pagination.page" :page-size="pagination.size" :page-sizes="[10,20,50,100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changeSize" /></div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogType==='adjust'?'库存调整':'库存移库'" width="560px" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting">
      <el-form v-if="dialogType==='adjust'" :model="adjustment" label-position="top"><div class="form-grid"><el-form-item label="仓库"><el-select v-model="adjustment.warehouseId" style="width:100%"><el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="库位"><el-select v-model="adjustment.locationCode" filterable style="width:100%"><el-option v-for="item in adjustmentLocations" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.code" /></el-select></el-form-item><el-form-item label="商品"><el-select v-model="adjustment.productId" filterable style="width:100%"><el-option v-for="item in products" :key="item.id" :label="`${item.sku} · ${item.name}`" :value="item.id" /></el-select></el-form-item><el-form-item label="调整数量（增加为正，减少为负）"><el-input-number v-model="adjustment.quantity" style="width:100%" /></el-form-item><el-form-item label="批次号"><el-input v-model="adjustment.batchNo" /></el-form-item><el-form-item label="有效期"><el-date-picker v-model="adjustment.expiryDate" type="date" value-format="YYYY-MM-DD" clearable style="width:100%" /></el-form-item><el-form-item label="调整原因" class="full"><el-input v-model="adjustment.reason" /></el-form-item></div></el-form>
      <el-form v-else :model="transfer" label-position="top"><el-form-item label="库存明细"><el-select v-model="transfer.inventoryId" filterable style="width:100%"><el-option v-for="item in inventory" :key="item.id" :label="`${item.sku} · ${item.locationCode} · 可用 ${item.availableQuantity}`" :value="item.id" /></el-select></el-form-item><div class="form-grid"><el-form-item label="源库位"><el-input v-model="transfer.sourceLocationCode" disabled /></el-form-item><el-form-item label="目标库位"><el-select v-model="transfer.targetLocationCode" filterable style="width:100%"><el-option v-for="item in transferLocations" :key="item.id" :label="`${item.code} · ${item.name}`" :value="item.code" /></el-select></el-form-item><el-form-item label="移动数量"><el-input-number v-model="transfer.quantity" :min="1" style="width:100%" /></el-form-item><el-form-item label="移库原因"><el-input v-model="transfer.reason" /></el-form-item></div></el-form>
      <template #footer><el-button :disabled="submitting" @click="closeDialog">取消</el-button><el-button type="primary" :loading="submitting" @click="dialogType==='adjust'?submitAdjust():submitTransfer()">确认执行</el-button></template>
    </el-dialog>

    <el-drawer v-model="movementDrawerVisible" title="库存流水" size="min(1100px, 92vw)" destroy-on-close>
      <div class="movement-toolbar">
        <el-input v-model="movementKeyword" :prefix-icon="Search" clearable placeholder="搜索流水号、SKU、批次或操作人" @keyup.enter="searchMovements" />
        <el-select v-model="movementWarehouseId" clearable placeholder="全部仓库"><el-option v-for="item in warehouses" :key="item.id" :label="item.name" :value="item.id" /></el-select>
        <el-select v-model="movementType" clearable placeholder="全部类型"><el-option label="入库收货" value="INBOUND_RECEIPT" /><el-option label="出库发运" value="OUTBOUND_SHIPMENT" /><el-option label="库存调整" value="ADJUSTMENT" /><el-option label="移库入" value="TRANSFER_IN" /><el-option label="移库出" value="TRANSFER_OUT" /></el-select>
        <el-button @click="searchMovements">查询</el-button>
      </div>
      <el-table v-loading="movementLoading" :data="movements" stripe>
        <el-table-column prop="movementNo" label="流水号" width="180"><template #default="scope"><span class="mono">{{ scope.row.movementNo }}</span></template></el-table-column>
        <el-table-column label="类型" width="110"><template #default="scope">{{ movementTypeLabel(scope.row.type) }}</template></el-table-column>
        <el-table-column prop="sku" label="SKU" width="130" /><el-table-column prop="productName" label="商品" min-width="180" />
        <el-table-column prop="warehouseName" label="仓库" min-width="140" /><el-table-column prop="locationCode" label="库位" width="110" />
        <el-table-column label="数量" width="100" align="right"><template #default="scope"><strong :class="scope.row.quantity>=0?'positive':'negative'">{{ scope.row.quantity>0?'+':'' }}{{ scope.row.quantity }}</strong></template></el-table-column>
        <el-table-column prop="batchNo" label="批次" width="120" /><el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column label="发生时间" width="175"><template #default="scope">{{ formatTime(scope.row.createdAt) }}</template></el-table-column>
      </el-table>
      <div class="pagination-wrap"><el-pagination :current-page="movementPagination.page" :page-size="movementPagination.size" :page-sizes="[10,20,50,100]" :total="movementPagination.total" layout="total, sizes, prev, pager, next, jumper" @current-change="changeMovementPage" @size-change="changeMovementSize" /></div>
    </el-drawer>
  </div>
</template>

<style scoped>
.inventory-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}.inventory-stats>div{display:flex;min-height:100px;justify-content:center;flex-direction:column;padding:18px 21px}.inventory-stats span{color:#7d8b9b;font-size:11px}.inventory-stats strong{margin-top:7px;color:#19344d;font-size:25px}.inventory-stats small{margin-top:3px;color:#98a3ae;font-size:9px}.sku{color:#254a68}.location{padding:4px 7px;color:#486278;background:#eef3f6;border-radius:5px;font-family:Consolas,monospace;font-size:11px}.available{color:#347d65}.warning{color:#b27d19}.expired{color:#c55c51;font-weight:650}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.form-grid .full{grid-column:1/-1}
.movement-toolbar{display:grid;grid-template-columns:minmax(240px,1fr) 170px 150px auto;gap:10px;margin-bottom:18px}.positive{color:#347d65}.negative{color:#c55c51}
@media(max-width:1000px){.inventory-stats{grid-template-columns:repeat(2,1fr)}}
@media(max-width:700px){.movement-toolbar{grid-template-columns:1fr 1fr}.movement-toolbar>:first-child{grid-column:1/-1}}
@media(max-width:600px){.inventory-stats,.form-grid,.movement-toolbar{grid-template-columns:1fr}.movement-toolbar>:first-child{grid-column:auto}}
</style>
