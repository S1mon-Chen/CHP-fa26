import React, { useEffect } from 'react';
import { Table } from 'antd';
import { useOrders } from '../context/OrdersContext';
import { useAuth } from '../context/AuthContext';

const OrdersPage = () => {
  const { orders, fetchOrders } = useOrders();
  const { currentUser } = useAuth();

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  // 订单表格列定义
  const orderColumns = [
    {
      title: '订单ID',
      dataIndex: 'id',
      key: 'id'
    },
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId'
    },
    {
      title: '总金额',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amount) => `¥${parseFloat(amount).toFixed(2)}`
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const statusMap = {
          'PENDING': '待支付',
          'PAID': '已支付',
          'DELIVERED': '已发货',
          'CANCELLED': '已取消'
        };
        return statusMap[status] || status;
      }
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => new Date(date).toLocaleString()
    }
  ];

  // 订单项表格列定义
  const orderItemColumns = [
    {
      title: '书籍ID',
      dataIndex: 'bookId',
      key: 'bookId'
    },
    {
      title: '书名',
      dataIndex: ['book', 'title'],
      key: 'bookTitle'
    },
    {
      title: '数量',
      dataIndex: 'quantity',
      key: 'quantity'
    },
    {
      title: '价格',
      dataIndex: 'price',
      key: 'price',
      render: (price) => `¥${parseFloat(price).toFixed(2)}`
    },
    {
      title: '小计',
      key: 'subtotal',
      render: (_, record) => `¥${(record.price * record.quantity).toFixed(2)}`
    }
  ];

  return (
    <div className="orders-page">
      <h2>订单列表</h2>
      {!currentUser ? (
        <p>请先登录查看订单</p>
      ) : (
        <Table 
          columns={orderColumns}
          dataSource={orders}
          rowKey="id"
          expandable={{
            expandedRowRender: (record) => (
              <Table 
                columns={orderItemColumns}
                dataSource={record.orderItems}
                rowKey="id"
                pagination={false}
                size="small"
              />
            )
          }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条订单`
          }}
        />
      )}
    </div>
  );
};

export default OrdersPage;