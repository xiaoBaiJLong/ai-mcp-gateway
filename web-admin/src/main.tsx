import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider, Layout, Typography } from 'antd';
import 'antd/dist/reset.css';

const { Content } = Layout;

function App() {
  return (
    <ConfigProvider theme={{ token: { colorPrimary: '#1d1d1f', borderRadius: 12 } }}>
      <Layout style={{ minHeight: '100vh', background: '#f5f5f7' }}>
        <Content style={{ display: 'grid', placeItems: 'center', padding: 32 }}>
          <section style={{ maxWidth: 560, textAlign: 'center' }}>
            <Typography.Title>MCP 网关</Typography.Title>
            <Typography.Paragraph type="secondary">
              本机开发运行基座已就绪，工具配置将在下一任务提供。
            </Typography.Paragraph>
          </section>
        </Content>
      </Layout>
    </ConfigProvider>
  );
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
