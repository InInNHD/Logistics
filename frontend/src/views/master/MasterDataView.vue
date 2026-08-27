<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { warehouseApi } from '@/api/warehouse'
import { createPagination } from '@/utils/pagination'
import type { Location, Partner, Product, Warehouse } from '@/types'

type TabName = 'products' | 'warehouses' | 'locations' | 'partners'

interface MasterForm {
  code: string
  name: string
  status: string
  unit: string
  type: 'SUPPLIER' | 'CUSTOMER'
  warehouseId?: number
  category: string
  barcode: string
  safetyStock: number
  address: string
  manager: string
  locationType: string
  capacity: number
  contact: string
  phone: string
}

const activeTab = ref<TabName>('products')
const loading = ref(false)
const keyword = ref('')
const products = ref<Product[]>([])
const warehouses = ref<Warehouse[]>([])
const warehouseOptions = ref<Warehouse[]>([])
const locations = ref<Location[]>([])
const partners = ref<Partner[]>([])
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<MasterForm>(emptyForm())
const pagination = reactive<Record<TabName, ReturnType<typeof createPagination>>>({
  products: createPagination(),
  warehouses: createPagination(),
  locations: createPagination(),
  partners: createPagination(),
})
const currentPagination = computed(() => pagination[activeTab.value])
const rules: FormRules<MasterForm> = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}
const titles: Record<TabName, string> = { products: '商品', warehouses: '仓库', locations: '货位', partners: '往来单位' }

function emptyForm(): MasterForm {
  return {
    code: '', name: '', status: 'ACTIVE', unit: '件', type: 'SUPPLIER', category: '', barcode: '',
    safetyStock: 0, address: '', manager: '', locationType: 'STORAGE', capacity: 0, contact: '', phone: '',
  }
}

async function loadWarehouseOptions() {
  warehouseOptions.value = (await warehouseApi.warehouses({ page: 1, size: 200 })).records
}

async function load() {
  loading.value = true
  const state = currentPagination.value
  const params = { keyword: keyword.value || undefined, page: state.page, size: state.size }
  try {
    if (activeTab.value === 'products') {
      const result = await warehouseApi.products(params)
      products.value = result.records
      Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else if (activeTab.value === 'warehouses') {
      const result = await warehouseApi.warehouses(params)
      warehouses.value = result.records
      Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else if (activeTab.value === 'locations') {
      const result = await warehouseApi.locations(params)
      locations.value = result.records
      Object.assign(state, { total: result.total, page: result.page, size: result.size })
    } else {
      const result = await warehouseApi.partners(params)
      partners.value = result.records
      Object.assign(state, { total: result.total, page: result.page, size: result.size })
    }
  } finally {
    loading.value = false
  }
}

async function search() {
  currentPagination.value.page = 1
  await load()
}

async function changePage(page: number) {
  currentPagination.value.page = page
  await load()
}

async function changeSize(size: number) {
  currentPagination.value.size = size
  currentPagination.value.page = 1
  await load()
}

function openCreate() {
  Object.assign(form, emptyForm(), { warehouseId: warehouseOptions.value[0]?.id })
  dialogVisible.value = true
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (activeTab.value === 'products') {
    await warehouseApi.createProduct({ sku: form.code, name: form.name, category: form.category, unit: form.unit, barcode: form.barcode, safetyStock: form.safetyStock, status: form.status })
  } else if (activeTab.value === 'warehouses') {
    await warehouseApi.createWarehouse({ code: form.code, name: form.name, address: form.address, manager: form.manager, status: form.status })
    await loadWarehouseOptions()
  } else if (activeTab.value === 'locations') {
    if (!form.warehouseId) return ElMessage.warning('请选择所属仓库')
    await warehouseApi.createLocation({ warehouseId: form.warehouseId, code: form.code, name: form.name, type: form.locationType, capacity: form.capacity, status: form.status })
  } else {
    await warehouseApi.createPartner({ code: form.code, name: form.name, type: form.type, contact: form.contact, phone: form.phone, status: form.status })
  }
  ElMessage.success('基础资料已创建')
  dialogVisible.value = false
  await load()
}

watch(activeTab, async () => {
  keyword.value = ''
  await load()
})

onMounted(async () => {
  await Promise.all([load(), loadWarehouseOptions()])
})
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="MASTER DATA" title="基础资料" description="维护仓库运转所依赖的商品、空间与业务伙伴" />
    <section class="surface-card table-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="商品档案" name="products" /><el-tab-pane label="仓库" name="warehouses" />
        <el-tab-pane label="库位" name="locations" /><el-tab-pane label="供应商 / 客户" name="partners" />
      </el-tabs>
      <div class="toolbar">
        <div class="toolbar-left"><el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索编码或名称" class="search-input" @keyup.enter="search" /><el-button @click="search">查询</el-button></div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增{{ titles[activeTab] }}</el-button>
      </div>

      <el-table v-if="activeTab==='products'" v-loading="loading" :data="products" stripe>
        <el-table-column prop="sku" label="SKU" width="150"><template #default="s"><span class="mono">{{ s.row.sku }}</span></template></el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="220" /><el-table-column prop="category" label="分类" width="150" />
        <el-table-column prop="unit" label="单位" width="90" /><el-table-column prop="barcode" label="条码" min-width="170" />
        <el-table-column prop="safetyStock" label="安全库存" width="110" align="right" /><el-table-column label="状态" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
      </el-table>
      <el-table v-else-if="activeTab==='warehouses'" v-loading="loading" :data="warehouses" stripe>
        <el-table-column prop="code" label="仓库编码" width="150"><template #default="s"><span class="mono">{{ s.row.code }}</span></template></el-table-column><el-table-column prop="name" label="仓库名称" min-width="200" />
        <el-table-column prop="address" label="地址" min-width="280" /><el-table-column prop="manager" label="负责人" width="130" /><el-table-column label="状态" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
      </el-table>
      <el-table v-else-if="activeTab==='locations'" v-loading="loading" :data="locations" stripe>
        <el-table-column prop="code" label="库位编码" width="160"><template #default="s"><span class="mono">{{ s.row.code }}</span></template></el-table-column><el-table-column prop="name" label="库位名称" min-width="180" />
        <el-table-column prop="warehouseName" label="所属仓库" min-width="180" /><el-table-column prop="type" label="库位类型" width="130" /><el-table-column prop="capacity" label="容量" width="110" align="right" /><el-table-column label="状态" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
      </el-table>
      <el-table v-else v-loading="loading" :data="partners" stripe>
        <el-table-column prop="code" label="单位编码" width="150"><template #default="s"><span class="mono">{{ s.row.code }}</span></template></el-table-column><el-table-column prop="name" label="单位名称" min-width="240" />
        <el-table-column label="类型" width="120"><template #default="s">{{ s.row.type==='SUPPLIER'?'供应商':'客户' }}</template></el-table-column><el-table-column prop="contact" label="联系人" width="130" /><el-table-column prop="phone" label="联系电话" width="150" /><el-table-column label="状态" width="100"><template #default="s"><StatusTag :status="s.row.status" /></template></el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination :current-page="currentPagination.page" :page-size="currentPagination.size" :page-sizes="[10,20,50,100]" :total="currentPagination.total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changeSize" />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="`新增${titles[activeTab]}`" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item :label="activeTab==='products'?'SKU':'编码'" prop="code"><el-input v-model="form.code" placeholder="请输入唯一编码" /></el-form-item>
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" placeholder="请输入名称" /></el-form-item>
          <template v-if="activeTab==='products'"><el-form-item label="商品分类"><el-input v-model="form.category" /></el-form-item><el-form-item label="计量单位"><el-input v-model="form.unit" /></el-form-item><el-form-item label="商品条码"><el-input v-model="form.barcode" /></el-form-item><el-form-item label="安全库存"><el-input-number v-model="form.safetyStock" :min="0" style="width:100%" /></el-form-item></template>
          <template v-if="activeTab==='warehouses'"><el-form-item label="负责人"><el-input v-model="form.manager" /></el-form-item><el-form-item class="full" label="仓库地址"><el-input v-model="form.address" /></el-form-item></template>
          <template v-if="activeTab==='locations'"><el-form-item label="所属仓库"><el-select v-model="form.warehouseId" style="width:100%"><el-option v-for="item in warehouseOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item><el-form-item label="库位类型"><el-select v-model="form.locationType" style="width:100%"><el-option label="存储位" value="STORAGE" /><el-option label="收货暂存" value="RECEIVING" /><el-option label="发货暂存" value="SHIPPING" /></el-select></el-form-item><el-form-item label="容量"><el-input-number v-model="form.capacity" :min="0" style="width:100%" /></el-form-item></template>
          <template v-if="activeTab==='partners'"><el-form-item label="单位类型"><el-select v-model="form.type" style="width:100%"><el-option label="供应商" value="SUPPLIER" /><el-option label="客户" value="CUSTOMER" /></el-select></el-form-item><el-form-item label="联系人"><el-input v-model="form.contact" /></el-form-item><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item></template>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="submit">保存资料</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 18px}.form-grid .full{grid-column:1/-1}.search-input{width:280px}
@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .full{grid-column:auto}.search-input{width:min(100%,280px)}}
</style>
