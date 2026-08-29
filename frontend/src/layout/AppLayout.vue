<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell, Box, Connection, DataAnalysis, Document, Expand, Fold, Goods, House, Operation, Setting, SwitchButton,
  Van,
} from '@element-plus/icons-vue'
import FireflyMark from '@/components/FireflyMark.vue'
import { useAuthStore } from '@/stores/auth'
import { hasAnyRole } from '@/utils/access'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(window.matchMedia('(max-width: 900px)').matches)

const pageTitle = computed(() => String(route.meta.title || '控制台'))
const initials = computed(() => (auth.user?.displayName || auth.user?.username || 'FL').slice(0, 2).toUpperCase())
const roles = computed(() => auth.user?.roles || [])
const can = (required: string[]) => hasAnyRole(roles.value, required)

function closeMobileNav() {
  if (window.matchMedia('(max-width: 900px)').matches) collapsed.value = true
}

async function logout() {
  try { await auth.signOut() } finally { await router.replace('/login') }
}
</script>

<template>
  <div class="app-shell" :class="{ collapsed }">
    <button v-if="!collapsed" class="sidebar-overlay" aria-label="关闭导航" @click="collapsed=true" />
    <aside class="sidebar">
      <div class="logo-wrap"><FireflyMark :compact="collapsed" /></div>
      <nav class="nav">
        <router-link to="/dashboard" class="nav-item" @click="closeMobileNav"><el-icon><DataAnalysis /></el-icon><span>运营总览</span></router-link>
        <div class="nav-label"><span>仓储作业</span></div>
        <router-link v-if="can(['WAREHOUSE_MANAGER','RECEIVER'])" to="/inbound" class="nav-item" @click="closeMobileNav"><el-icon><Box /></el-icon><span>入库管理</span></router-link>
        <router-link v-if="can(['WAREHOUSE_MANAGER','RECEIVER','PICKER'])" to="/inventory" class="nav-item" @click="closeMobileNav"><el-icon><Goods /></el-icon><span>库存管理</span></router-link>
        <router-link v-if="can(['WAREHOUSE_MANAGER','PICKER'])" to="/outbound" class="nav-item" @click="closeMobileNav"><el-icon><Van /></el-icon><span>出库管理</span></router-link>
        <div v-if="can(['WAREHOUSE_MANAGER','ADMIN'])" class="nav-label"><span>物流集成</span></div>
        <router-link v-if="can(['WAREHOUSE_MANAGER','ADMIN'])" to="/carrier-integration" class="nav-item" @click="closeMobileNav"><el-icon><Connection /></el-icon><span>快递集成</span></router-link>
        <div v-if="can(['WAREHOUSE_MANAGER']) || can(['ADMIN'])" class="nav-label"><span>系统配置</span></div>
        <router-link v-if="can(['WAREHOUSE_MANAGER'])" to="/master-data" class="nav-item" @click="closeMobileNav"><el-icon><House /></el-icon><span>基础资料</span></router-link>
        <router-link v-if="can(['ADMIN'])" to="/users" class="nav-item" @click="closeMobileNav"><el-icon><Setting /></el-icon><span>用户与权限</span></router-link>
        <router-link v-if="can(['ADMIN'])" to="/audit-events" class="nav-item" @click="closeMobileNav"><el-icon><Document /></el-icon><span>认证审计</span></router-link>
      </nav>
      <div class="sidebar-foot">
        <div class="system-state"><span class="pulse" /><span>所有服务运行正常</span></div>
        <div class="version">FIREFLY · MVP 0.1</div>
      </div>
    </aside>

    <main class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <button class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
          </button>
          <div class="breadcrumb"><span>Firefly Logistics</span><b>/</b><strong>{{ pageTitle }}</strong></div>
        </div>
        <div class="topbar-right">
          <button class="icon-btn" title="快捷作业"><el-icon><Operation /></el-icon></button>
          <button class="icon-btn notification" title="通知"><el-icon><Bell /></el-icon><span /></button>
          <el-dropdown trigger="click">
            <div class="user-card">
              <div class="avatar">{{ initials }}</div>
              <div class="user-copy"><strong>{{ auth.user?.displayName || '仓库管理员' }}</strong><span>{{ auth.user?.username || 'admin' }}</span></div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :icon="SwitchButton" @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; padding-left: 240px; transition: padding .2s ease; }
.sidebar-overlay { display: none; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 20; display: flex; width: 240px; flex-direction: column; color: #dfe7ee; background: #0b1f33; transition: width .2s ease; }
.logo-wrap { display: flex; height: 80px; align-items: center; padding: 0 22px; border-bottom: 1px solid rgba(255,255,255,.08); }
.nav { flex: 1; padding: 18px 12px; overflow: hidden auto; }
.nav-item { position: relative; display: flex; height: 46px; align-items: center; gap: 13px; margin-bottom: 4px; padding: 0 14px; color: #aebbc8; border-radius: 10px; text-decoration: none; white-space: nowrap; transition: .18s ease; }
.nav-item:hover { color: #fff; background: rgba(255,255,255,.06); }
.nav-item.router-link-active { color: #fff; background: rgba(243,201,105,.12); }
.nav-item.router-link-active::before { position: absolute; left: 0; width: 3px; height: 22px; border-radius: 0 4px 4px 0; background: #f3c969; content: ''; }
.nav-item .el-icon { flex: 0 0 20px; font-size: 19px; }
.nav-item span { font-size: 14px; font-weight: 540; }
.nav-label { height: 36px; padding: 17px 14px 0; color: #60758a; font-size: 10px; font-weight: 700; letter-spacing: 1.5px; }
.sidebar-foot { padding: 18px 20px 22px; border-top: 1px solid rgba(255,255,255,.08); }
.system-state { display: flex; align-items: center; gap: 8px; color: #8fa1b2; font-size: 11px; white-space: nowrap; }
.pulse { width: 7px; height: 7px; border-radius: 50%; background: #58b795; box-shadow: 0 0 0 4px rgba(88,183,149,.1); }
.version { margin-top: 10px; color: #50677d; font-size: 9px; letter-spacing: 1px; }
.main-area { min-height: 100vh; }
.topbar { position: sticky; top: 0; z-index: 15; display: flex; height: 70px; align-items: center; justify-content: space-between; padding: 0 28px; background: rgba(255,255,255,.94); border-bottom: 1px solid #e7ecf0; backdrop-filter: blur(12px); }
.topbar-left, .topbar-right { display: flex; align-items: center; gap: 14px; }
.collapse-btn, .icon-btn { display: grid; width: 36px; height: 36px; place-items: center; padding: 0; color: #526276; background: transparent; border: 0; border-radius: 9px; cursor: pointer; }
.collapse-btn:hover, .icon-btn:hover { background: #f1f4f6; }
.breadcrumb { display: flex; align-items: center; gap: 9px; font-size: 13px; }
.breadcrumb span { color: #8995a3; }.breadcrumb b { color: #cad1d8; font-weight: 400; }.breadcrumb strong { color: #233b51; font-weight: 620; }
.notification { position: relative; }.notification span { position: absolute; top: 7px; right: 7px; width: 6px; height: 6px; border-radius: 50%; background: #e1aa35; border: 1px solid #fff; }
.user-card { display: flex; align-items: center; gap: 10px; padding-left: 4px; cursor: pointer; outline: none; }
.avatar { display: grid; width: 36px; height: 36px; place-items: center; color: #10283f; background: #f5d98e; border-radius: 10px; font-size: 12px; font-weight: 750; }
.user-copy { display: flex; min-width: 88px; flex-direction: column; }.user-copy strong { color: #243c52; font-size: 12px; }.user-copy span { margin-top: 2px; color: #8995a3; font-size: 10px; }
.collapsed { padding-left: 72px; }.collapsed .sidebar { width: 72px; }.collapsed .logo-wrap { padding: 0 16px; }.collapsed .nav { padding-inline: 10px; }.collapsed .nav-item { justify-content: center; padding: 0; }.collapsed .nav-item span, .collapsed .nav-label span, .collapsed .sidebar-foot { display: none; }
@media(max-width:900px){
  .app-shell,.app-shell.collapsed{padding-left:0}.sidebar,.collapsed .sidebar{width:240px;transform:translateX(0);box-shadow:8px 0 30px rgba(4,19,32,.24)}.collapsed .sidebar{transform:translateX(-100%)}
  .collapsed .logo-wrap{padding:0 22px}.collapsed .nav{padding:18px 12px}.collapsed .nav-item{justify-content:flex-start;padding:0 14px}.collapsed .nav-item span,.collapsed .nav-label span,.collapsed .sidebar-foot{display:flex}
  .sidebar-overlay{position:fixed;inset:0;z-index:19;display:block;padding:0;background:rgba(7,22,36,.38);border:0}.topbar{padding:0 16px}.breadcrumb>span,.breadcrumb>b{display:none}
}
@media(max-width:600px){.user-copy{display:none}.topbar-right{gap:3px}.topbar{height:62px}}
</style>
