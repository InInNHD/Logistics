<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="page-header">
    <div>
      <div v-if="eyebrow" class="eyebrow">{{ eyebrow }}</div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div class="actions"><slot /></div>
  </header>
</template>

<style scoped>
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.eyebrow { margin-bottom: 6px; color: #a07117; font-size: 11px; font-weight: 750; letter-spacing: 1.8px; text-transform: uppercase; }
h1 { margin: 0; color: #10283f; font-size: 28px; font-weight: 720; letter-spacing: -.7px; }
p { margin: 7px 0 0; color: #738195; font-size: 14px; }
.actions { display: flex; align-items: center; gap: 10px; }
@media(max-width:700px){.page-header{align-items:flex-start;flex-direction:column}.actions{width:100%;flex-wrap:wrap}h1{font-size:24px}}
</style>
