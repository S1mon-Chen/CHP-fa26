import chromadb

# 连接到 Chroma 服务器
client = chromadb.HttpClient(host='localhost', port=8000)

# 列出所有集合
print("=== 集合列表 ===")
collections = client.list_collections()
for collection in collections:
    print(f"- 集合名称: {collection.name}")
    print(f"  项目数量: {collection.count()}")
    print()

# 获取 books 集合
print("=== 获取 books 集合 ===")
try:
    collection = client.get_collection(name="books")
    print(f"成功获取集合: {collection.name}")
    
    # 添加测试数据
    print("\n=== 添加测试数据 ===")
    collection.add(
        ids=["test1", "test2"],
        documents=["测试书籍 1", "测试书籍 2"],
        metadatas=[
            {"title": "测试书 1", "author": "测试作者 1"},
            {"title": "测试书 2", "author": "测试作者 2"}
        ],
        embeddings=[
            [0.1, 0.2, 0.3],
            [0.4, 0.5, 0.6]
        ]
    )
    print("成功添加测试数据")
    print(f"现在集合中的项目数量: {collection.count()}")
    
    # 查看数据
    print("\n=== 查看数据 ===")
    results = collection.get(limit=10)
    print(f"数据数量: {len(results['ids'])}")
    
    for i in range(len(results['ids'])):
        print(f"  数据 {i+1}:")
        print(f"    ID: {results['ids'][i]}")
        print(f"    文档: {results['documents'][i]}")
        if 'metadatas' in results and results['metadatas'][i]:
            print(f"    元数据: {results['metadatas'][i]}")
        print()
        
except Exception as e:
    print(f"操作失败: {e}")

print("=== 操作完成 ===")