import React, { useEffect, useRef } from 'react';
import { Row, Col, Card, Table } from 'antd';
import * as echarts from 'echarts';
import { useOrders } from '../context/OrdersContext';

const StatisticsPage = () => {
  const { salesData, fetchSalesData } = useOrders();
  const chartRef = useRef(null);
  const chartInstance = useRef(null);

  useEffect(() => {
    fetchSalesData();
  }, [fetchSalesData]);

  useEffect(() => {
    if (salesData.length > 0 && chartRef.current) {
      initSalesChart();
    }

    return () => {
      if (chartInstance.current) {
        chartInstance.current.dispose();
      }
    };
  }, [salesData]);

  // 初始化图表
  const initSalesChart = () => {
    if (chartInstance.current) {
      chartInstance.current.dispose();
    }

    const chartDom = chartRef.current;
    if (!chartDom) return;

    const myChart = echarts.init(chartDom);
    chartInstance.current = myChart;

    const bookTitles = salesData.map(item => item.bookTitle);
    const quantities = salesData.map(item => item.totalQuantity);
    const amounts = salesData.map(item => item.totalAmount);

    const option = {
      title: {
        text: '书籍销量统计',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'cross'
        }
      },
      legend: {
        data: ['销量', '销售额'],
        top: '10%'
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
      yAxis: [
        {
          type: 'value',
          name: '销量',
          position: 'left'
        },
        {
          type: 'value',
          name: '销售额',
          position: 'right'
        }
      ],
      series: [
        {
          name: '销量',
          type: 'bar',
          data: quantities,
          itemStyle: {
            color: '#5470C6'
          }
        },
        {
          name: '销售额',
          type: 'line',
          yAxisIndex: 1,
          data: amounts,
          itemStyle: {
            color: '#91CC75'
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

  return (
    <div className="statistics-page">
      <h2>销量统计</h2>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="销量图表" bordered={false}>
            <div ref={chartRef} style={{ width: '100%', height: '500px' }}></div>
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
              dataSource={salesData}
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
      </Row>
    </div>
  );
};

export default StatisticsPage;