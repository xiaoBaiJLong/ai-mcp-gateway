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

    const wrapper = mount(App)
    await wrapper.get('input').setValue('auditor')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('审计查看者')
    expect(wrapper.text()).toContain('不能修改系统状态')
    expect(wrapper.get('input[maxlength="100"]').attributes('readonly')).toBeDefined()
    expect(wrapper.find('button[type="submit"]').exists()).toBe(false)
  })
})
