import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.vue'

const profile = {
  name: '本地 MCP 网关',
  updatedBy: 'system',
  updatedAt: '2026-08-25T00:00:00Z',
}

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  }))
}

function emptyResponse() {
  return Promise.resolve(new Response(null, { status: 204 }))
}

describe('控制面角色冒烟流程', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('平台管理员登录后显示写入入口并可保存', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => jsonResponse({
        username: 'admin',
        role: 'PLATFORM_ADMIN',
        csrfToken: 'csrf-admin',
      }))
      .mockImplementationOnce(() => jsonResponse(profile))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse({ ...profile, name: '研发 MCP 网关', updatedBy: 'admin' }))

    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('平台管理员')
    expect(wrapper.get('button[type="submit"]').text()).toContain('保存修改')

    await wrapper.get('input[maxlength="100"]').setValue('研发 MCP 网关')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenLastCalledWith('/api/management/gateway-profile', expect.objectContaining({
      method: 'PUT',
    }))
    const lastOptions = fetchMock.mock.calls.at(-1)?.[1]
    expect(new Headers(lastOptions?.headers).get('X-XSRF-TOKEN')).toBe('csrf-admin')
    expect(wrapper.text()).toContain('网关基本信息已保存')
  })

  it('审计查看者登录后只能读取且没有写入入口', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => jsonResponse({
        username: 'auditor',
        role: 'AUDIT_VIEWER',
        csrfToken: 'csrf-auditor',
      }))
      .mockImplementationOnce(() => jsonResponse(profile))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))

    const wrapper = mount(App)
    await wrapper.get('input').setValue('auditor')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('审计查看者')
    expect(wrapper.text()).toContain('不能修改系统状态')
    expect(wrapper.get('input[maxlength="100"]').attributes('readonly')).toBeDefined()
    expect(wrapper.find('button[type="submit"]').exists()).toBe(false)
  })

  it('管理员创建 Agent 后只在当前流程展示一次性 API Key', async () => {
    const createdAgent = {
      id: 7,
      name: '研发助手',
      apiKeyPrefix: 'mgw_12345678',
      roleIds: [],
      createdAt: '2026-08-25T00:00:00Z',
      updatedAt: '2026-08-25T00:00:00Z',
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => jsonResponse({
        username: 'admin',
        role: 'PLATFORM_ADMIN',
        csrfToken: 'csrf-admin',
      }))
      .mockImplementationOnce(() => jsonResponse(profile))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse({
        id: 7,
        name: '研发助手',
        apiKeyPrefix: 'mgw_12345678',
        apiKey: 'mgw_once-only-secret-value',
      }, 201))
      .mockImplementationOnce(() => jsonResponse([createdAgent]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))

    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const agentForm = wrapper.findAll('form').find(form => form.text().includes('Agent 名称'))
    expect(agentForm).toBeDefined()
    await agentForm!.get('input').setValue('研发助手')
    await agentForm!.trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('/api/management/agents', expect.objectContaining({ method: 'POST' }))
    expect(wrapper.text()).toContain('API Key 仅显示这一次')
    expect(wrapper.text()).toContain('mgw_once-only-secret-value')

    await wrapper.get('.credential button').trigger('click')
    expect(wrapper.text()).not.toContain('mgw_once-only-secret-value')
  })

  it('管理员可以更新并删除已有 Agent', async () => {
    const existingAgent = {
      id: 9,
      name: '待修改 Agent',
      apiKeyPrefix: 'mgw_abcdefgh',
      roleIds: [],
      createdAt: '2026-08-25T00:00:00Z',
      updatedAt: '2026-08-25T00:00:00Z',
    }
    const updatedAgent = { ...existingAgent, name: '已修改 Agent' }
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => jsonResponse({ username: 'admin', role: 'PLATFORM_ADMIN', csrfToken: 'csrf-admin' }))
      .mockImplementationOnce(() => jsonResponse(profile))
      .mockImplementationOnce(() => jsonResponse([existingAgent]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse(updatedAgent))
      .mockImplementationOnce(() => jsonResponse([updatedAgent]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => emptyResponse())
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
    vi.spyOn(window, 'prompt').mockReturnValue('已修改 Agent')
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const agentArticle = wrapper.findAll('article').find(article => article.get('h3').text() === 'Agent')!
    await agentArticle.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith('/api/management/agents/9', expect.objectContaining({ method: 'PUT' }))
    expect(wrapper.text()).toContain('已修改 Agent')

    const updatedArticle = wrapper.findAll('article').find(article => article.get('h3').text() === 'Agent')!
    await updatedArticle.findAll('button').find(button => button.text() === '删除')!.trigger('click')
    await flushPromises()
    expect(fetchMock).toHaveBeenCalledWith('/api/management/agents/9', expect.objectContaining({ method: 'DELETE' }))
    expect(wrapper.text()).not.toContain('已修改 Agent')
  })

  it('管理员可以通过薄客户端关联 Agent 与角色', async () => {
    const agent = {
      id: 3,
      name: '授权 Agent',
      apiKeyPrefix: 'mgw_87654321',
      roleIds: [],
      createdAt: '2026-08-25T00:00:00Z',
      updatedAt: '2026-08-25T00:00:00Z',
    }
    const role = { id: 5, name: '查询角色', description: '', toolSetIds: [] }
    const toolSet = { id: 8, name: '查询工具集', description: '', toolNames: ['crm.get_user'] }
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockImplementationOnce(() => jsonResponse({ username: 'admin', role: 'PLATFORM_ADMIN', csrfToken: 'csrf-admin' }))
      .mockImplementationOnce(() => jsonResponse(profile))
      .mockImplementationOnce(() => jsonResponse([agent]))
      .mockImplementationOnce(() => jsonResponse([role]))
      .mockImplementationOnce(() => jsonResponse([toolSet]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => jsonResponse([]))
      .mockImplementationOnce(() => emptyResponse())
      .mockImplementationOnce(() => jsonResponse([{ ...agent, roleIds: [5] }]))
      .mockImplementationOnce(() => jsonResponse([role]))
      .mockImplementationOnce(() => jsonResponse([toolSet]))

    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const relationForm = wrapper.findAll('form').find(form => form.text().includes('Agent ↔ 角色'))!
    await relationForm.trigger('submit')
    await flushPromises()

    expect(fetchMock).toHaveBeenCalledWith('/api/management/agents/3/roles/5', expect.objectContaining({ method: 'POST' }))
    expect(wrapper.text()).toContain('已授予 Agent 角色')
  })

  it('管理员可以登记上游、校验发布工具并从版本创建新草稿', async () => {
    let upstreams: unknown[] = []
    let drafts: unknown[] = []
    let versions: unknown[] = []
    const upstream = {
      id: 11,
      serviceId: 'inventory',
      displayName: '库存服务',
      baseUrl: 'http://inventory.internal',
      connectivityStatus: 'CONNECTED',
      connectivityError: '',
      lastCheckedAt: '2026-08-25T00:00:00Z',
    }
    const draft = {
      id: 21,
      toolName: 'inventory.read',
      displayName: '读取库存',
      riskLevel: 'READ_ONLY',
      upstreamId: 11,
      serviceId: 'inventory',
      httpMethod: 'GET',
      path: '/inventory',
      requestConfig: '{"query":"sku"}',
      responseConfig: '{"items":"$.items"}',
      validationStatus: 'UNVALIDATED',
      validationErrors: [],
    }
    const version = {
      id: 31,
      toolName: 'inventory.read',
      versionNumber: 1,
      displayName: '读取库存',
      riskLevel: 'READ_ONLY',
      upstreamId: 11,
      serviceId: 'inventory',
      httpMethod: 'GET',
      path: '/inventory',
      requestConfig: '{"query":"sku"}',
      responseConfig: '{"items":"$.items"}',
      current: true,
      publishedBy: 'admin',
      publishedAt: '2026-08-25T00:00:00Z',
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation((input, options) => {
      const path = String(input)
      const method = options?.method ?? 'GET'
      if (path === '/api/auth/login') {
        return jsonResponse({ username: 'admin', role: 'PLATFORM_ADMIN', csrfToken: 'csrf-admin' })
      }
      if (path === '/api/management/gateway-profile') return jsonResponse(profile)
      if (path === '/api/management/agents' || path === '/api/management/roles' || path === '/api/management/tool-sets') {
        return jsonResponse([])
      }
      if (path === '/api/management/upstreams' && method === 'GET') return jsonResponse(upstreams)
      if (path === '/api/management/upstreams' && method === 'POST') {
        upstreams = [upstream]
        return jsonResponse(upstream, 201)
      }
      if (path === '/api/management/tool-drafts' && method === 'GET') return jsonResponse(drafts)
      if (path === '/api/management/tool-drafts' && method === 'POST') {
        drafts = [draft]
        return jsonResponse(draft, 201)
      }
      if (path === '/api/management/tool-drafts/21/validate') {
        drafts = [{ ...draft, validationStatus: 'VALID' }]
        return jsonResponse(drafts[0])
      }
      if (path === '/api/management/tool-drafts/22' && method === 'PUT') {
        const body = JSON.parse(String(options?.body))
        drafts = [{ ...draft, ...body, id: 22 }]
        return jsonResponse(drafts[0])
      }
      if (path === '/api/management/tool-drafts/21/publish') {
        drafts = []
        versions = [version]
        return jsonResponse(version, 201)
      }
      if (path === '/api/management/tool-versions' && method === 'GET') return jsonResponse(versions)
      if (path === '/api/management/tool-versions/31/draft') {
        drafts = [{ ...draft, id: 22 }]
        return jsonResponse(drafts[0], 201)
      }
      throw new Error(`未模拟请求：${method} ${path}`)
    })

    const wrapper = mount(App)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const upstreamForm = wrapper.findAll('form').find(form => form.text().includes('服务标识'))!
    const upstreamInputs = upstreamForm.findAll('input')
    await upstreamInputs[0].setValue('inventory')
    await upstreamInputs[1].setValue('库存服务')
    await upstreamInputs[2].setValue('http://inventory.internal')
    await upstreamForm.trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('连通')

    const draftForm = wrapper.findAll('form').find(form => form.text().includes('稳定名称'))!
    const draftInputs = draftForm.findAll('input')
    await draftInputs[0].setValue('inventory.read')
    await draftInputs[1].setValue('读取库存')
    await draftInputs[2].setValue('GET')
    await draftInputs[3].setValue('/inventory')
    await draftForm.trigger('submit')
    await flushPromises()

    const draftArticle = wrapper.findAll('article').find(article => article.get('h3').text() === '工具草稿')!
    await draftArticle.findAll('button').find(button => button.text() === '校验')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('VALID')
    await draftArticle.findAll('button').find(button => button.text() === '发布')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('inventory.read · v1')

    const versionArticle = wrapper.findAll('article').find(article => article.get('h3').text() === '不可变发布版本')!
    await versionArticle.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('已从发布版本创建新草稿')
    expect(fetchMock).toHaveBeenCalledWith('/api/management/tool-versions/31/draft', expect.objectContaining({ method: 'POST' }))

    vi.spyOn(window, 'prompt')
      .mockReturnValueOnce('读取库存新版')
      .mockReturnValueOnce('/inventory/v2')
    const copiedDraftArticle = wrapper.findAll('article').find(article => article.get('h3').text() === '工具草稿')!
    await copiedDraftArticle.findAll('button').find(button => button.text() === '编辑')!.trigger('click')
    await flushPromises()
    const updateCall = fetchMock.mock.calls.find(([input, options]) =>
      String(input) === '/api/management/tool-drafts/22' && options?.method === 'PUT',
    )
    expect(updateCall).toBeDefined()
    expect(JSON.parse(String(updateCall?.[1]?.body))).toMatchObject({
      requestConfig: '{"query":"sku"}',
      responseConfig: '{"items":"$.items"}',
    })
  })
})
