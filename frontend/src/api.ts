export type ControlPlaneRole = 'PLATFORM_ADMIN' | 'AUDIT_VIEWER'

export interface Session {
  username: string
  role: ControlPlaneRole
  csrfToken: string
}

export interface GatewayProfile {
  name: string
  updatedBy: string
  updatedAt: string
}

export interface Agent {
  id: number
  name: string
  apiKeyPrefix: string
  roleIds: number[]
  createdAt: string
  updatedAt: string
}

export interface AgentCredential {
  id: number
  name: string
  apiKeyPrefix: string
  apiKey: string
}

export interface Role {
  id: number
  name: string
  description: string
  toolSetIds: number[]
}

export interface ToolSet {
  id: number
  name: string
  description: string
  toolNames: string[]
}

export interface PermissionResult {
  toolNames: string[]
}

interface ApiErrorBody {
  message?: string
}

let csrfToken = ''

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (csrfToken && options.method && options.method !== 'GET') {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const error = (await response.json().catch(() => ({}))) as ApiErrorBody
    throw new Error(error.message ?? `请求失败（${response.status}）`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json() as Promise<T>
}

export async function login(username: string, password: string): Promise<Session> {
  const session = await request<Session>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  csrfToken = session.csrfToken
  return session
}

export function getGatewayProfile(): Promise<GatewayProfile> {
  return request<GatewayProfile>('/api/management/gateway-profile')
}

export function updateGatewayProfile(name: string): Promise<GatewayProfile> {
  return request<GatewayProfile>('/api/management/gateway-profile', {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function getAgents(): Promise<Agent[]> {
  return request<Agent[]>('/api/management/agents')
}

export function createAgent(name: string): Promise<AgentCredential> {
  return request<AgentCredential>('/api/management/agents', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function updateAgent(id: number, name: string): Promise<Agent> {
  return request<Agent>(`/api/management/agents/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function deleteAgent(id: number): Promise<void> {
  return request<void>(`/api/management/agents/${id}`, { method: 'DELETE' })
}

export function resetAgentApiKey(id: number): Promise<AgentCredential> {
  return request<AgentCredential>(`/api/management/agents/${id}/reset-api-key`, {
    method: 'POST',
  })
}

export function getRoles(): Promise<Role[]> {
  return request<Role[]>('/api/management/roles')
}

export function createRole(name: string, description: string): Promise<Role> {
  return request<Role>('/api/management/roles', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  })
}

export function updateRole(id: number, name: string, description: string): Promise<Role> {
  return request<Role>(`/api/management/roles/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description }),
  })
}

export function deleteRole(id: number): Promise<void> {
  return request<void>(`/api/management/roles/${id}`, { method: 'DELETE' })
}

export function getToolSets(): Promise<ToolSet[]> {
  return request<ToolSet[]>('/api/management/tool-sets')
}

export function createToolSet(name: string, description: string, toolNames: string[]): Promise<ToolSet> {
  return request<ToolSet>('/api/management/tool-sets', {
    method: 'POST',
    body: JSON.stringify({ name, description, toolNames }),
  })
}

export function updateToolSet(
  id: number,
  name: string,
  description: string,
  toolNames: string[],
): Promise<ToolSet> {
  return request<ToolSet>(`/api/management/tool-sets/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, description, toolNames }),
  })
}

export function deleteToolSet(id: number): Promise<void> {
  return request<void>(`/api/management/tool-sets/${id}`, { method: 'DELETE' })
}

export function addAgentRole(agentId: number, roleId: number): Promise<void> {
  return request<void>(`/api/management/agents/${agentId}/roles/${roleId}`, { method: 'POST' })
}

export function removeAgentRole(agentId: number, roleId: number): Promise<void> {
  return request<void>(`/api/management/agents/${agentId}/roles/${roleId}`, { method: 'DELETE' })
}

export function addRoleToolSet(roleId: number, toolSetId: number): Promise<void> {
  return request<void>(`/api/management/roles/${roleId}/tool-sets/${toolSetId}`, { method: 'POST' })
}

export function removeRoleToolSet(roleId: number, toolSetId: number): Promise<void> {
  return request<void>(`/api/management/roles/${roleId}/tool-sets/${toolSetId}`, { method: 'DELETE' })
}

export function getAgentPermissions(agentId: number): Promise<PermissionResult> {
  return request<PermissionResult>(`/api/management/agents/${agentId}/permissions`)
}
