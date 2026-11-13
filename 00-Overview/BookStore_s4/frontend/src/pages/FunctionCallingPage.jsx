import { useState, useRef, useEffect } from 'react';
import { Input, Button, Avatar, Tag, Collapse, Card, Space, message } from 'antd';
import {
  SendOutlined,
  LoadingOutlined,
  CustomerServiceOutlined,
  ApiOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import { aiService } from '../services/aiService';
import './FunctionCallingPage.css';

const { TextArea } = Input;

const EXAMPLES = [
  '帮我查一下 ISBN为12345 的书还有多少本，顺便看看别家卖多少钱',
  'ISBN 12354 这本书还有货吗？',
  'ISBN 99901 这本书别家卖多少钱？',
  '978-7-111-07575-2 还剩几本？',
  'ISBN 12a45 多少钱？',
];

// 每次请求最多带上最近几条历史消息，支持“请确认 ISBN”之类的多轮追问
const HISTORY_LIMIT = 10;

const formatJson = (value) => JSON.stringify(value, null, 2);

// 展示一次回答过程中模型触发的所有函数调用
const ToolCallList = ({ toolCalls }) => {
  if (!toolCalls?.length) {
    return null;
  }

  const items = toolCalls.map((call, index) => ({
    key: call.id || index,
    label: (
      <Space size={6} wrap>
        <Tag color="blue">第 {call.round} 轮</Tag>
        <code className="fc-call-signature">
          {call.name}({formatJson(call.arguments)})
        </code>
        {call.success ? (
          <Tag icon={<CheckCircleOutlined />} color="success">{call.durationMs}ms</Tag>
        ) : (
          <Tag icon={<CloseCircleOutlined />} color="error">{call.result?.error}</Tag>
        )}
      </Space>
    ),
    children: (
      <div className="fc-call-detail">
        <div className="fc-call-detail-title">参数</div>
        <pre>{formatJson(call.arguments)}</pre>
        <div className="fc-call-detail-title">模拟执行结果（回传给模型）</div>
        <pre>{formatJson(call.result)}</pre>
      </div>
    ),
  }));

  return (
    <div className="fc-tool-calls">
      <div className="fc-tool-calls-title">
        <ApiOutlined /> 模型触发了 {toolCalls.length} 次函数调用
      </div>
      <Collapse size="small" items={items} />
    </div>
  );
};

const FunctionCallingPage = () => {
  const [messageApi, contextHolder] = message.useMessage();
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, loading]);

  const handleSend = async (text = inputValue) => {
    const query = text.trim();
    if (!query || loading) return;

    const history = messages
      .filter(m => !m.failed)
      .slice(-HISTORY_LIMIT)
      .map(m => ({ role: m.sender === 'user' ? 'user' : 'assistant', content: m.text }));

    setMessages(prev => [...prev, { id: Date.now(), sender: 'user', text: query, timestamp: new Date().toLocaleTimeString() }]);
    setInputValue('');
    setLoading(true);

    try {
      const response = await aiService.functionChat(query, history);
      setMessages(prev => [...prev, {
        id: Date.now() + 1,
        sender: 'ai',
        text: response.reply,
        toolCalls: response.toolCalls,
        failed: Boolean(response.error),
        timestamp: new Date().toLocaleTimeString(),
      }]);
    } catch (error) {
      console.error('客服助手请求失败:', error);
      messageApi.error('客服助手暂时无法响应，请确认后端已启动');
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Card
      className="fc-page"
      title={<Space><CustomerServiceOutlined />智能客服助手（Function Calling）</Space>}
      extra={<Tag>check_inventory · get_competitor_price</Tag>}
    >
      {contextHolder}
      <div className="fc-messages">
        {messages.length === 0 && (
          <div className="fc-empty">
            <p>可以问我某本书的库存，或让我和京东、当当、淘宝比价。试试：</p>
            <Space wrap>
              {EXAMPLES.map(example => (
                <Button key={example} size="small" onClick={() => handleSend(example)}>{example}</Button>
              ))}
            </Space>
          </div>
        )}

        {messages.map(msg => (
          <div key={msg.id} className={`fc-message ${msg.sender}`}>
            <Avatar className="fc-avatar">{msg.sender === 'user' ? '你' : 'AI'}</Avatar>
            <div className="fc-bubble-wrap">
              {msg.sender === 'ai' && <ToolCallList toolCalls={msg.toolCalls} />}
              <div className={`fc-bubble ${msg.failed ? 'failed' : ''}`}>{msg.text}</div>
              <div className="fc-time">{msg.timestamp}</div>
            </div>
          </div>
        ))}

        {loading && (
          <div className="fc-message ai">
            <Avatar className="fc-avatar" icon={<LoadingOutlined spin />} />
            <div className="fc-bubble-wrap">
              <div className="fc-bubble">正在查询库存和比价…</div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      <div className="fc-input">
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="例如：帮我查一下 ISBN为12345 的书还有多少本，顺便看看别家卖多少钱"
          autoSize={{ minRows: 2, maxRows: 4 }}
        />
        <Button type="primary" icon={<SendOutlined />} onClick={() => handleSend()} loading={loading}>
          发送
        </Button>
      </div>
    </Card>
  );
};

export default FunctionCallingPage;
