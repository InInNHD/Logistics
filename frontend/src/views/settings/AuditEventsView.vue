<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import * as authApi from '@/api/auth'
import { createPagination } from '@/utils/pagination'

type AuditEvent = Awaited<ReturnType<typeof authApi.getAuditEvents>>['records'][number]
const loading = ref(false)
const events = ref<AuditEvent[]>([])
const pagination = reactive(createPagination())

async function load() {
  loading.value = true
  try {
    const result = await authApi.getAuditEvents({ page: pagination.page, size: pagination.size })
    events.value = result.records
    Object.assign(pagination, { total: result.total, page: result.page, size: result.size })
  } finally { loading.value = false }
}
async function changePage(page: number) { pagination.page = page; await load() }
async function changeSize(size: number) { pagination.size = size; pagination.page = 1; await load() }
function formatTime(value: string) { return new Date(value).toLocaleString('zh-CN', { hour12: false }) }
onMounted(load)
</script>

<template>
  <div class="page-container">
    <PageHeader eyebrow="SECURITY AUDIT" title="认证审计" description="追踪登录、退出、注册申请及管理员账号变更" />
    <section class="surface-card table-card">
      <div class="toolbar"><span class="muted">最近事件按时间倒序排列</span><span class="muted">共 {{ pagination.total }} 条</span></div>
      <el-table v-loading="loading" :data="events" stripe>
        <el-table-column prop="createdAt" label="时间" width="190"><template #default="s">{{ formatTime(s.row.createdAt) }}</template></el-table-column>
        <el-table-column prop="eventType" label="事件" width="160"><template #default="s"><strong class="mono">{{ s.row.eventType }}</strong></template></el-table-column>
        <el-table-column prop="username" label="账号" min-width="150" />
        <el-table-column label="结果" width="90"><template #default="s"><el-tag :type="s.row.success?'success':'danger'" effect="light">{{ s.row.success?'成功':'失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="detail" label="说明" min-width="220" />
      </el-table>
      <div class="pagination-wrap"><el-pagination :current-page="pagination.page" :page-size="pagination.size" :page-sizes="[20,50,100]" :total="pagination.total" layout="total, sizes, prev, pager, next" @current-change="changePage" @size-change="changeSize" /></div>
    </section>
  </div>
</template>
