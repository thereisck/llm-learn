import React, { useState, useEffect } from 'react'
import { Table, Button, Modal, Form, Input, Select, message, Space, Tag } from 'antd'
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'

const { TextArea } = Input

function TemplateManage() {
  const [templates, setTemplates] = useState([])
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState(null)
  const [form] = Form.useForm()
  
  useEffect(() => {
    loadTemplates()
  }, [])
  
  const loadTemplates = async () => {
    setLoading(true)
    try {
      const response = await axios.get('/api/prompt/templates')
      setTemplates(response.data)
    } catch (error) {
      message.error('加载模板失败')
    } finally {
      setLoading(false)
    }
  }
  
  const handleAdd = () => {
    setEditingTemplate(null)
    form.resetFields()
    setModalVisible(true)
  }
  
  const handleEdit = (record) => {
    setEditingTemplate(record)
    form.setFieldsValue(record)
    setModalVisible(true)
  }
  
  const handleDelete = async (id) => {
    try {
      await axios.delete(`/api/prompt/templates/${id}`)
      message.success('删除成功')
      loadTemplates()
    } catch (error) {
      message.error('删除失败')
    }
  }
  
  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      
      if (editingTemplate) {
        await axios.put(`/api/prompt/templates/${editingTemplate.id}`, values)
        message.success('更新成功')
      } else {
        await axios.post('/api/prompt/templates', values)
        message.success('添加成功')
      }
      
      setModalVisible(false)
      loadTemplates()
    } catch (error) {
      message.error('保存失败')
    }
  }
  
  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 150
    },
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: (category) => <Tag color="blue">{category}</Tag>
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (time) => time ? new Date(time).toLocaleString() : '-'
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button icon={<EditOutlined />} onClick={() => handleEdit(record)} />
          <Button icon={<DeleteOutlined />} danger onClick={() => handleDelete(record.id)} />
        </Space>
      )
    }
  ]
  
  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          新增模板
        </Button>
      </Space>
      
      <Table
        columns={columns}
        dataSource={templates}
        rowKey="id"
        loading={loading}
      />
      
      <Modal
        title={editingTemplate ? '编辑模板' : '新增模板'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input />
          </Form.Item>
          
          <Form.Item
            name="category"
            label="分类"
            rules={[{ required: true, message: '请选择分类' }]}
          >
            <Select>
              <Select.Option value="translation">翻译</Select.Option>
              <Select.Option value="code-generation">代码生成</Select.Option>
              <Select.Option value="summarization">总结</Select.Option>
              <Select.Option value="qa">问答</Select.Option>
            </Select>
          </Form.Item>
          
          <Form.Item
            name="template"
            label="模板内容"
            rules={[{ required: true, message: '请输入模板内容' }]}
          >
            <TextArea rows={6} placeholder="使用 ${变量名} 作为占位符" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default TemplateManage