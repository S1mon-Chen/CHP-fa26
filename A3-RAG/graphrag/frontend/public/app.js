/**
 * GraphRAG vs RAG 对比演示
 */

// API 配置
const API_BASE = 'http://localhost:5001';

document.addEventListener('DOMContentLoaded', () => {
    const questionInput = document.getElementById('question-input');
    const sendBtn = document.getElementById('send-btn');
    const importBtn = document.getElementById('import-btn');
    const importFileBtn = document.getElementById('import-file-btn');
    const fileInput = document.getElementById('file-input');
    const fileName = document.getElementById('file-name');
    const statusIndicator = document.getElementById('status-indicator');
    const statusText = document.getElementById('status-text');
    const graphStats = document.getElementById('graph-stats');
    const ragStats = document.getElementById('rag-stats');
    const graphContainer = document.getElementById('graph-container');
    const ragChatHistory = document.getElementById('rag-chat-history');
    const graphragChatHistory = document.getElementById('graphrag-chat-history');
    const ragContextContent = document.getElementById('rag-context-content');
    const graphragContextContent = document.getElementById('graphrag-context-content');

    // 示例数据
    const sampleDocuments = [
        "张三是李四的父亲，李四在谷歌公司工作。张三是一名的士司机。",
        "王五是谷歌公司的CEO，他提倡人工智能技术。",
        "谷歌公司位于美国加州山景城。",
        "苹果公司是一家总部位于美国加州库比蒂诺的科技公司。",
        "蒂姆·库克是苹果公司的CEO。",
        "史蒂夫·乔布斯是苹果公司的创始人。",
        "中国市场是苹果公司最重要的市场之一。"
    ];

    // 检查后端状态
    async function checkStatus() {
        try {
            const response = await fetch(`${API_BASE}/api/status`);
            const data = await response.json();
            
            if (data.status === 'running') {
                statusIndicator.className = 'status online';
                statusText.textContent = '服务正常';
                graphStats.textContent = `图谱: ${data.nodes_count} 节点, ${data.edges_count} 关系`;
                loadGraph();
            } else {
                statusIndicator.className = 'status offline';
                statusText.textContent = '服务异常';
            }
        } catch {
            statusIndicator.className = 'status offline';
            statusText.textContent = '后端未连接';
        }
    }

    // 加载图谱可视化
    async function loadGraph() {
        try {
            const response = await fetch(`${API_BASE}/api/graph`);
            const data = await response.json();
            
            if (data.nodes && data.nodes.length > 0) {
                renderGraph(data.nodes, data.edges);
            }
        } catch (error) {
            console.error('加载图谱失败:', error);
        }
    }

    // 渲染图谱
    function renderGraph(nodes, edges) {
        graphContainer.innerHTML = '<div class="graph-visualization" id="graph-vis"></div>';
        const visContainer = document.getElementById('graph-vis');
        
        // 简单布局：圆形排列
        const centerX = 300;
        const centerY = 200;
        const radius = 150;
        const angleStep = (2 * Math.PI) / nodes.length;
        
        const nodePositions = {};
        
        nodes.forEach((node, index) => {
            const angle = index * angleStep;
            const x = centerX + radius * Math.cos(angle);
            const y = centerY + radius * Math.sin(angle);
            nodePositions[node.id] = { x, y };
            
            const nodeElement = document.createElement('div');
            nodeElement.className = 'graph-node';
            nodeElement.style.left = `${x}px`;
            nodeElement.style.top = `${y}px`;
            nodeElement.textContent = node.name;
            nodeElement.style.transform = 'translate(-50%, -50%)';
            visContainer.appendChild(nodeElement);
        });
        
        // 绘制关系
        edges.forEach(edge => {
            const source = nodePositions[edge.source];
            const target = nodePositions[edge.target];
            
            if (source && target) {
                // 创建 SVG 线条
                const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
                svg.style.position = 'absolute';
                svg.style.top = '0';
                svg.style.left = '0';
                svg.style.width = '100%';
                svg.style.height = '100%';
                svg.style.pointerEvents = 'none';
                
                const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
                line.setAttribute('x1', source.x);
                line.setAttribute('y1', source.y);
                line.setAttribute('x2', target.x);
                line.setAttribute('y2', target.y);
                line.setAttribute('stroke', '#ddd');
                line.setAttribute('stroke-width', '2');
                
                svg.appendChild(line);
                visContainer.appendChild(svg);
                
                // 添加关系标签
                const midX = (source.x + target.x) / 2;
                const midY = (source.y + target.y) / 2;
                
                const relationElement = document.createElement('div');
                relationElement.className = 'graph-relation';
                relationElement.style.left = `${midX}px`;
                relationElement.style.top = `${midY}px`;
                relationElement.textContent = edge.relation;
                relationElement.style.transform = 'translate(-50%, -50%)';
                visContainer.appendChild(relationElement);
            }
        });
    }

    // 发送对比查询
    async function sendCompareQuery() {
        const question = questionInput.value.trim();
        if (!question) return;
        
        // 添加用户消息到两边
        addMessage(ragChatHistory, 'user', question);
        addMessage(graphragChatHistory, 'user', question);
        questionInput.value = '';
        
        // 添加加载状态
        const ragLoading = addMessage(ragChatHistory, 'bot', '<span class="loading"></span> 正在检索...');
        const graphragLoading = addMessage(graphragChatHistory, 'bot', '<span class="loading"></span> 正在推理...');
        
        try {
            const response = await fetch(`${API_BASE}/api/compare-query`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ query: question })
            });
            
            const data = await response.json();
            
            // 更新统计信息
            if (data.graphrag.nodes_count !== undefined) {
                graphStats.textContent = `图谱: ${data.graphrag.nodes_count} 节点, ${data.graphrag.edges_count} 关系`;
            }
            if (data.rag.documents_count !== undefined) {
                ragStats.textContent = `RAG: ${data.rag.documents_count} 文档`;
            }
            
            // 移除加载消息
            ragLoading.remove();
            graphragLoading.remove();
            
            // 显示 RAG 回答
            if (data.rag.error) {
                addMessage(ragChatHistory, 'bot', `❌ ${data.rag.error}`);
            } else {
                addAnswerMessage(ragChatHistory, data.rag.answer);
                ragContextContent.textContent = data.rag.context || '无';
            }
            
            // 显示 GraphRAG 回答
            if (data.graphrag.error) {
                addMessage(graphragChatHistory, 'bot', `❌ ${data.graphrag.error}`);
            } else {
                addAnswerMessage(graphragChatHistory, data.graphrag.answer);
                graphragContextContent.textContent = data.graphrag.context || '无';
            }
            
            // 刷新图谱
            loadGraph();
            
        } catch (error) {
            ragLoading.remove();
            graphragLoading.remove();
            addMessage(ragChatHistory, 'bot', `❌ 错误: ${error.message}`);
            addMessage(graphragChatHistory, 'bot', `❌ 错误: ${error.message}`);
        }
    }

    // 添加消息到聊天历史
    function addMessage(chatContainer, type, content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}`;
        messageDiv.innerHTML = `<div class="message-content">${content}</div>`;
        chatContainer.appendChild(messageDiv);
        chatContainer.scrollTop = chatContainer.scrollHeight;
        return messageDiv;
    }

    // 添加回答消息
    function addAnswerMessage(chatContainer, answer) {
        const answerDiv = document.createElement('div');
        answerDiv.className = 'message bot';
        answerDiv.innerHTML = `<div class="message-content">${answer}</div>`;
        chatContainer.appendChild(answerDiv);
        chatContainer.scrollTop = chatContainer.scrollHeight;
    }

    // 导入示例数据（同时导入到知识图谱和RAG）
    async function importSampleData() {
        importBtn.disabled = true;
        importBtn.textContent = '导入中...';
        
        try {
            // 导入到知识图谱
            const graphResponse = await fetch(`${API_BASE}/api/import`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ 
                    documents: sampleDocuments.map(content => ({ content }))
                })
            });
            
            const graphData = await graphResponse.json();
            
            // 导入到RAG
            const ragResponse = await fetch(`${API_BASE}/api/rag-import`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ documents: sampleDocuments })
            });
            
            const ragData = await ragResponse.json();
            
            // 更新统计
            graphStats.textContent = `图谱: ${graphData.nodes_count} 节点, ${graphData.edges_count} 关系`;
            ragStats.textContent = `RAG: ${ragData.documents_count} 文档`;
            
            // 添加系统消息
            addMessage(ragChatHistory, 'system', `✅ ${ragData.message}`);
            addMessage(graphragChatHistory, 'system', `✅ ${graphData.message}`);
            
            // 刷新图谱
            setTimeout(loadGraph, 500);
            
        } catch (error) {
            addMessage(ragChatHistory, 'bot', `❌ 导入失败: ${error.message}`);
            addMessage(graphragChatHistory, 'bot', `❌ 导入失败: ${error.message}`);
        } finally {
            importBtn.disabled = false;
            importBtn.textContent = '导入示例数据';
        }
    }

    // 从文件导入数据
    async function importFromFile(file) {
        importFileBtn.disabled = true;
        importFileBtn.textContent = '导入中...';
        
        try {
            // 读取文件内容
            const text = await file.text();
            const lines = text.split('\n').filter(line => line.trim());
            
            // 导入到知识图谱
            const graphResponse = await fetch(`${API_BASE}/api/import-file`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ 
                    file_path: `data/${file.name}`
                })
            });
            
            const graphData = await graphResponse.json();
            
            // 导入到RAG
            const ragResponse = await fetch(`${API_BASE}/api/rag-import-file`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ 
                    file_path: `data/${file.name}`
                })
            });
            
            const ragData = await ragResponse.json();
            
            // 更新统计
            if (graphData.nodes_count !== undefined) {
                graphStats.textContent = `图谱: ${graphData.nodes_count} 节点, ${graphData.edges_count} 关系`;
            }
            if (ragData.documents_count !== undefined) {
                ragStats.textContent = `RAG: ${ragData.documents_count} 文档`;
            }
            
            // 添加系统消息
            if (ragData.message) {
                addMessage(ragChatHistory, 'system', `✅ ${ragData.message}`);
            }
            if (graphData.message) {
                addMessage(graphragChatHistory, 'system', `✅ ${graphData.message}`);
            }
            
            // 刷新图谱
            setTimeout(loadGraph, 500);
            
        } catch (error) {
            addMessage(ragChatHistory, 'bot', `❌ 导入失败: ${error.message}`);
            addMessage(graphragChatHistory, 'bot', `❌ 导入失败: ${error.message}`);
        } finally {
            importFileBtn.disabled = false;
            importFileBtn.textContent = '从文件导入';
        }
    }

    // 事件监听
    sendBtn.addEventListener('click', sendCompareQuery);
    importBtn.addEventListener('click', importSampleData);
    
    // 从文件导入按钮点击事件
    importFileBtn.addEventListener('click', () => {
        fileInput.click();
    });
    
    // 文件选择后的处理
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            fileName.textContent = `已选择: ${file.name}`;
            importFromFile(file);
        }
    });
    
    questionInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendCompareQuery();
        }
    });

    // 初始化
    checkStatus();
    setInterval(checkStatus, 5000);
});
