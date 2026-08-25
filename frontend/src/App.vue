<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  getGatewayProfile,
  login,
  updateGatewayProfile,
  type GatewayProfile,
  type Session,
} from './api'

const username = ref('admin')
const password = ref('666666')
const session = ref<Session | null>(null)
const profile = ref<GatewayProfile | null>(null)
const nameDraft = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)

const canWrite = computed(() => session.value?.role === 'PLATFORM_ADMIN')

async function submitLogin() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    session.value = await login(username.value, password.value)
    profile.value = await getGatewayProfile()
    nameDraft.value = profile.value.name
  } catch (error) {
    session.value = null
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!canWrite.value) return
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    profile.value = await updateGatewayProfile(nameDraft.value)
    nameDraft.value = profile.value.name
    successMessage.value = '网关基本信息已保存'
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="shell">
    <section class="card brand">
      <p class="eyebrow">AI MCP GATEWAY</p>
      <h1>MCP 网关控制台</h1>
      <p>本地单实例 · 双角色边界验证</p>
    </section>

    <section v-if="!session" class="card" aria-labelledby="login-title">
      <h2 id="login-title">控制面登录</h2>
      <form @submit.prevent="submitLogin">
        <label>
          账号
          <input v-model="username" autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" />
        </label>
        <button :disabled="loading" type="submit">{{ loading ? '登录中…' : '登录' }}</button>
      </form>
      <p class="hint">平台管理员：admin / 666666</p>
      <p class="hint">审计查看者：auditor / 666666</p>
    </section>

    <section v-else class="card" aria-labelledby="profile-title">
      <header class="session-header">
        <div>
          <p class="eyebrow">当前账号</p>
          <strong>{{ session.username }}</strong>
        </div>
        <span class="role">{{ canWrite ? '平台管理员' : '审计查看者' }}</span>
      </header>

      <h2 id="profile-title">网关基本信息</h2>
      <form v-if="profile" @submit.prevent="saveProfile">
        <label>
          网关名称
          <input v-model="nameDraft" :readonly="!canWrite" maxlength="100" />
        </label>
        <button v-if="canWrite" :disabled="loading" type="submit">
          {{ loading ? '保存中…' : '保存修改' }}
        </button>
        <p v-else class="readonly-note">审计查看者为只读角色，不能修改系统状态。</p>
        <dl>
          <div><dt>最近修改人</dt><dd>{{ profile.updatedBy }}</dd></div>
          <div><dt>最近修改时间</dt><dd>{{ new Date(profile.updatedAt).toLocaleString('zh-CN') }}</dd></div>
        </dl>
      </form>
    </section>

    <p v-if="errorMessage" class="message error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="message success" role="status">{{ successMessage }}</p>
  </main>
</template>
