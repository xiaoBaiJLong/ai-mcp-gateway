<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  addAgentRole,
  addRoleToolSet,
  createAgent,
  createRole,
  createToolSet,
  deleteAgent,
  deleteRole,
  deleteToolSet,
  getAgentPermissions,
  getAgents,
  getGatewayProfile,
  getRoles,
  getToolSets,
  login,
  removeAgentRole,
  removeRoleToolSet,
  resetAgentApiKey,
  updateAgent,
  updateGatewayProfile,
  updateRole,
  updateToolSet,
  type Agent,
  type AgentCredential,
  type GatewayProfile,
  type Role,
  type Session,
  type ToolSet,
} from './api'

const username = ref('admin')
const password = ref('666666')
const session = ref<Session | null>(null)
const profile = ref<GatewayProfile | null>(null)
const nameDraft = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const loading = ref(false)
const agents = ref<Agent[]>([])
const roles = ref<Role[]>([])
const toolSets = ref<ToolSet[]>([])
const newAgentName = ref('')
const newRoleName = ref('')
const newRoleDescription = ref('')
const newToolSetName = ref('')
const newToolSetDescription = ref('')
const newToolNames = ref('')
const selectedAgentId = ref<number | null>(null)
const selectedRoleId = ref<number | null>(null)
const selectedToolSetId = ref<number | null>(null)
const oneTimeCredential = ref<AgentCredential | null>(null)
const permissionToolNames = ref<string[]>([])

const canWrite = computed(() => session.value?.role === 'PLATFORM_ADMIN')

async function submitLogin() {
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    session.value = await login(username.value, password.value)
    profile.value = await getGatewayProfile()
    nameDraft.value = profile.value.name
    await refreshAuthorization()
  } catch (error) {
    session.value = null
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

async function refreshAuthorization() {
  const [agentList, roleList, toolSetList] = await Promise.all([
    getAgents(),
    getRoles(),
    getToolSets(),
  ])
  agents.value = agentList
  roles.value = roleList
  toolSets.value = toolSetList
  if (!agentList.some(agent => agent.id === selectedAgentId.value)) selectedAgentId.value = agentList[0]?.id ?? null
  if (!roleList.some(role => role.id === selectedRoleId.value)) selectedRoleId.value = roleList[0]?.id ?? null
  if (!toolSetList.some(toolSet => toolSet.id === selectedToolSetId.value)) selectedToolSetId.value = toolSetList[0]?.id ?? null
}

async function runWrite(action: () => Promise<void>, message: string) {
  if (!canWrite.value) return
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await action()
    successMessage.value = message
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '操作失败'
  } finally {
    loading.value = false
  }
}

async function submitAgent() {
  await runWrite(async () => {
    oneTimeCredential.value = await createAgent(newAgentName.value)
    newAgentName.value = ''
    await refreshAuthorization()
  }, 'Agent 已创建，请立即保存一次性 API Key')
}

async function resetKey(agent: Agent) {
  await runWrite(async () => {
    oneTimeCredential.value = await resetAgentApiKey(agent.id)
    await refreshAuthorization()
  }, 'API Key 已重置，旧 Key 已失效')
}

async function editAgent(agent: Agent) {
  const name = window.prompt('Agent 名称', agent.name)?.trim()
  if (!name) return
  await runWrite(async () => {
    await updateAgent(agent.id, name)
    await refreshAuthorization()
  }, 'Agent 已更新')
}

async function removeAgent(agent: Agent) {
  if (!window.confirm(`确认删除 Agent“${agent.name}”？`)) return
  await runWrite(async () => {
    await deleteAgent(agent.id)
    await refreshAuthorization()
  }, 'Agent 已删除')
}

async function submitRole() {
  await runWrite(async () => {
    await createRole(newRoleName.value, newRoleDescription.value)
    newRoleName.value = ''
    newRoleDescription.value = ''
    await refreshAuthorization()
  }, '角色已创建')
}

async function editRole(role: Role) {
  const name = window.prompt('角色名称', role.name)?.trim()
  if (!name) return
  const description = window.prompt('角色说明', role.description)
  if (description === null) return
  await runWrite(async () => {
    await updateRole(role.id, name, description.trim())
    await refreshAuthorization()
  }, '角色已更新')
}

async function removeRole(role: Role) {
  if (!window.confirm(`确认删除角色“${role.name}”？`)) return
  await runWrite(async () => {
    await deleteRole(role.id)
    await refreshAuthorization()
  }, '角色已删除')
}

async function submitToolSet() {
  const members = newToolNames.value.split(/[\n,]/).map(value => value.trim()).filter(Boolean)
  await runWrite(async () => {
    await createToolSet(newToolSetName.value, newToolSetDescription.value, members)
    newToolSetName.value = ''
    newToolSetDescription.value = ''
    newToolNames.value = ''
    await refreshAuthorization()
  }, '工具集已按明确成员保存')
}

async function editToolSet(toolSet: ToolSet) {
  const name = window.prompt('工具集名称', toolSet.name)?.trim()
  if (!name) return
  const description = window.prompt('工具集说明', toolSet.description)
  if (description === null) return
  const members = window.prompt('明确工具名称（逗号分隔）', toolSet.toolNames.join(','))
  if (members === null) return
  const toolNames = members.split(',').map(value => value.trim()).filter(Boolean)
  await runWrite(async () => {
    await updateToolSet(toolSet.id, name, description.trim(), toolNames)
    await refreshAuthorization()
  }, '工具集已更新')
}

async function removeToolSet(toolSet: ToolSet) {
  if (!window.confirm(`确认删除工具集“${toolSet.name}”？`)) return
  await runWrite(async () => {
    await deleteToolSet(toolSet.id)
    await refreshAuthorization()
  }, '工具集已删除')
}

async function changeAgentRole(remove = false) {
  if (selectedAgentId.value === null || selectedRoleId.value === null) return
  await runWrite(async () => {
    const operation = remove ? removeAgentRole : addAgentRole
    await operation(selectedAgentId.value!, selectedRoleId.value!)
    await refreshAuthorization()
  }, remove ? '已撤销 Agent 角色' : '已授予 Agent 角色')
}

async function changeRoleToolSet(remove = false) {
  if (selectedRoleId.value === null || selectedToolSetId.value === null) return
  await runWrite(async () => {
    const operation = remove ? removeRoleToolSet : addRoleToolSet
    await operation(selectedRoleId.value!, selectedToolSetId.value!)
    await refreshAuthorization()
  }, remove ? '已撤销角色工具集' : '已关联角色工具集')
}

async function showPermissions(agentId: number) {
  try {
    permissionToolNames.value = (await getAgentPermissions(agentId)).toolNames
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '读取权限失败'
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

    <template v-else>
    <section class="card" aria-labelledby="profile-title">
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

    <section class="card" aria-labelledby="authorization-title">
      <h2 id="authorization-title">Agent 与工具权限</h2>
      <p v-if="!canWrite" class="readonly-note">审计查看者可读取 Agent、角色、工具集和权限结果，不能执行任何修改。</p>

      <aside v-if="oneTimeCredential" class="credential" role="status">
        <strong>API Key 仅显示这一次</strong>
        <code>{{ oneTimeCredential.apiKey }}</code>
        <button type="button" @click="oneTimeCredential = null">我已保存并关闭</button>
      </aside>

      <div class="management-grid">
        <article>
          <h3>Agent</h3>
          <form v-if="canWrite" class="compact-form" @submit.prevent="submitAgent">
            <label>Agent 名称<input v-model="newAgentName" required maxlength="100" /></label>
            <button :disabled="loading" type="submit">创建 Agent</button>
          </form>
          <ul>
            <li v-for="agent in agents" :key="agent.id">
              <span><strong>{{ agent.name }}</strong><small>Key 前缀 {{ agent.apiKeyPrefix }}</small></span>
              <span class="actions">
                <button type="button" @click="showPermissions(agent.id)">查看权限</button>
                <button v-if="canWrite" type="button" @click="editAgent(agent)">编辑</button>
                <button v-if="canWrite" type="button" @click="resetKey(agent)">重置 Key</button>
                <button v-if="canWrite" type="button" @click="removeAgent(agent)">删除</button>
              </span>
            </li>
          </ul>
          <p class="permission-result">权限结果：{{ permissionToolNames.length ? permissionToolNames.join('、') : '默认拒绝（空）' }}</p>
        </article>

        <article>
          <h3>角色</h3>
          <form v-if="canWrite" class="compact-form" @submit.prevent="submitRole">
            <label>角色名称<input v-model="newRoleName" required maxlength="100" /></label>
            <label>说明<input v-model="newRoleDescription" maxlength="500" /></label>
            <button :disabled="loading" type="submit">创建角色</button>
          </form>
          <ul><li v-for="role in roles" :key="role.id">
            <span><strong>{{ role.name }}</strong><small>{{ role.description || '无说明' }}</small></span>
            <span v-if="canWrite" class="actions"><button type="button" @click="editRole(role)">编辑</button><button type="button" @click="removeRole(role)">删除</button></span>
          </li></ul>
        </article>

        <article>
          <h3>工具集</h3>
          <form v-if="canWrite" class="compact-form" @submit.prevent="submitToolSet">
            <label>工具集名称<input v-model="newToolSetName" required maxlength="100" /></label>
            <label>说明<input v-model="newToolSetDescription" maxlength="500" /></label>
            <label>明确工具名称<textarea v-model="newToolNames" placeholder="crm.get_user，每行或逗号分隔" /></label>
            <button :disabled="loading" type="submit">保存工具集</button>
          </form>
          <ul><li v-for="toolSet in toolSets" :key="toolSet.id">
            <span><strong>{{ toolSet.name }}</strong><small>{{ toolSet.toolNames.join('、') || '空工具集' }}</small></span>
            <span v-if="canWrite" class="actions"><button type="button" @click="editToolSet(toolSet)">编辑</button><button type="button" @click="removeToolSet(toolSet)">删除</button></span>
          </li></ul>
        </article>
      </div>

      <div v-if="canWrite" class="relation-grid">
        <form class="compact-form" @submit.prevent="changeAgentRole(false)">
          <h3>Agent ↔ 角色</h3>
          <select v-model="selectedAgentId" required><option v-for="agent in agents" :key="agent.id" :value="agent.id">{{ agent.name }}</option></select>
          <select v-model="selectedRoleId" required><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select>
          <span class="actions"><button type="submit">授予</button><button type="button" @click="changeAgentRole(true)">撤销</button></span>
        </form>
        <form class="compact-form" @submit.prevent="changeRoleToolSet(false)">
          <h3>角色 ↔ 工具集</h3>
          <select v-model="selectedRoleId" required><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select>
          <select v-model="selectedToolSetId" required><option v-for="toolSet in toolSets" :key="toolSet.id" :value="toolSet.id">{{ toolSet.name }}</option></select>
          <span class="actions"><button type="submit">关联</button><button type="button" @click="changeRoleToolSet(true)">撤销</button></span>
        </form>
      </div>
    </section>
    </template>

    <p v-if="errorMessage" class="message error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="message success" role="status">{{ successMessage }}</p>
  </main>
</template>
