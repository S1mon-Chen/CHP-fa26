import { useState, useRef, useEffect } from 'react';
import { Input, Button, Avatar, message, Badge } from 'antd';
import { SendOutlined, LoadingOutlined, MessageOutlined } from '@ant-design/icons';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { aiService } from '../services/aiService';
import './AIAssistant.css';

const { TextArea } = Input;

const AIAssistant = () => {
  const [msg, contextHolder] = message.useMessage();
  const [mode, setMode] = useState('tools');
  const [conversations, setConversations] = useState({ tools: [], shopping: [] });
  const messages = conversations[mode];
  const setMessages = update => setConversations(previous => ({
    ...previous, [mode]: update(previous[mode]),
  }));
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
      const aiResponse = await (mode === 'tools' ? aiService.toolChat : aiService.chat)(inputValue, history);
      const aiMessage = {
        id: Date.now() + 1,
        text: aiResponse.answer,
        steps: aiResponse.steps,
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
          <div className="chat-mode">
            <label htmlFor="assistant-mode">咨询类型</label>
            <select id="assistant-mode" value={mode} disabled={loading} onChange={event => {
              setMode(event.target.value);
              setInputValue('');
            }}>
              <option value="tools">库存与比价</option>
              <option value="shopping">图书推荐与政策</option>
            </select>
            <Button size="small" disabled={loading} onClick={() => {
              setMessages(() => []);
              setInputValue('');
            }}>新对话</Button>
          </div>
          <div className="chat-messages">
            {messages.length === 0 && <div className="chat-hint">
              {mode === 'tools' ? <>
                按 ISBN 查询本店实时库存和模拟竞品价格，展开查询记录可查看参数、结果和纠错过程。
                <div className="chat-examples">
                  <Button size="small" onClick={() => setInputValue('帮我查一下 ISBN 为 12345 的书还有多少本，顺便看看别家卖多少钱。')}>库存与比价示例</Button>
                  <Button size="small" onClick={() => setInputValue('请先按原样调用 check_inventory 查询 ISBN 978-7-111-42190-0；如果工具报格式错误，再按工具建议修正。')}>ISBN 格式纠正示例</Button>
                  <Button size="small" onClick={() => setInputValue('请按原样调用 check_inventory 查询 ISBN 9787111421901，不要修改数字；如果书号无效，请告诉我。')}>无效 ISBN 示例</Button>
                </div>
              </> : '教学模拟导购：推荐图书，查询退换货与会员政策。可试：我想买一本关于微服务的书，另外如果我买了不喜欢，拆了塑封还能退吗？'}
            </div>}
            {messages.map(message => (
              <div key={message.id} className={`message ${message.sender}`}>
                <div className="message-avatar">
                  <Avatar>{message.sender === 'user' ? '你' : 'AI'}</Avatar>
                </div>
                <div className="message-content">
                  <div className={`message-text ${message.sender === 'ai' ? 'markdown-answer' : ''}`}>
                    {message.sender === 'ai'
                      ? <Markdown remarkPlugins={[remarkGfm]} skipHtml disallowedElements={['img']}>{message.text}</Markdown>
                      : message.text}
                  </div>
                  {message.toolCalls?.length > 0 && <details className="tool-trace">
                    <summary>查看查询记录（{message.toolCalls.length} 次）</summary>
                    {message.toolCalls.map(call => <div key={`${call.round}-${call.id}`}>
                      <strong>第 {call.round} 轮 · {call.name}</strong>
                      <p>调用参数</p>
                      <pre>{call.arguments}</pre>
                      <p>执行结果 · {call.result.ok ? '成功' : '失败'}</p>
                      <pre>{JSON.stringify(call.result, null, 2)}</pre>
                    </div>)}
                  </details>}
                  {message.steps?.length > 0 && <details className="tool-trace">
                    <summary>查看 ReAct 执行记录（{message.steps.length} 次）</summary>
                    <p>Thought 是简短行动摘要。Observation 包含检索原文和来源。</p>
                    {message.steps.map(step => <div key={`${step.round}-${step.id}`}>
                      <strong>第 {step.round} 轮</strong>
                      <p>Thought：{step.thought}</p>
                      <strong>Action：{step.action.name}</strong>
                      <pre>{JSON.stringify(step.action.arguments, null, 2)}</pre>
                      <strong>Observation</strong>
                      <pre>{JSON.stringify(step.observation, null, 2)}</pre>
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
              placeholder={mode === 'tools' ? '输入 ISBN，查询库存或模拟竞品价格' : '描述想读的书，或咨询退换货与会员政策'}
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
