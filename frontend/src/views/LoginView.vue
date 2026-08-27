<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import FireflyMark from '@/components/FireflyMark.vue'
import * as authApi from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const isRegister = computed(() => route.name === 'register')
const form = reactive({ username: '', displayName: '', password: '', confirmPassword: '', remember: false })
const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    ...(isRegister.value ? [{ pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '使用 3-64 位字母、数字、下划线、点或连字符', trigger: 'blur' }] : []),
  ],
  displayName: isRegister.value ? [{ required: true, min: 2, max: 100, message: '请输入 2-100 位姓名', trigger: 'blur' }] : [],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    ...(isRegister.value ? [{ pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/, message: '需包含大小写字母、数字和特殊字符', trigger: 'blur' }] : []),
  ],
  confirmPassword: isRegister.value ? [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value) callback(new Error('请再次输入密码'))
      else if (value !== form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur',
  }] : [],
}))

watch(isRegister, () => {
  form.password = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate()
})

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    if (isRegister.value) {
      await authApi.register({ username: form.username, displayName: form.displayName, password: form.password })
      ElMessage.success('申请已提交，请等待管理员启用')
      await router.replace('/login')
      return
    }
    await auth.signIn(form.username, form.password, form.remember)
    ElMessage.success('欢迎回来')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="story-panel" aria-label="Firefly Logistics 产品介绍">
      <div class="story-content">
        <FireflyMark />
        <div class="kicker">WAREHOUSE OPERATIONS, ILLUMINATED</div>
        <h1>让每一件货物<br />都有清晰的去向。</h1>
        <p>从到货、库存到拣选和发运，Firefly 将仓内作业汇聚成一条可追踪、可协作、可度量的物流链路。</p>
        <div class="metrics">
          <div><strong>99.9%</strong><span>库存可追溯</span></div>
          <div><strong>24 × 7</strong><span>作业实时可见</span></div>
          <div><strong>4</strong><span>岗位权限隔离</span></div>
        </div>
      </div>
      <div class="grid" /><div class="orb orb-one" /><div class="orb orb-two" />
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-logo"><FireflyMark /></div>
        <nav class="auth-tabs" aria-label="账号入口">
          <router-link to="/login" :class="{ active: !isRegister }">登录</router-link>
          <router-link to="/register" :class="{ active: isRegister }">申请账号</router-link>
        </nav>
        <span class="hello">{{ isRegister ? 'JOIN THE TEAM' : 'WELCOME BACK' }}</span>
        <h2>{{ isRegister ? '申请仓储账号' : '登录控制台' }}</h2>
        <p class="hint">{{ isRegister ? '提交后由管理员审批岗位与访问权限' : '使用企业分配的仓储系统账号继续' }}</p>

        <el-alert v-if="isRegister" title="申请账号不会立即获得仓库权限" type="info" :closable="false" show-icon class="approval-note" />
        <el-form ref="formRef" :model="form" :rules="rules" :validate-on-rule-change="false" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model.trim="form.username" :prefix-icon="User" autocomplete="username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="姓名" prop="displayName">
            <el-input v-model.trim="form.displayName" :prefix-icon="User" autocomplete="name" placeholder="请输入真实姓名" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password :autocomplete="isRegister ? 'new-password' : 'current-password'" placeholder="请输入密码" size="large" />
          </el-form-item>
          <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" :prefix-icon="Lock" type="password" show-password autocomplete="new-password" placeholder="请再次输入密码" size="large" />
          </el-form-item>
          <div v-if="!isRegister" class="login-options">
            <el-checkbox v-model="form.remember">在此设备保持登录</el-checkbox>
            <span>忘记密码请联系管理员</span>
          </div>
          <el-button type="primary" size="large" :loading="loading" class="submit" @click="submit">
            {{ isRegister ? '提交账号申请' : '进入 Firefly' }}
          </el-button>
        </el-form>
        <p class="switch-tip">
          {{ isRegister ? '已有账号？' : '还没有企业账号？' }}
          <router-link :to="isRegister ? '/login' : '/register'">{{ isRegister ? '返回登录' : '申请账号' }}</router-link>
        </p>
      </div>
      <footer>© 2026 Firefly Logistics · 企业仓储物流管理平台</footer>
    </section>
  </main>
</template>

<style scoped>
.login-page{display:grid;min-height:100vh;grid-template-columns:minmax(0,1.15fr) minmax(420px,.85fr);background:#fff}.story-panel{position:relative;display:flex;align-items:center;min-height:100vh;padding:7vw;overflow:hidden;color:#fff;background:#0b1f33}.story-content{position:relative;z-index:2;max-width:650px}.kicker{margin-top:110px;color:#f3c969;font-size:11px;font-weight:700;letter-spacing:2.6px}h1{margin:20px 0 26px;font-size:clamp(42px,4.3vw,68px);font-weight:650;line-height:1.18;letter-spacing:-2.4px}.story-content>p{max-width:580px;color:#aebbc8;font-size:16px;line-height:1.9}.metrics{display:flex;gap:54px;margin-top:66px;padding-top:26px;border-top:1px solid rgba(255,255,255,.11)}.metrics div{display:flex;flex-direction:column}.metrics strong{color:#f3c969;font-size:23px;font-weight:650}.metrics span{margin-top:8px;color:#8799aa;font-size:11px}.grid{position:absolute;inset:0;opacity:.035;background-image:linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px);background-size:42px 42px}.orb{position:absolute;border-radius:50%;filter:blur(4px)}.orb-one{right:-160px;top:-160px;width:500px;height:500px;border:1px solid rgba(243,201,105,.16);box-shadow:inset 0 0 80px rgba(243,201,105,.05)}.orb-two{right:14%;bottom:-260px;width:520px;height:520px;background:rgba(49,108,126,.1)}
.login-panel{display:flex;min-height:100vh;align-items:center;justify-content:center;padding:48px;position:relative;overflow:auto}.login-card{width:100%;max-width:420px}.mobile-logo{display:none}.auth-tabs{display:flex;gap:24px;margin-bottom:34px;border-bottom:1px solid #e8edf1}.auth-tabs a{position:relative;padding:0 2px 12px;color:#7c8997;text-decoration:none;font-size:14px;font-weight:650}.auth-tabs a.active{color:#10283f}.auth-tabs a.active::after{position:absolute;right:0;bottom:-1px;left:0;height:3px;border-radius:3px;background:#e4ad38;content:""}.hello{color:#a07117;font-size:11px;font-weight:750;letter-spacing:1.8px}.login-card h2{margin:10px 0 8px;color:#10283f;font-size:32px;letter-spacing:-1px}.hint{margin:0 0 28px;color:#8491a0;font-size:14px}.approval-note{margin-bottom:22px}.login-card :deep(.el-form-item__label){color:#405469;font-size:13px;font-weight:600}.login-card :deep(.el-input__wrapper){min-height:48px;border-radius:10px;box-shadow:0 0 0 1px #dce3e9 inset}.login-card :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 1px #16314c inset}.login-options{display:flex;align-items:center;justify-content:space-between;margin:-2px 0 24px;color:#8995a3;font-size:11px}.submit{width:100%;height:50px;border-radius:10px;font-weight:650}.switch-tip{margin:22px 0 0;text-align:center;color:#83909d;font-size:13px}.switch-tip a{margin-left:5px;color:#a07117;font-weight:650;text-decoration:none}footer{position:absolute;bottom:20px;color:#adb6c0;font-size:10px}
@media(max-width:1000px){.login-page{grid-template-columns:1fr}.story-panel{display:none}.login-panel{padding:36px 20px 70px}.mobile-logo{display:block;margin-bottom:36px}.mobile-logo :deep(.brand){color:#0b1f33}.mobile-logo :deep(.wordmark span){color:#738195}}
</style>
