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
})
