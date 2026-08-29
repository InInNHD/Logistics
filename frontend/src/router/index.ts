import { createRouter, createWebHistory } from 'vue-router'
import { canAccessRoute, readStoredRoles } from '@/utils/access'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: '申请账号' },
    },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '运营总览' },
        },
        {
          path: 'master-data',
          name: 'master-data',
          component: () => import('@/views/master/MasterDataView.vue'),
          meta: { title: '基础资料', roles: ['WAREHOUSE_MANAGER'] },
        },
        {
          path: 'inbound',
          name: 'inbound',
          component: () => import('@/views/inbound/InboundOrdersView.vue'),
          meta: { title: '入库管理', roles: ['WAREHOUSE_MANAGER', 'RECEIVER'] },
        },
        {
          path: 'inventory',
          name: 'inventory',
          component: () => import('@/views/inventory/InventoryView.vue'),
          meta: { title: '库存管理', roles: ['WAREHOUSE_MANAGER', 'RECEIVER', 'PICKER'] },
        },
        {
          path: 'outbound',
          name: 'outbound',
          component: () => import('@/views/outbound/OutboundOrdersView.vue'),
          meta: { title: '出库管理', roles: ['WAREHOUSE_MANAGER', 'PICKER'] },
        },
        {
          path: 'carrier-integration',
          name: 'carrier-integration',
          component: () => import('@/views/carrier/CarrierIntegrationView.vue'),
          meta: { title: '快递集成', roles: ['ADMIN', 'WAREHOUSE_MANAGER'] },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/settings/UsersView.vue'),
          meta: { title: '用户与权限', roles: ['ADMIN'] },
        },
        {
          path: 'audit-events',
          name: 'audit-events',
          component: () => import('@/views/settings/AuditEventsView.vue'),
          meta: { title: '认证审计', roles: ['ADMIN'] },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
})

router.beforeEach((to) => {
  document.title = `${String(to.meta.title || '控制台')} · Firefly Logistics`
  const token = localStorage.getItem('firefly_token') || sessionStorage.getItem('firefly_token')
  if (!to.meta.public && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if ((to.name === 'login' || to.name === 'register') && token) return '/dashboard'
  if (token && !to.meta.public && !canAccessRoute(to, readStoredRoles())) return '/dashboard'
  return true
})

export default router
