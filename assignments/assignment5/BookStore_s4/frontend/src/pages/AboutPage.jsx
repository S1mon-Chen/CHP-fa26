import React from 'react';
import { Row, Col, Card } from 'antd';

const AboutPage = () => {
  return (
    <div className="about-page">
      <h2>关于我们</h2>
      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card title="公司简介" bordered={false}>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              智能在线书店是一家专注于提供高质量图书的在线平台，致力于为读者提供便捷、优质的阅读体验。
            </p>
            <p style={{ fontSize: '16px', lineHeight: '1.8', marginTop: '16px' }}>
              我们的使命是通过数字化技术，让更多人享受阅读的乐趣，传播知识与文化。
            </p>
          </Card>
        </Col>
        <Col span={24} md={12}>
          <Card title="我们的特色" bordered={false}>
            <ul style={{ fontSize: '16px', lineHeight: '1.8' }}>
              <li>丰富的图书资源，涵盖各个领域</li>
              <li>智能搜索功能，快速找到您需要的书籍</li>
              <li>便捷的购物体验，支持多种支付方式</li>
              <li>专业的客服团队，为您提供贴心服务</li>
              <li>定期更新书籍，保持最新的阅读内容</li>
            </ul>
          </Card>
        </Col>
        <Col span={24} md={12}>
          <Card title="联系我们" bordered={false}>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              <strong>地址：</strong>北京市海淀区中关村大街1号
            </p>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              <strong>电话：</strong>010-12345678
            </p>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              <strong>邮箱：</strong>contact@smartbookstore.com
            </p>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              <strong>营业时间：</strong>周一至周日 9:00-21:00
            </p>
          </Card>
        </Col>
        <Col span={24}>
          <Card title="加入我们" bordered={false}>
            <p style={{ fontSize: '16px', lineHeight: '1.8' }}>
              我们欢迎热爱阅读、有创新精神的人才加入我们的团队。如果您对我们的公司感兴趣，
              请发送简历至 <strong>hr@smartbookstore.com</strong>，我们会尽快与您联系。
            </p>
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default AboutPage;