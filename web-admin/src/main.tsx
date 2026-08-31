import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { Alert, Button, ConfigProvider, Descriptions, Drawer, Form, Input, Layout, Menu, Select, Space, Table, Tag, Typography, message } from 'antd';
import 'antd/dist/reset.css';

type Operation = { serviceName: string; method: string; path: string; operationId?: string; summary: string; description: string; deprecated: boolean; supported: boolean; unsupportedReason?: string };
type ToolDraft = { serviceName: string; method: string; path: string; initialName: string; initialDescription: string; inputSchema: unknown };
type Tool = { id: string; name: string; description: string; enabled: boolean; mapping: { serviceName: string; method: string; path: string } };
type ApiResponse<T> = { code: string; message: string; data: T };

const { Header, Content, Sider } = Layout;

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { headers: { 'Content-Type': 'application/json', ...options?.headers }, ...options });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok) throw new Error(payload.message || '请求失败');
  return payload.data;
}

function App() {
  const [services, setServices] = useState<string[]>([]);
  const [serviceName, setServiceName] = useState<string>();
  const [operations, setOperations] = useState<Operation[]>([]);
  const [tools, setTools] = useState<Tool[]>([]);
  const [draft, setDraft] = useState<ToolDraft>();
  const [error, setError] = useState<string>();
  const [form] = Form.useForm<{ name: string; description: string }>();

  const loadSources = async () => {
    try {
      setError(undefined);
      const sources = await request<{ name: string }[]>('/tool-sources');
      setServices(sources.map((source) => source.name));
    } catch (reason) { setError(reason instanceof Error ? reason.message : '无法获取业务服务'); }
  };
  const loadTools = async () => {
    try { setTools(await request<Tool[]>('/tools')); }
    catch (reason) { setError(reason instanceof Error ? reason.message : '无法读取 MCP 工具'); }
  };
  useEffect(() => { void loadSources(); void loadTools(); }, []);

  const loadOperations = async (name: string) => {
    setServiceName(name); setDraft(undefined);
    try {
      setError(undefined);
      const result = await request<{ operations: Operation[] }>(`/tool-sources/${encodeURIComponent(name)}/operations`);
      setOperations(result.operations);
    } catch (reason) { setOperations([]); setError(reason instanceof Error ? reason.message : '获取 OpenAPI 文档失败'); }
  };
  const openDraft = async (operation: Operation) => {
    if (!operation.supported) return;
    try {
      setError(undefined);
      const nextDraft = await request<ToolDraft>('/tool-drafts', { method: 'POST', body: JSON.stringify(operation) });
      setDraft(nextDraft); form.setFieldsValue({ name: nextDraft.initialName, description: nextDraft.initialDescription });
    } catch (reason) { setError(reason instanceof Error ? reason.message : '无法生成工具映射草稿'); }
  };
  const saveTool = async () => {
    if (!draft) return;
    try {
      const values = await form.validateFields();
      await request<Tool>('/tools', { method: 'POST', body: JSON.stringify({ ...draft, ...values }) });
      message.success('MCP 工具已创建并默认启用'); setDraft(undefined); await loadTools();
    } catch (reason) { if (reason instanceof Error) setError(reason.message); }
  };

  return <ConfigProvider theme={{ token: { colorPrimary: '#1d1d1f', borderRadius: 10 } }}>
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={220}>
        <Typography.Title level={4} style={{ padding: '20px 24px', margin: 0 }}>MCP 网关</Typography.Title>
        <Menu selectedKeys={['tools']} items={[
          { key: 'tools', label: '工具配置' }, { key: 'manage', label: '工具管理', disabled: true },
          { key: 'collections', label: '工具集管理', disabled: true }, { key: 'agents', label: '智能体管理', disabled: true },
          { key: 'validation', label: 'MCP 验证', disabled: true },
        ]} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 32px' }}><Typography.Title level={3} style={{ lineHeight: '64px', margin: 0 }}>从 OpenAPI 导入 MCP 工具</Typography.Title></Header>
        <Content style={{ padding: 32, background: '#f5f5f7' }}><Space direction="vertical" size="large" style={{ display: 'flex' }}>
          {error && <Alert type="error" showIcon closable message={error} onClose={() => setError(undefined)} />}
          <section style={{ background: '#fff', padding: 24, borderRadius: 12 }}><Space>
            <Typography.Text strong>业务服务</Typography.Text>
            <Select placeholder="选择已发现的业务服务" style={{ width: 320 }} options={services.map((name) => ({ value: name, label: name }))} value={serviceName} onChange={(value) => void loadOperations(value)} />
            <Button onClick={() => void loadSources()}>刷新服务</Button>
          </Space></section>
          <section style={{ background: '#fff', padding: 24, borderRadius: 12 }}>
            <Typography.Title level={4}>OpenAPI operations</Typography.Title>
            <Table<Operation> rowKey={(item) => `${item.method}:${item.path}`} dataSource={operations} pagination={false} columns={[
              { title: '方法', dataIndex: 'method', width: 90 }, { title: '路径', dataIndex: 'path' },
              { title: '说明', dataIndex: 'summary', render: (value, item) => value || item.description || '—' },
              { title: '状态', render: (_, item) => <Space>{item.deprecated && <Tag color="orange">已废弃</Tag>}{!item.supported && <Tag color="red">当前版本不支持</Tag>}</Space> },
              { title: '操作', render: (_, item) => <Button type="link" disabled={!item.supported} onClick={() => void openDraft(item)}>{item.supported ? '查看映射草稿' : item.unsupportedReason}</Button> },
            ]} />
          </section>
          <section style={{ background: '#fff', padding: 24, borderRadius: 12 }}>
            <Typography.Title level={4}>已保存的 MCP 工具</Typography.Title>
            <Table<Tool> rowKey="id" dataSource={tools} pagination={false} columns={[
              { title: '名称', dataIndex: 'name' }, { title: '说明', dataIndex: 'description' },
              { title: '来源', render: (_, item) => `${item.mapping.serviceName} · ${item.mapping.method} ${item.mapping.path}` },
              { title: '状态', render: (_, item) => <Tag color={item.enabled ? 'green' : 'default'}>{item.enabled ? '启用' : '禁用'}</Tag> },
            ]} />
          </section>
        </Space></Content>
      </Layout>
    </Layout>
    <Drawer title="确认 MCP 工具映射" width={640} open={Boolean(draft)} onClose={() => setDraft(undefined)} extra={<Button type="primary" onClick={() => void saveTool()}>保存</Button>}>
      {draft && <Space direction="vertical" size="large" style={{ display: 'flex' }}>
        <Descriptions column={1} size="small" items={[{ key: 'source', label: '来源业务服务', children: draft.serviceName }, { key: 'mapping', label: 'HTTP Mapping', children: `${draft.method} ${draft.path}` }]} />
        <Form form={form} layout="vertical"><Form.Item name="name" label="MCP 工具名称" rules={[{ required: true }, { pattern: /^[A-Za-z0-9_.-]{1,128}$/, message: '仅允许 ASCII 字母、数字、下划线、连字符和点' }]}><Input /></Form.Item><Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item></Form>
        <Typography.Text strong>输入 schema</Typography.Text><pre style={{ overflow: 'auto', padding: 16, background: '#f5f5f7', borderRadius: 8 }}>{JSON.stringify(draft.inputSchema, null, 2)}</pre>
      </Space>}
    </Drawer>
  </ConfigProvider>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
