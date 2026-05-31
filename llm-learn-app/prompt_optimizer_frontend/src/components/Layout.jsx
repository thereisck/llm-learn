import React from 'react'
import { Layout, Menu } from 'antd'
import { FileTextOutlined, ExperimentOutlined, SwapOutlined } from '@ant-design/icons'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'

const { Header, Content, Sider } = Layout

const menuItems = [
  {
    key: '/templates',
    icon: <FileTextOutlined />,
    label: '模板管理'
  },
  {
    key: '/test',
    icon: <ExperimentOutlined />,
    label: 'Prompt测试'
  },
  {
    key: '/abtest',
    icon: <SwapOutlined />,
    label: 'A/B对比'
  }
]

function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ background: '#1890ff', padding: '0 50px' }}>
        <h1 style={{ color: '#fff', margin: 0 }}>Prompt优化系统</h1>
      </Header>
      
      <Layout>
        <Sider width={200} style={{ background: '#fff' }}>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ height: '100%', borderRight: 0 }}
          />
        </Sider>
        
        <Content style={{ padding: '24px', background: '#f0f2f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default AppLayout