<script setup lang="ts">
defineProps<{ compact?: boolean }>()
</script>

<template>
  <div class="brand" :class="{ compact }">
    <div class="mark" aria-hidden="true">
      <span class="wing wing-left" />
      <span class="wing wing-right" />
      <span class="body" />
      <span class="glow" />
    </div>
    <div v-if="!compact" class="wordmark">
      <strong>firefly</strong>
      <span>LOGISTICS</span>
    </div>
  </div>
</template>

<style scoped>
.brand { display: flex; align-items: center; gap: 12px; color: #fff; }
.mark { position: relative; width: 40px; height: 40px; flex: 0 0 auto; }
.wing { position: absolute; top: 7px; width: 17px; height: 22px; background: rgba(255,255,255,.9); }
.wing-left { left: 2px; border-radius: 14px 5px 12px 7px; transform: rotate(-25deg); }
.wing-right { right: 2px; border-radius: 5px 14px 7px 12px; transform: rotate(25deg); }
.body { position: absolute; left: 17px; top: 6px; width: 7px; height: 25px; border-radius: 8px; background: #f3c969; z-index: 2; }
.glow { position: absolute; left: 14px; bottom: 1px; width: 13px; height: 13px; border-radius: 50%; background: #f3c969; box-shadow: 0 0 13px #f3c969, 0 0 25px rgba(243,201,105,.65); z-index: 3; }
.wordmark { display: flex; flex-direction: column; line-height: 1; }
.wordmark strong { font-size: 23px; font-weight: 750; letter-spacing: -.7px; }
.wordmark span { margin-top: 5px; color: #aebbc8; font-size: 9px; font-weight: 650; letter-spacing: 3px; }
.compact .mark { width: 38px; }
</style>
