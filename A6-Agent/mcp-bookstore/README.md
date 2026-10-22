# MCP 书店服务器

## 系统要求

- **Python**: 3.13 或更高版本
- **包管理器**: UV（推荐）或 pip

## 安装

### 方式一：使用 UV（推荐）

1. **安装 UV**（如果尚未安装）:
   ```bash
   curl -LsSf https://astral.sh/uv/install.sh | sh
   # 或在 Windows 上:
   # powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
   ```

2. **克隆并导航到项目目录**:
   ```bash
   cd mcp-bookstore
   ```

3. **安装依赖项**（这会自动安装 python 3.13 和所有用到的依赖到 `.venv` 目录下）:
   ```bash
   uv sync
   ```

### 方式二：使用 pip 和虚拟环境

1. **导航到项目目录**:
   ```bash
   cd mcp-bookstore
   ```

2. **创建并激活虚拟环境**:
   ```bash
   python -m venv .venv
   source .venv/bin/activate  # 在 Windows 上: venv\Scripts\activate
   ```

3. **安装依赖项**:
   ```bash
   pip install -e .
   ```

## 启动 MCP 服务器

### HTTP 模式

以 HTTP 模式启动服务器，便于与 Cherry Studio 等 AI 客户端集成：

```bash
# 使用 UV
uv run fastmcp run src/bookstore_mcp/server.py -t http --port 8000

# 使用已激活的虚拟环境
fastmcp run src/bookstore_mcp/server.py -t http --port 8000
```

服务器启动后会显示：
```
INFO     Starting MCP server 'BookStore' with transport 'http' on http://127.0.0.1:8000/mcp
```

## 服务器配置

- **HTTP 端点**: `http://localhost:8000/mcp`
- **端口**: 8000（可通过 `--port` 标志配置）
- **数据文件**: `data/books.json`（包含图书库存的 JSON 文件）

## Cherry Studio 集成

[Cherry Studio](https://www.cherry-ai.com/) 是一款功能强大的全能 AI 助手平台，集成了多模型对话、知识库管理、AI 绘画、翻译等功能。通过 MCP（模型上下文协议）支持，Cherry Studio 可以无缝集成本服务，提供图书管理功能。

### 安装 Cherry Studio

1. **下载客户端**：
   - 访问 [Cherry Studio 官网](https://docs.cherry-ai.com/cherry-studio/download) 下载适合您系统的版本
   - 支持 Windows、macOS 和 Linux

2. **完成安装**：
   - 按照安装向导完成 Cherry Studio 的安装
   - 首次启动时会进入欢迎界面

### 配置 MCP 服务器

1. **启动书店 MCP 服务器**（以 HTTP 模式）：
   ```bash
   uv run fastmcp run src/bookstore_mcp/server.py -t http --port 8000
   ```

2. **打开 Cherry Studio**：
   - 启动 Cherry Studio 应用程序
   - 进入 **设置** → **MCP 服务器**

3. **添加 MCP 服务器配置**：
   - 点击 **添加服务器**
   - 填写以下配置信息：
     - **名称**: `书城 MCP`
     - **类型**: `streamableHttp`
     - **URL**: `http://localhost:8000/mcp`
     - **描述**: `书城管理和搜索工具`（可选）

4. **保存并测试连接**：
   - 点击 **保存** 保存配置
   - 点击 **测试连接** 确认服务器连接正常
   - 连接成功后，工具将自动可用

### 使用书店 MCP 工具

连接成功后，您可以在 Cherry Studio 的对话中使用以下功能。当前书店库存包含10本经典文学作品。

#### 可用的 MCP 工具

本书店服务器提供以下工具：

| 工具名称 | 功能描述 | 参数 |
|---------|----------|------|
| `get_all_books` | 获取所有书籍列表 | 无 |
| `get_book_by_id` | 根据ID获取特定书籍 | book_id (整数) |
| `search_books_by_title` | 按书名搜索 | title_query (字符串) |
| `search_books_by_author` | 按作者搜索 | author_query (字符串) |
| `get_books_in_stock` | 获取有库存的书籍 | 无 |
| `buy_book` | 购买书籍 | book_id (整数), quantity (整数，默认1) |
| `check_availability` | 检查库存可用性 | book_id (整数), quantity (整数，默认1) |
| `restock_book` | 补充库存 | book_id (整数), quantity (整数) |

#### 使用示例

以下是常用的查询和操作示例：

**浏览和搜索功能**
- 显示书店中的所有书籍
- 搜索书名包含"Great"的书籍
- 查找书名里包含"Hobbit"的图书
- 查找George Orwell的所有作品
- 搜索J.R.R. Tolkien写的书
- 找一找Harper Lee的书籍
- 显示目前有库存的所有书籍
- 哪些书还有现货？

**书籍信息查询**
- 显示ID为1的书籍详细信息
- 查看书籍编号3的详情
- 检查《The Great Gatsby》是否有库存
- ID为7的书籍还有多少本？
- 确认《Animal Farm》能否购买2本

**购买和库存管理**
- 购买一本《To Kill a Mockingbird》
- 买2本ID为3的书籍
- 我要买《Pride and Prejudice》
- 为《The Catcher in the Rye》补货10本
- 给ID为8的书籍增加5本库存
- 补充《Fahrenheit 451》的库存数量15本

#### 实际对话示例

以下是使用书城MCP工具的实际对话记录，展示了如何通过自然语言与书城系统进行交互：

> **用户：** 书城有什么书
> 
> **GLM-4.5-Flash | 智譜開放平台：**
> 
> 我来帮您查看书城有哪些书。
> 
> **书城MCP : get_all_books** 已完成
> 
> 书城有以下书籍：
> 
> - The Great Gatsby (了不起的盖茨比) - F. Scott Fitzgerald，库存：15本
> - To Kill a Mockingbird (杀死一只知更鸟) - Harper Lee，库存：8本
> - Animal Farm (动物农场) - George Orwell，库存：14本
> - Pride and Prejudice (傲慢与偏见) - Jane Austen，库存：12本
> - The Catcher in the Rye (麦田里的守望者) - J.D. Salinger，库存：6本
> - Lord of the Flies (蝇王) - William Golding，库存：10本
> - The Hobbit (霍比特人) - J.R.R. Tolkien，库存：18本
> - Fahrenheit 451 (华氏451度) - Ray Bradbury，库存：7本
> - Jane Eyre (简·爱) - Charlotte Brontë，库存：9本
> - The Chronicles of Narnia (纳尼亚传奇) - C.S. Lewis，库存：13本
> 
> 书城共有10种书籍，涵盖了经典文学作品，库存总数为112本。如果您想了解某本书的详细信息或购买，请告诉我书名或ID。

> **用户：** 买两本 Ray Bradbury 的书
> 
> **GLM-4.5-Flash | 智譜開放平台：**
> 
> 我需要先找到Ray Bradbury的书籍ID，然后为您购买两本。
> 
> **书城MCP : search_books_by_author** 已完成
> 
> **书城MCP : buy_book** 已完成
> 
> 已成功购买2本Ray Bradbury的《Fahrenheit 451》！

> **用户：** Fahrenheit 451 还有多少本
> 
> **GLM-4.5-Flash | 智譜開放平台：**
> 
> **书城MCP : check_availability** 已完成
> 
> 《Fahrenheit 451》目前还有 5本 在库。

### 更多资源

- [Cherry Studio 官方文档](https://docs.cherry-ai.com/)
- [MCP 配置教程](https://docs.cherry-ai.com/advanced-basic/mcp/config)

