import React, { useState, useEffect } from 'react'
import { Form, Input, Button, Select, Card, Tabs, Spin, message, Divider, Alert, Statistic, Row, Col, Table, Tag } from 'antd'
import axios from 'axios'

const { TextArea } = Input

function ABTest() {
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
      const response = await axios.post('/api/prompt/abtest', values)
      setResult(response.data)
      message.success('A/B测试完成')
    } catch (error) {
      message.error('A/B测试失败')
    } finally {
      setLoading(false)
    }
  }
  
  const detailColumns = [
    {
      title: '方案',
      dataIndex: 'index',
      key: 'index',
      render: (index) => <Tag color={index === 1 ? 'blue' : 'green'}>方案{index}</Tag>
    },
    {
      title: 'Token消耗',
      key: 'tokens',
      render: (_, record) => `${record.tokenUsage?.inputTokens || 0} + ${record.tokenUsage?.outputTokens || 0}`
    },
    {
      title: '响应延迟',
      dataIndex: 'latencyMs',
      key: 'latencyMs',
      render: (ms) => `${ms}ms`
    },
    {
      title: '评分',
      key: 'score',
      render: (_, record) => record.evaluation?.overallScore || 0
    },
    {
      title: '等级',
      key: 'grade',
      render: (_, record) => record.evaluation?.grade || '-'
    }
  ]
  
  return (
    <div>
      <Card title="A/B测试对比">
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="方案数量">
            <Select
              onChange={(value) => {
                // 根据数量调整prompts数组
                const prompts = Array(value).fill({ prompt: '' })
                form.setFieldsValue({ prompts })
              }}
              defaultValue={2}
            >
              <Select.Option value={2}>2个方案</Select.Option>
              <Select.Option value={3}>3个方案</Select.Option>
              <Select.Option value={4}>4个方案</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.List name="prompts">
            {(fields) => (
              <>
                {fields.map((field, index) => (
                  <Card key={field.key} title={`方案${index + 1}`} style={{ marginBottom: 16 }}>
                    <Form.Item
                      name={[field.name, 'prompt']}
                      label="Prompt内容"
                      rules={[{ required: true, message: '请输入Prompt' }]}
                    >
                      <TextArea rows={4} />
                    </Form.Item>
                  </Card>
                ))}
              </>
            )}
          </Form.List>
          
          <Form.Item name="expectedOutput" label="期望输出（用于评估）">
            <TextArea rows={4} placeholder="可选" />
          </Form.Item>
          
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              开始A/B测试
            </Button>
          </Form.Item>
        </Form>
      </Card>
      
      {loading && (
        <Card>
          <Spin tip="正在并行测试..." />
        </Card>
      )}
      
      {result && (
        <Card title="A/B测试结果" style={{ marginTop: 16 }}>
          <Tabs items={[
            {
              key: 'summary',
              label: '对比摘要',
              children: (
                <>
                  <Alert
                    type="success"
                    message={`推荐方案${result.recommendation?.bestIndex + 1}`}
                    description={result.recommendation?.reason}
                    showIcon
                  />
                  
                  <Divider />
                  
                  <Row gutter={16}>
                    <Col span={8}>
                      <Statistic title="总Token消耗" value={result.costAnalysis?.totalTokens || 0} />
                    </Col>
                    <Col span={8}>
                      <Statistic title="总成本(USD)" value={result.costAnalysis?.totalCostUSD || 0} prefix="$" precision={4} />
                    </Col>
                    <Col span={8}>
                      <Statistic title="总耗时" value={result.totalTimeMs || 0} suffix="ms" />
                    </Col>
                  </Row>
                  
                  <Divider />
                  
                  <Table
                    columns={detailColumns}
                    dataSource={result.details}
                    rowKey="index"
                    pagination={false}
                  />
                </>
              )
            },
            {
              key: 'report',
              label: '完整报告',
              children: <TextArea rows={20} value={result.comparisonReport} readOnly />
            }
          ]} />
        </Card>
      )}
    </div>
  )
}

export default ABTest