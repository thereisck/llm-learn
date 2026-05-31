import React, { useState, useEffect } from 'react'
import { Form, Input, Button, Select, Card, Divider, Spin, Alert, Statistic, Row, Col, message } from 'antd'
import axios from 'axios'

const { TextArea } = Input

function PromptTest() {
  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [form] = Form.useForm()
  
  useEffect(() => {
    loadTemplates()
  }, [])
  
  const loadTemplates = async () => {
    try {
      const response = await axios.get('/api/prompt/templates')
      setTemplates(response.data)
    } catch (error) {
      message.error('加载模板失败')
    }
  }
  
  const handleSubmit = async (values) => {
    setLoading(true)
    setResult(null)
    
    try {
      // 处理 params：如果是字符串，解析为对象
      const payload = { ...values }
      if (payload.params && typeof payload.params === 'string') {
        try {
          payload.params = JSON.parse(payload.params)
        } catch (e) {
          payload.params = {}
        }
      }
      // 移除 useTemplate 字段（后端不需要）
      delete payload.useTemplate
      
      const response = await axios.post('/api/prompt/test', payload)
      setResult(response.data)
      message.success('测试完成')
    } catch (error) {
      message.error('测试失败: ' + (error.response?.data?.message || error.message))
    } finally {
      setLoading(false)
    }
  }
  
  return (
    <div>
      <Card title="Prompt测试">
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="测试方式">
            <Select
              onChange={(value) => {
                form.setFieldsValue({ useTemplate: value })
                if (value === 'template') {
                  form.setFieldsValue({ prompt: undefined })
                } else {
                  form.setFieldsValue({ templateId: undefined, params: undefined })
                }
              }}
              defaultValue="direct"
            >
              <Select.Option value="direct">直接输入Prompt</Select.Option>
              <Select.Option value="template">使用模板</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            noStyle
            shouldUpdate={(prev, curr) => prev.useTemplate !== curr.useTemplate}
          >
            {({ getFieldValue }) => {
              const useTemplate = getFieldValue('useTemplate')
              
              if (useTemplate === 'template') {
                return (
                  <>
                    <Form.Item
                      name="templateId"
                      label="选择模板"
                      rules={[{ required: true, message: '请选择模板' }]}
                    >
                      <Select>
                        {templates.map(t => (
                          <Select.Option key={t.id} value={t.id}>
                            {t.name} ({t.category})
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>
                    
                    <Form.Item name="params" label="变量参数（JSON格式）">
                      <TextArea rows={4} placeholder='{"text": "Hello", "language": "中文"}' />
                    </Form.Item>
                  </>
                )
              }
              
              return (
                <Form.Item
                  name="prompt"
                  label="Prompt内容"
                  rules={[{ required: true, message: '请输入Prompt' }]}
                >
                  <TextArea rows={6} />
                </Form.Item>
              )
            }}
          </Form.Item>
          
          <Form.Item name="expectedOutput" label="期望输出（用于评估）">
            <TextArea rows={4} placeholder="可选，用于对比评估" />
          </Form.Item>
          
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              开始测试
            </Button>
          </Form.Item>
        </Form>
      </Card>
      
      {loading && (
        <Card>
          <Spin tip="正在测试..." />
        </Card>
      )}
      
      {result && (
        <Card title="测试结果" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic title="输入Token" value={result.tokenUsage?.inputTokens || 0} />
            </Col>
            <Col span={6}>
              <Statistic title="输出Token" value={result.tokenUsage?.outputTokens || 0} />
            </Col>
            <Col span={6}>
              <Statistic title="响应延迟" value={result.latencyMs || 0} suffix="ms" />
            </Col>
            <Col span={6}>
              <Statistic title="综合评分" value={result.evaluation?.overallScore || 0} suffix="分" />
            </Col>
          </Row>
          
          <Divider />
          
          <h4>LLM响应：</h4>
          <TextArea rows={6} value={result.response} readOnly />
          
          <Divider />
          
          <h4>评估结果：</h4>
          <Alert
            type={result.evaluation?.overallScore >= 80 ? 'success' : 'warning'}
            message={result.evaluation?.grade || '-'}
            description={result.evaluation?.recommendation || '-'}
          />
          
          <Divider />
          
          <h4>完整报告：</h4>
          <TextArea rows={10} value={result.report} readOnly />
        </Card>
      )}
    </div>
  )
}

export default PromptTest