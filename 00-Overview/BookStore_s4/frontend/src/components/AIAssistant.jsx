import { useState, useRef, useEffect } from 'react';
import { Input, Button, Avatar, message, Badge } from 'antd';
import { SendOutlined, LoadingOutlined, MessageOutlined } from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { aiService } from '../services/aiService';
import './AIAssistant.css';

const { TextArea } = Input;

const AIAssistant = () => {
  const { message: msg } = message.useMessage();
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [showChat, setShowChat] = useState(false);
  const { currentUser } = useAuth();
  const messagesEndRef = useRef(null);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  const handleSend = async () => {
    if (!inputValue.trim()) return;

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
      const aiResponse = await aiService.chat(inputValue, currentUser?.id);
      const aiMessage = {
        id: Date.now() + 1,
        text: aiResponse,
        sender: 'ai',
        timestamp: new Date().toLocaleTimeString(),
      };
      setMessages(prev => [...prev, aiMessage]);
    } catch (error) {
      console.error('Error communicating with AI assistant:', error);
      msg.error('AI助手暂时无法响应，请稍后再试');
    } finally {
      setLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="ai-assistant-container">
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
            {messages.map(message => (
              <div key={message.id} className={`message ${message.sender}`}>
                <div className="message-avatar">
                  <Avatar>{message.sender === 'user' ? '你' : 'AI'}</Avatar>
                </div>
                <div className="message-content">
                  <div className="message-text">{message.text}</div>
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
              onKeyPress={handleKeyPress}
              placeholder="请输入您的问题，例如：推荐几本关于人工智能的书籍"
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
        <div className="chat-toggle" onClick={() => setShowChat(true)}>
          <Badge count={messages.length} size="small">
            <Avatar icon={<MessageOutlined />} style={{ backgroundColor: '#1890ff' }} />
          </Badge>
        </div>
      )}
    </div>
  );
};

export default AIAssistant;