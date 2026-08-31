import React, { useEffect, useState } from 'react';
import ReactDOM from 'react-dom/client';
import { Alert, Button, ConfigProvider, Descriptions, Drawer, Form, Input, Layout, Menu, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import 'antd/dist/reset.css';

type Operation = { serviceName: string; method: string; path: string; operationId?: string; summary: string; description: string; deprecated: boolean; supported: boolean; unsupportedReason?: string };
type ToolDraft = { serviceName: string; method: string; path: string; initialName: string; initialDescription: string; inputSchema: unknown };
type Tool = { id: string; name: string; description: string; enabled: boolean; mapping: { serviceName: string; method: string; path: string } };
type Credential = { id: string; prefix: string; createdAt: string; enabled: boolean };
type AgentTool = { id: string; name: string; description: string; enabled: boolean };
type Agent = { id: string; name: string; description: string; createdAt: string; credentials: Credential[]; toolSnapshot: AgentTool[] };
type CreatedAgent = { id: string; name: string; description: string; createdAt: string; toolSnapshot: AgentTool[]; credential: Credential & { apiKey: string } };
type RevealedCredential = Credential & { apiKey: string };
type ApiResponse<T> = { code: string; message: string; data: T };

const { Header, Content, Sider } = Layout;

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { headers: { 'Content-Type': 'application/json', ...options?.headers }, ...options });
  const text = await response.text();
  let payload: ApiResponse<T>;
  try {
    payload = JSON.parse(text) as ApiResponse<T>;
  } catch {
    throw new Error(`服务返回了非 JSON 响应（HTTP ${response.status}）`);
  }
  if (!response.ok) throw new Error(payload.message || `请求失败（HTTP ${response.status}）`);
  return payload.data;
}

function App() {
  const [services, setServices] = useState<string[]>([]);
  const [serviceName, setServiceName] = useState<string>();
  const [operations, setOperations] = useState<Operation[]>([]);
  const [tools, setTools] = useState<Tool[]>([]);
  const [agents, setAgents] = useState<Agent[]>([]);
  const [draft, setDraft] = useState<ToolDraft>();
  const [agentDraft, setAgentDraft] = useState(false);
  const [configuredAgent, setConfiguredAgent] = useState<Agent>();
  const [draftToolIds, setDraftToolIds] = useState<string[]>([]);
  const [revealedCredential, setRevealedCredential] = useState<RevealedCredential>();
  const [page, setPage] = useState<'tools' | 'agents'>('tools');
  const [error, setError] = useState<string>();
  const [form] = Form.useForm<{ name: string; description: string }>();
  const [agentForm] = Form.useForm<{ name: string; description: string }>();

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
  const loadAgents = async () => {
    try { setAgents(await request<Agent[]>('/agents')); }
    catch (reason) { setError(reason instanceof Error ? reason.message : '无法读取智能体'); }
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
  const createAgent = async () => {
    try {
      const values = await agentForm.validateFields();
      const created = await request<CreatedAgent>('/agents', { method: 'POST', body: JSON.stringify(values) });
      setRevealedCredential(created.credential); setAgentDraft(false); agentForm.resetFields();
      setConfiguredAgent({ id: created.id, name: created.name, description: created.description, createdAt: created.createdAt, credentials: [], toolSnapshot: created.toolSnapshot });
      setDraftToolIds(created.toolSnapshot.map((tool) => tool.id)); await loadAgents();
      message.success('智能体已创建，API Key 仅显示本次');
    } catch (reason) { if (reason instanceof Error) setError(reason.message); }
  };
  const updateCredentialStatus = async (agent: Agent, enabled: boolean) => {
    try { await request<Agent>(`/agents/${agent.id}/credential`, { method: 'PATCH', body: JSON.stringify({ enabled }) }); await loadAgents(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : '无法更新凭证状态'); }
  };
  const resetCredential = async (agent: Agent) => {
    try {
      const credential = await request<RevealedCredential>(`/agents/${agent.id}/credential/reset`, { method: 'POST' });
      setRevealedCredential(credential); await loadAgents(); message.success('新 API Key 已生成，旧 Key 已失效');
    } catch (reason) { setError(reason instanceof Error ? reason.message : '无法重置凭证'); }
  };
  const publishToolSnapshot = async () => {
    if (!configuredAgent) return;
    try {
      await request<Agent>(`/agents/${configuredAgent.id}/tool-snapshot`, { method: 'PUT', body: JSON.stringify({ toolIds: draftToolIds }) });
      setConfiguredAgent(undefined); await loadAgents(); message.success('智能体工具快照已发布');
    } catch (reason) { setError(reason instanceof Error ? reason.message : '无法发布工具快照'); }
  };

  return <ConfigProvider theme={{ token: { colorPrimary: '#1d1d1f', borderRadius: 10 } }}>
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="light" width={220}>
        <Typography.Title level={4} style={{ padding: '20px 24px', margin: 0 }}>MCP 网关</Typography.Title>
        <Menu selectedKeys={[page]} onClick={({ key }) => { if (key === 'agents') { setPage('agents'); void loadAgents(); } if (key === 'tools') setPage('tools'); }} items={[
          { key: 'tools', label: '工具配置' }, { key: 'manage', label: '工具管理', disabled: true },
          { key: 'collections', label: '工具集管理', disabled: true }, { key: 'agents', label: '智能体管理' },
          { key: 'validation', label: 'MCP 验证', disabled: true },
        ]} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 32px' }}><Typography.Title level={3} style={{ lineHeight: '64px', margin: 0 }}>{page === 'tools' ? '从 OpenAPI 导入 MCP 工具' : '智能体管理'}</Typography.Title></Header>
        <Content style={{ padding: 32, background: '#f5f5f7' }}>{page === 'agents' ? <Space direction="vertical" size="large" style={{ display: 'flex' }}>
          {error && <Alert type="error" showIcon closable message={error} onClose={() => setError(undefined)} />}
          {revealedCredential && <Alert type="warning" showIcon closable message="请立即保存 API Key；关闭或刷新页面后将无法再次查看" description={<pre style={{ margin: '8px 0 0', whiteSpace: 'pre-wrap' }}>{revealedCredential.apiKey}</pre>} onClose={() => setRevealedCredential(undefined)} />}
          <section style={{ background: '#fff', padding: 24, borderRadius: 12 }}><Space><Button type="primary" onClick={() => setAgentDraft(true)}>创建智能体</Button><Button onClick={() => void loadAgents()}>刷新</Button></Space></section>
          <section style={{ background: '#fff', padding: 24, borderRadius: 12 }}><Table<Agent> rowKey="id" dataSource={agents} pagination={false} columns={[
            { title: '名称', dataIndex: 'name' }, { title: '说明', dataIndex: 'description', render: (value) => value || '—' },
            { title: '凭证状态', render: (_, item) => { const credential = item.credentials[0]; return credential ? <Space><Tag color={credential.enabled ? 'green' : 'default'}>{credential.enabled ? '启用' : '禁用'}</Tag><Switch size="small" checked={credential.enabled} onChange={(enabled) => void updateCredentialStatus(item, enabled)} /></Space> : '—'; } },
            { title: '工具快照', render: (_, item) => item.toolSnapshot.length ? item.toolSnapshot.map((tool) => <Tag key={tool.id}>{tool.name}</Tag>) : '未配置' },
            { title: '操作', render: (_, item) => <Space><Button type="link" onClick={() => void resetCredential(item)}>重置 Key</Button><Button type="link" onClick={() => { setConfiguredAgent(item); setDraftToolIds(item.toolSnapshot.map((tool) => tool.id)); }}>配置工具</Button></Space> },
          ]} /></section>
        </Space> : <Space direction="vertical" size="large" style={{ display: 'flex' }}>
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
        </Space>}</Content>
      </Layout>
    </Layout>
    <Drawer title="确认 MCP 工具映射" width={640} open={Boolean(draft)} onClose={() => setDraft(undefined)} extra={<Button type="primary" onClick={() => void saveTool()}>保存</Button>}>
      {draft && <Space direction="vertical" size="large" style={{ display: 'flex' }}>
        <Descriptions column={1} size="small" items={[{ key: 'source', label: '来源业务服务', children: draft.serviceName }, { key: 'mapping', label: 'HTTP Mapping', children: `${draft.method} ${draft.path}` }]} />
        <Form form={form} layout="vertical"><Form.Item name="name" label="MCP 工具名称" rules={[{ required: true }, { pattern: /^[A-Za-z0-9_.-]{1,128}$/, message: '仅允许 ASCII 字母、数字、下划线、连字符和点' }]}><Input /></Form.Item><Form.Item name="description" label="说明"><Input.TextArea rows={3} /></Form.Item></Form>
        <Typography.Text strong>输入 schema</Typography.Text><pre style={{ overflow: 'auto', padding: 16, background: '#f5f5f7', borderRadius: 8 }}>{JSON.stringify(draft.inputSchema, null, 2)}</pre>
      </Space>}
    </Drawer>
    <Drawer title="创建智能体" width={520} open={agentDraft} onClose={() => setAgentDraft(false)} extra={<Button type="primary" onClick={() => void createAgent()}>创建并显示 Key</Button>}>
      <Form form={agentForm} layout="vertical"><Form.Item name="name" label="智能体名称" rules={[{ required: true, message: '请输入智能体名称' }]}><Input /></Form.Item><Form.Item name="description" label="说明（可选）"><Input.TextArea rows={3} /></Form.Item></Form>
    </Drawer>
    <Drawer title={`配置 ${configuredAgent?.name ?? ''} 的工具快照`} width={560} open={Boolean(configuredAgent)} onClose={() => setConfiguredAgent(undefined)} extra={<Button type="primary" onClick={() => void publishToolSnapshot()}>发布快照</Button>}>
      <Typography.Paragraph>此处选择仅为临时配置，点击“发布快照”后才会整体替换当前生效的工具快照。</Typography.Paragraph>
      <Select mode="multiple" style={{ width: '100%' }} placeholder="选择已发布的 MCP 工具" value={draftToolIds} onChange={setDraftToolIds} options={tools.filter((tool) => tool.enabled).map((tool) => ({ value: tool.id, label: `${tool.name}${tool.description ? ` · ${tool.description}` : ''}` }))} />
    </Drawer>
  </ConfigProvider>;
}

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
