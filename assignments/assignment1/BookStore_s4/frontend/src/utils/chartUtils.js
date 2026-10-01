import * as echarts from 'echarts';

// 初始化销量图表
export const initSalesChart = (chartDom, salesData) => {
  if (!chartDom || !salesData || salesData.length === 0) return null;

  const myChart = echarts.init(chartDom);

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

  return {
    chart: myChart,
    cleanup: () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
    }
  };
};

// 初始化销量条形图
export const initSalesBarChart = (chartDom, salesByBook) => {
  if (!chartDom || !salesByBook || salesByBook.length === 0) return null;

  const myChart = echarts.init(chartDom);

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

  return {
    chart: myChart,
    cleanup: () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
    }
  };
};

// 初始化用户消费环形图
export const initUserPieChart = (chartDom, salesByUser) => {
  if (!chartDom || !salesByUser || salesByUser.length === 0) return null;

  const myChart = echarts.init(chartDom);

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

  return {
    chart: myChart,
    cleanup: () => {
      window.removeEventListener('resize', handleResize);
      myChart.dispose();
    }
  };
};