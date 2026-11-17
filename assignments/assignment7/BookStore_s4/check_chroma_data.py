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

# 查看每个集合的内容
for collection in collections:
    print(f"=== 集合 '{collection.name}' 的内容 ===")
    try:
        # 获取前 10 条数据
        results = collection.get(limit=10)
        print(f"  数据数量: {len(results['ids'])}")
        
        # 显示数据
        for i in range(min(5, len(results['ids']))):  # 只显示前 5 条
            print(f"  数据 {i+1}:")
            print(f"    ID: {results['ids'][i]}")
            print(f"    文档: {results['documents'][i]}")
            if 'metadatas' in results and results['metadatas'][i]:
                print(f"    元数据: {results['metadatas'][i]}")
            print()
    except Exception as e:
        print(f"  读取数据时出错: {e}")
    print()

print("=== 操作完成 ===")