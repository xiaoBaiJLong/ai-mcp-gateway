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
