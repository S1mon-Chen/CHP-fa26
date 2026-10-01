import React, { useEffect, useRef } from 'react';
import { Row, Col, Card, Table } from 'antd';
import * as echarts from 'echarts';
import { useOrders } from '../context/OrdersContext';

const DashboardPage = () => {
  const { dashboardData, fetchDashboardData } = useOrders();
  const salesBarChartRef = useRef(null);
  const userPieChartRef = useRef(null);
  const salesBarChartInstance = useRef(null);
  const userPieChartInstance = useRef(null);

  useEffect(() => {
    fetchDashboardData();
  }, [fetchDashboardData]);

  useEffect(() => {
    if (dashboardData.salesByBook.length > 0 && dashboardData.salesByUser.length > 0) {
      initSalesBarChart();
      initUserPieChart();
    }

    return () => {
      if (salesBarChartInstance.current) {
        salesBarChartInstance.current.dispose();
      }
      if (userPieChartInstance.current) {
        userPieChartInstance.current.dispose();
      }
    };
  }, [dashboardData]);

  // 初始化销量条形图
  const initSalesBarChart = () => {
    if (salesBarChartInstance.current) {
      salesBarChartInstance.current.dispose();
    }

    const chartDom = salesBarChartRef.current;
    if (!chartDom) return;

    const myChart = echarts.init(chartDom);
    salesBarChartInstance.current = myChart;

    const { salesByBook } = dashboardData;
    const bookTitles = salesByBook.map(item => item.bookTitle);
    const quantities = salesByBook.map(item => item.totalQuantity);

    const option = {
      title: {
        text: '书籍销量排行榜',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: bookTitles,
        axisLabel: {
          interval: 0,
          rotate: 45
        }
      },
      yAxis: {
        type: 'value',
        name: '销量'
      },
      series: [
        {
          name: '销量',
          type: 'bar',
          data: quantities,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#83bff6' },
              { offset: 0.5, color: '#188df0' },
              { offset: 1, color: '#188df0' }
            ])
          },
          emphasis: {
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#2378f7' },
                { offset: 0.7, color: '#2378f7' },
                { offset: 1, color: '#83bff6' }
              ])
            }
          }
        }
      ]
    };

    myChart.setOption(option);

    const handleResize = () => {
      myChart.resize();
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
    };
  };

  // 初始化用户消费环形图
  const initUserPieChart = () => {
    if (userPieChartInstance.current) {
      userPieChartInstance.current.dispose();
    }

    const chartDom = userPieChartRef.current;
    if (!chartDom) return;

    const myChart = echarts.init(chartDom);
    userPieChartInstance.current = myChart;

    const { salesByUser } = dashboardData;
    const pieData = salesByUser.map(item => ({
      name: item.username,
      value: item.totalAmount
    }));

    const option = {
      title: {
        text: '用户消费排行榜',
        left: 'center'
      },
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: ¥{c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        data: salesByUser.map(item => item.username)
      },
      series: [
        {
          name: '消费金额',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: false,
            position: 'center'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: '18',
              fontWeight: 'bold'
            }
          },
          labelLine: {
            show: false
          },
          data: pieData
        }
      ]
    };

    myChart.setOption(option);

    const handleResize = () => {
      myChart.resize();
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
    };
  };

  return (
    <div className="dashboard-page">
      <h2>管理员Dashboard</h2>
      <Row gutter={[16, 16]}>
        <Col span={24} md={12}>
          <Card title="书籍销量排行榜" bordered={false}>
            <div ref={salesBarChartRef} style={{ width: '100%', height: '400px' }}></div>
          </Card>
        </Col>
        <Col span={24} md={12}>
          <Card title="用户消费排行榜" bordered={false}>
            <div ref={userPieChartRef} style={{ width: '100%', height: '400px' }}></div>
          </Card>
        </Col>
        <Col span={24}>
          <Card title="销量明细" bordered={false}>
            <Table 
              columns={[
                {
                  title: '排名',
                  key: 'rank',
                  render: (_, __, index) => index + 1
                },
                {
                  title: '书名',
                  dataIndex: 'bookTitle',
                  key: 'bookTitle'
                },
                {
                  title: '作者',
                  dataIndex: 'bookAuthor',
                  key: 'bookAuthor'
                },
                {
                  title: '销量',
                  dataIndex: 'totalQuantity',
                  key: 'totalQuantity',
                  sorter: (a, b) => a.totalQuantity - b.totalQuantity
                },
                {
                  title: '销售额',
                  dataIndex: 'totalAmount',
                  key: 'totalAmount',
                  render: (amount) => `¥${parseFloat(amount).toFixed(2)}`,
                  sorter: (a, b) => a.totalAmount - b.totalAmount
                }
              ]}
              dataSource={dashboardData.salesByBook}
              rowKey="bookId"
              pagination={{
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 本书`
              }}
            />
          </Card>
        </Col>
        <Col span={24}>
          <Card title="用户消费明细" bordered={false}>
            <Table 
              columns={[
                {
                  title: '排名',
                  key: 'rank',
                  render: (_, __, index) => index + 1
                },
                {
                  title: '用户ID',
                  dataIndex: 'userId',
                  key: 'userId'
                },
                {
                  title: '用户名',
                  dataIndex: 'username',
                  key: 'username'
                },
                {
                  title: '消费金额',
                  dataIndex: 'totalAmount',
                  key: 'totalAmount',
                  render: (amount) => `¥${parseFloat(amount).toFixed(2)}`,
                  sorter: (a, b) => a.totalAmount - b.totalAmount
                }
              ]}
              dataSource={dashboardData.salesByUser}
              rowKey="userId"
              pagination={{
                pageSize: 10,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (total) => `共 ${total} 个用户`
              }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default DashboardPage;