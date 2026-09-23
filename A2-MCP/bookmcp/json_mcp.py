from fastmcp import FastMCP
from pydantic import BaseModel, Field
from typing import List, Dict, Any

app = FastMCP(
    name="JSON-Bookstore-MCP",
    version="1.0.0"
)

# 硬编码的书籍数据
BOOKS_DATA = [
    {
        "id": 1,
        "title": "The Great Gatsby",
        "author": "F. Scott Fitzgerald",
        "count": 15
    },
    {
        "id": 2,
        "title": "To Kill a Mockingbird",
        "author": "Harper Lee",
        "count": 8
    },
    {
        "id": 3,
        "title": "Animal Farm",
        "author": "George Orwell",
        "count": 14
    },
    {
        "id": 4,
        "title": "Pride and Prejudice",
        "author": "Jane Austen",
        "count": 12
    },
    {
        "id": 5,
        "title": "The Catcher in the Rye",
        "author": "J.D. Salinger",
        "count": 6
    },
    {
        "id": 6,
        "title": "Lord of the Flies",
        "author": "William Golding",
        "count": 10
    },
    {
        "id": 7,
        "title": "The Hobbit",
        "author": "J.R.R. Tolkien",
        "count": 18
    },
    {
        "id": 8,
        "title": "Fahrenheit 451",
        "author": "Ray Bradbury",
        "count": 3
    },
    {
        "id": 9,
        "title": "Jane Eyre",
        "author": "Charlotte Brontë",
        "count": 9
    },
    {
        "id": 10,
        "title": "The Chronicles of Narnia",
        "author": "C.S. Lewis",
        "count": 13
    }
]

@app.tool(
    name="search_books",
    description="根据关键词搜索书籍信息"
)
def search_books(query: str = Field(description="搜索关键词，可以是书名或作者")) -> Dict[str, Any]:
    """
    根据关键词搜索书籍信息
    
    Args:
        query: 搜索关键词
        
    Returns:
        匹配的书籍列表
    """
    if not query:
        return {"books": BOOKS_DATA}
    
    query = query.lower()
    results = []
    
    for book in BOOKS_DATA:
        title = book.get("title", "").lower()
        author = book.get("author", "").lower()
        
        if query in title or query in author:
            results.append(book)
    
    return {"books": results}

@app.tool(
    name="get_all_books",
    description="获取所有书籍信息"
)
def get_all_books() -> Dict[str, Any]:
    """
    获取所有书籍信息
    
    Returns:
        所有书籍列表
    """
    return {"books": BOOKS_DATA}

@app.tool(
    name="get_book_by_id",
    description="根据ID获取书籍信息"
)
def get_book_by_id(book_id: int = Field(description="书籍ID")) -> Dict[str, Any]:
    """
    根据ID获取书籍信息
    
    Args:
        book_id: 书籍ID
        
    Returns:
        匹配的书籍信息
    """
    for book in BOOKS_DATA:
        if book.get("id") == book_id:
            return {"book": book}
    
    return {"book": None, "message": "书籍未找到"}

@app.tool(
    name="get_total_count",
    description="获取书籍总数量"
)
def get_total_count() -> Dict[str, Any]:
    """
    获取书籍总数量
    
    Returns:
        书籍总数和库存总数
    """
    total_books = len(BOOKS_DATA)
    total_count = sum(book.get("count", 0) for book in BOOKS_DATA)
    
    return {
        "total_books": total_books,
        "total_count": total_count
    }

if __name__ == "__main__":
    print("JSON MCP服务器启动中...")
    print("地址: http://127.0.0.1:8001/mcp")
    app.run(transport="streamable-http", show_banner=True, port=8001)