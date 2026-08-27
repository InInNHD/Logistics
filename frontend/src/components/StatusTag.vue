<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status?: string }>()

const labelMap: Record<string, string> = {
  ACTIVE: '启用', INACTIVE: '停用', ENABLED: '启用', DISABLED: '停用',
  DRAFT: '草稿', PENDING: '待处理', CREATED: '已创建',
  RECEIVING: '收货中', RECEIVED: '已收货', COMPLETED: '已完成', CANCELLED: '已取消',
  ALLOCATED: '已分配', PICKING: '拣货中', PICKED: '已拣货', PACKED: '已包装',
  SHIPPED: '已发运', PARTIAL: '部分完成', LOCKED: '已冻结',
}

const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  ACTIVE: 'success', ENABLED: 'success', RECEIVED: 'success', COMPLETED: 'success', SHIPPED: 'success',
  PENDING: 'warning', RECEIVING: 'warning', PICKING: 'warning', PARTIAL: 'warning',
  DRAFT: 'info', CREATED: 'info', ALLOCATED: 'primary', PICKED: 'primary', PACKED: 'primary',
  CANCELLED: 'danger', DISABLED: 'danger', INACTIVE: 'danger', LOCKED: 'danger',
}

const normalized = computed(() => (props.status || 'UNKNOWN').toUpperCase())
</script>

<template>
  <el-tag :type="typeMap[normalized] || 'info'" effect="light" round>
    {{ labelMap[normalized] || status || '未知' }}
  </el-tag>
</template>
