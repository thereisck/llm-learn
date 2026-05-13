import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import AppLayout from './components/Layout'
import TemplateManage from './pages/TemplateManage'
import PromptTest from './pages/PromptTest'
import ABTest from './pages/ABTest'

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<AppLayout />}>
            <Route index element={<Navigate to="/templates" replace />} />
            <Route path="templates" element={<TemplateManage />} />
            <Route path="test" element={<PromptTest />} />
            <Route path="abtest" element={<ABTest />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App