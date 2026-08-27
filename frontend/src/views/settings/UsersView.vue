<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import * as authApi from '@/api/auth'
import { createPagination } from '@/utils/pagination'
import type { AuthRole, AuthUser, CreateUserRequest, UserStatus } from '@/types'

interface UserForm extends CreateUserRequest {
  id?: number
}

const roleOptions = ref<AuthRole[]>([
  { name: '系统管理员', code: 'ADMIN', scope: '全部仓库与系统配置' },
  { name: '仓库主管', code: 'WAREHOUSE_MANAGER', scope: '仓库资料与全部仓储作业' },
  { name: '收货员', code: 'RECEIVER', scope: '入库建单与收货' },
  { name: '拣货员', code: 'PICKER', scope: '出库建单、分配与发运' },
])
const roleLabels = computed(() => Object.fromEntries(roleOptions.value.map((role) => [role.code, role.name])))
const loading = ref(false)
const submitting = ref(false)
const users = ref<AuthUser[]>([])
const keyword = ref('')
const status = ref('')
const role = ref('')
const pagination = reactive(createPagination())
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserForm>(emptyForm())
const rules: FormRules<UserForm> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  roles: [{ required: true, type: 'array', min: 1, message: '请至少选择一个角色', trigger: 'change' }],
}

function emptyForm(): UserForm {
  return { username: '', displayName: '', password: '', roles: [], status: 'ACTIVE' }
}

function roleName(value: string) {
  return roleLabels.value[value] || value
}

async function load() {
  loading.value = true
  try {
    const result = await authApi.getUsers({ page: pagination.page, size: pagination.size, keyword: keyword.value || undefined, status: status.value || undefined, role: role.value || undefined })
    users.value = result.records
    Object.assign(pagination, { total: result.total, page: result.page, size: result.size })
  } finally {
    loading.value = false
  }
}

async function search() {
  pagination.page = 1
  await load()
}

async function changePage(page: number) {
  pagination.page = page
  await load()
}

async function changeSize(size: number) {
  pagination.size = size
  pagination.page = 1
  await load()
}

function openCreate() {
  Object.assign(form, emptyForm())
  delete form.id
  dialogVisible.value = true
}

function openEdit(user: AuthUser) {
  Object.assign(form, { id: user.id, username: user.username, displayName: user.displayName, password: '', roles: [...user.roles], status: user.status === 'DISABLED' ? 'DISABLED' : 'ACTIVE' })
  dialogVisible.value = true
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  if (form.roles.length !== 1) return ElMessage.warning('当前版本每位用户请选择一个角色')
  if (!form.id && !form.password) return ElMessage.warning('新用户必须设置初始密码')
  if (form.password && form.password.length < 8) return ElMessage.warning('密码长度不能少于 8 位')
  submitting.value = true
  try {
    if (form.id) {
      await authApi.updateUser(form.id, { displayName: form.displayName, roles: [...form.roles], status: form.status, ...(form.password ? { password: form.password } : {}) })
      ElMessage.success('用户信息已更新')
    } else {
      await authApi.createUser({ username: form.username, displayName: form.displayName, password: form.password, roles: [...form.roles], status: form.status })
      ElMessage.success('用户已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(user: AuthUser) {
  const nextStatus: UserStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await ElMessageBox.confirm(`确认${nextStatus === 'ACTIVE' ? '启用' : '停用'}用户 ${user.username}？`, '用户状态', { type: 'warning' })
  await authApi.updateUser(user.id, { status: nextStatus })
  ElMessage.success('用户状态已更新')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), authApi.getRoles().then((roles) => { roleOptions.value = roles })])
})
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="ACCESS CONTROL" title="用户与权限" description="按岗位控制菜单、数据范围和仓储操作权限">
      <el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button>
    </PageHeader>
    <section class="surface-card table-card">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索用户名或姓名" class="search-input" @keyup.enter="search" />
          <el-select v-model="role" clearable placeholder="全部角色" class="filter-select"><el-option v-for="item in roleOptions" :key="item.code" :label="item.name" :value="item.code" /></el-select>
          <el-select v-model="status" clearable placeholder="全部状态" class="filter-select"><el-option label="启用" value="ACTIVE" /><el-option label="锁定" value="LOCKED" /><el-option label="停用" value="DISABLED" /></el-select>
          <el-button @click="search">查询</el-button>
        </div>
        <span class="muted">共 {{ pagination.total }} 位用户</span>
      </div>
      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="150"><template #default="scope"><strong class="mono">{{ scope.row.username }}</strong></template></el-table-column>
        <el-table-column prop="displayName" label="显示名称" min-width="150" />
        <el-table-column label="角色" min-width="260"><template #default="scope"><div class="role-list"><el-tag v-for="item in scope.row.roles" :key="item" effect="plain">{{ roleName(item) }}</el-tag></div></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope"><StatusTag :status="scope.row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right"><template #default="scope"><el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button><el-button link :type="scope.row.status==='ACTIVE'?'danger':'success'" @click="toggleStatus(scope.row)">{{ scope.row.status==='ACTIVE'?'停用':'启用' }}</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-wrap"><el-pagination :current-page="pagination.page" :page-size="pagination.size" :page-sizes="[10,20,50,100]" :total="pagination.total" layout="total, sizes, prev, pager, next, jumper" @current-change="changePage" @size-change="changeSize" /></div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="用户名" prop="username"><el-input v-model="form.username" :disabled="Boolean(form.id)" autocomplete="off" /></el-form-item>
          <el-form-item label="显示名称" prop="displayName"><el-input v-model="form.displayName" /></el-form-item>
          <el-form-item :label="form.id ? '重置密码（留空则不修改）' : '初始密码'" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="form.status" style="width:100%"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
          <el-form-item label="角色" prop="roles" class="full"><el-select v-model="form.roles" multiple :multiple-limit="1" style="width:100%"><el-option v-for="item in roleOptions" :key="item.code" :label="`${item.name} · ${item.scope}`" :value="item.code" /></el-select></el-form-item>
        </div>
      </el-form>
      <template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" :loading="submitting" @click="submit">保存用户</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.search-input{width:260px}.filter-select{width:140px}.role-list{display:flex;flex-wrap:wrap;gap:6px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.form-grid .full{grid-column:1/-1}
@media(max-width:700px){.form-grid{grid-template-columns:1fr}.form-grid .full{grid-column:auto}.search-input{width:min(100%,260px)}}
</style>
