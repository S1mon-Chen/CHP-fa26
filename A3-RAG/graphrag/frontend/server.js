/**
 * GraphRAG 前端服务
 * 使用 Express 提供静态文件服务
 */

const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;

// 静态文件服务
app.use(express.static(path.join(__dirname, 'public')));

// API 代理到后端
const http = require('http');

app.use('/api', (req, res) => {
    const options = {
        hostname: 'localhost',
        port: 5001,
        path: req.url,
        method: req.method,
        headers: {
            'Content-Type': 'application/json',
            ...req.headers
        }
    };

    const proxyReq = http.request(options, (proxyRes) => {
        res.writeHead(proxyRes.statusCode, proxyRes.headers);
        proxyRes.pipe(res);
    });

    req.pipe(proxyReq);

    proxyReq.on('error', (err) => {
        res.status(503).json({ error: '后端服务未启动' });
    });
});

app.listen(PORT, () => {
    console.log(`🚀 前端服务已启动`);
    console.log(`📡 服务地址: http://localhost:${PORT}`);
});
