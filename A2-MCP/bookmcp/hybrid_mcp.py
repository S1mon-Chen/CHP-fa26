from fastmcp import FastMCP
from pydantic import BaseModel, Field
import mysql.connector
from typing import List, Optional, Dict, Any

app = FastMCP(
    name="Bookstore-MCP",
    version="1.0.0"
)

DB_CONFIG = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'root',
    'password': 'reins2011!',
    'database': 'bookstore'
}

def get_db_connection():
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except Exception as err:
        print(f"数据库连接错误: {err}")
        return None

def search_books_internal(query: str = "") -> List[Dict[str, Any]]:
    conn = get_db_connection()
    if not conn:
        return []
    
    try:
        cursor = conn.cursor(dictionary=True)
        
        if query:
            query = query.lower()
            sql = """SELECT * FROM book WHERE 
                     name LIKE %s OR 
                     author LIKE %s OR 
                     type LIKE %s OR
                     description LIKE %s"""
            pattern = f"%{query}%"
            cursor.execute(sql, (pattern, pattern, pattern, pattern))
        else:
            cursor.execute('SELECT * FROM book')
        
        books_data = cursor.fetchall()
        cursor.close()
        conn.close()
        
        return books_data
    
    except Exception as err:
        if conn:
            conn.close()
        return []

@app.tool(
    name="search_books",
    description="根据关键词搜索书籍信息"
)
def search_books(query: str = Field(description="搜索关键词，可以是书名、作者或类型")) -> Dict[str, Any]:
    """
    根据关键词搜索书籍信息
    
    Args:
        query: 搜索关键词
        
    Returns:
        匹配的书籍列表
    """
    books = search_books_internal(query)
    return {"books": books}

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
    books = search_books_internal("")
    return {"books": books}

if __name__ == "__main__":
    print("FastMCP服务器启动中...")
    print("地址: http://127.0.0.1:8000/mcp")
    app.run(transport="streamable-http", show_banner=True)