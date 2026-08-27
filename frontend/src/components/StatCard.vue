<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  value: string | number
  caption?: string
  icon: Component
  tone?: 'navy' | 'green' | 'amber' | 'blue'
}>()
</script>

<template>
  <section class="stat-card surface-card" :class="tone || 'navy'">
    <div class="stat-icon"><component :is="icon" /></div>
    <div class="stat-copy">
      <span>{{ title }}</span>
      <strong>{{ value }}</strong>
      <small v-if="caption">{{ caption }}</small>
    </div>
    <div class="accent" />
  </section>
</template>

<style scoped>
.stat-card { position: relative; display: flex; align-items: flex-start; min-height: 138px; padding: 22px; overflow: hidden; }
.stat-icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 12px; }
.stat-icon :deep(svg) { width: 20px; height: 20px; }
.stat-copy { display: flex; flex-direction: column; margin-left: 15px; }
.stat-copy span { color: #718095; font-size: 13px; font-weight: 550; }
.stat-copy strong { margin-top: 8px; color: #112a42; font-size: 28px; font-weight: 730; line-height: 1; font-variant-numeric: tabular-nums; }
.stat-copy small { margin-top: 10px; color: #8a97a7; font-size: 12px; }
.accent { position: absolute; right: -18px; bottom: -22px; width: 80px; height: 80px; border-radius: 50%; opacity: .1; }
.navy .stat-icon { color: #1b4a6c; background: #e9f1f6; } .navy .accent { background: #1b4a6c; }
.green .stat-icon { color: #347d65; background: #e5f3ee; } .green .accent { background: #347d65; }
.amber .stat-icon { color: #a07117; background: #fbf1d8; } .amber .accent { background: #d6a22f; }
.blue .stat-icon { color: #536da7; background: #e9eef9; } .blue .accent { background: #536da7; }
</style>
