import { useState, useRef, useEffect } from 'react';
import { Input, Button, Avatar, message, Badge } from 'antd';
import { SendOutlined, LoadingOutlined, MessageOutlined } from '@ant-design/icons';
import { aiService } from '../services/aiService';
import './AIAssistant.css';

const { TextArea } = Input;

const AIAssistant = () => {
  const [msg, contextHolder] = message.useMessage();
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [showChat, setShowChat] = useState(false);
  const requestInFlight = useRef(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    if (!inputValue.trim() || requestInFlight.current) return;
    requestInFlight.current = true;

    const userMessage = {
      id: Date.now(),
      text: inputValue,
      sender: 'user',
      timestamp: new Date().toLocaleTimeString(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');
    setLoading(true);

    try {
      const history = messages.filter(entry => !entry.error).slice(-12).map(entry => ({
        role: entry.sender === 'user' ? 'user' : 'assistant', content: entry.text,
      }));
      const aiResponse = await aiService.chat(inputValue, history);
      const aiMessage = {
        id: Date.now() + 1,
        text: aiResponse.answer,
        toolCalls: aiResponse.toolCalls,
        sender: 'ai',
        timestamp: new Date().toLocaleTimeString(),
      };
      setMessages(prev => [...prev, aiMessage]);
    } catch (error) {
      console.error('Error communicating with AI assistant:', error);
      const errorText = error.message || 'AI助手暂时无法响应，请稍后再试';
      msg.error(errorText);
      setMessages(prev => [...prev, { id: Date.now() + 1, sender: 'ai', text: errorText,
        error: true, timestamp: new Date().toLocaleTimeString() }]);
    } finally {
      requestInFlight.current = false;
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="ai-assistant-container">
      {contextHolder}
      {showChat ? (
        <div className="chat-window">
          <div className="chat-header">
            <div className="header-left">
              <Avatar icon={<MessageOutlined />} />
              <h3>智能书店助手</h3>
            </div>
            <Button 
              type="text" 
              icon={<SendOutlined />} 
              onClick={() => setShowChat(false)}
            >
              关闭
            </Button>
          </div>
          <div className="chat-messages">
            {messages.length === 0 && <div className="chat-hint">
              查询实时库存与模拟竞品价格。可试：帮我查 ISBN 为 12345 的书还有多少本，别家卖多少钱？
            </div>}
            {messages.map(message => (
              <div key={message.id} className={`message ${message.sender}`}>
                <div className="message-avatar">
                  <Avatar>{message.sender === 'user' ? '你' : 'AI'}</Avatar>
                </div>
                <div className="message-content">
                  <div className="message-text">{message.text}</div>
                  {message.toolCalls?.length > 0 && <details className="tool-trace">
                    <summary>查看查询记录（{message.toolCalls.length} 次）</summary>
                    {message.toolCalls.map(call => <div key={`${call.round}-${call.id}`}>
                      <strong>第 {call.round} 轮 · {call.name}</strong>
                      <pre>{call.arguments}</pre>
                      <pre>{JSON.stringify(call.result, null, 2)}</pre>
                    </div>)}
                  </details>}
                  <div className="message-time">{message.timestamp}</div>
                </div>
              </div>
            ))}
            {loading && (
              <div className="message ai loading">
                <div className="message-avatar">
                  <Avatar icon={<LoadingOutlined spin />} />
                </div>
                <div className="message-content">
                  <div className="message-text">正在思考...</div>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
          <div className="chat-input">
            <TextArea
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyDown={handleKeyPress}
              maxLength={4000}
              aria-label="书店助手问题"
              placeholder="输入 ISBN，查询库存或模拟竞品价格"
              rows={2}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={handleSend}
              disabled={loading}
            >
              发送
            </Button>
          </div>
        </div>
      ) : (
        <button type="button" aria-label="打开智能书店助手" className="chat-toggle" onClick={() => setShowChat(true)}>
          <Badge count={messages.length} size="small">
            <Avatar icon={<MessageOutlined />} style={{ backgroundColor: '#1890ff' }} />
          </Badge>
        </button>
      )}
    </div>
  );
};

export default AIAssistant;
