<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import FireflyMark from '@/components/FireflyMark.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  loading.value = true
  try {
    await auth.signIn(form.username, form.password)
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
    <section class="story-panel">
      <div class="story-content">
        <FireflyMark />
        <div class="kicker">WAREHOUSE OPERATIONS, ILLUMINATED</div>
        <h1>让每一件货物<br />都有清晰的去向。</h1>
        <p>从到货、上架到拣选和发运，Firefly 将仓内作业汇聚成一条可追踪、可协作、可度量的物流链路。</p>
        <div class="metrics">
          <div><strong>99.9%</strong><span>库存可追溯</span></div>
          <div><strong>24 × 7</strong><span>作业实时可见</span></div>
          <div><strong>1</strong><span>统一库存账本</span></div>
        </div>
      </div>
      <div class="grid" /><div class="orb orb-one" /><div class="orb orb-two" />
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="mobile-logo"><FireflyMark /></div>
        <span class="hello">欢迎回来</span>
        <h2>登录控制台</h2>
        <p class="hint">使用您的仓储系统账号继续</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" type="password" show-password placeholder="请输入密码" size="large" />
          </el-form-item>
          <div class="login-options"><el-checkbox>保持登录</el-checkbox><span>遇到问题？请联系管理员</span></div>
          <el-button type="primary" size="large" :loading="loading" class="submit" @click="submit">进入 Firefly</el-button>
        </el-form>
      </div>
      <footer>© 2026 Firefly Logistics · 仓储物流管理平台</footer>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100vh; grid-template-columns: 1.15fr .85fr; background: #fff; }
.story-panel { position: relative; display: flex; align-items: center; min-height: 100vh; padding: 7vw; overflow: hidden; color: #fff; background: #0b1f33; }
.story-content { position: relative; z-index: 2; max-width: 650px; }
.kicker { margin-top: 110px; color: #f3c969; font-size: 11px; font-weight: 700; letter-spacing: 2.6px; }
h1 { margin: 20px 0 26px; font-size: clamp(42px, 4.3vw, 68px); font-weight: 650; line-height: 1.18; letter-spacing: -2.4px; }
.story-content > p { max-width: 580px; color: #aebbc8; font-size: 16px; line-height: 1.9; }
.metrics { display: flex; gap: 54px; margin-top: 66px; padding-top: 26px; border-top: 1px solid rgba(255,255,255,.11); }
.metrics div { display: flex; flex-direction: column; }.metrics strong { color: #f3c969; font-size: 23px; font-weight: 650; }.metrics span { margin-top: 8px; color: #8799aa; font-size: 11px; }
.grid { position: absolute; inset: 0; opacity: .035; background-image: linear-gradient(#fff 1px,transparent 1px),linear-gradient(90deg,#fff 1px,transparent 1px); background-size: 42px 42px; }
.orb { position: absolute; border-radius: 50%; filter: blur(4px); }.orb-one { right: -160px; top: -160px; width: 500px; height: 500px; border: 1px solid rgba(243,201,105,.16); box-shadow: inset 0 0 80px rgba(243,201,105,.05); }.orb-two { right: 14%; bottom: -260px; width: 520px; height: 520px; background: rgba(49,108,126,.1); }
.login-panel { display: flex; min-height: 100vh; align-items: center; justify-content: center; padding: 50px; position: relative; }
.login-card { width: 100%; max-width: 420px; }.mobile-logo { display: none; }.hello { color: #a07117; font-size: 11px; font-weight: 750; letter-spacing: 1.8px; }.login-card h2 { margin: 10px 0 8px; color: #10283f; font-size: 32px; letter-spacing: -1px; }.hint { margin: 0 0 36px; color: #8491a0; font-size: 14px; }
.login-card :deep(.el-form-item__label) { color: #405469; font-size: 13px; font-weight: 600; }.login-card :deep(.el-input__wrapper) { min-height: 48px; border-radius: 10px; box-shadow: 0 0 0 1px #dce3e9 inset; }.login-card :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #16314c inset; }
.login-options { display: flex; align-items: center; justify-content: space-between; margin: -2px 0 24px; color: #8995a3; font-size: 11px; }.submit { width: 100%; height: 50px; border-radius: 10px; font-weight: 650; }
footer { position: absolute; bottom: 24px; color: #adb6c0; font-size: 10px; }
@media(max-width: 1000px){.login-page{grid-template-columns:1fr}.story-panel{display:none}.mobile-logo{display:block;margin-bottom:50px}.mobile-logo :deep(.brand){color:#0b1f33}.mobile-logo :deep(.wordmark span){color:#738195}}
</style>
