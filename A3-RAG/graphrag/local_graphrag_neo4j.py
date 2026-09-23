"""
GraphRAG 课程 - 使用 Neo4j 作为后端存储的全本地 Ollama 实现
实现将知识图谱持久化到 Neo4j 数据库中。

前置支持需求:
1. 正在运行的本地 Ollama 服务 (`ollama serve`)
2. 已拉取的模型 (`ollama pull qwen2.5`)
3. 正在运行的 Neo4j 数据库服务
"""

import os
import time
from typing import List, Optional
from neo4j import GraphDatabase, exceptions

# LlamaIndex 核心组件
from llama_index.core import Settings

# Ollama LLM 和 Embedding
from llama_index.llms.ollama import Ollama
from llama_index.embeddings.ollama import OllamaEmbedding


# =========================================================
# Neo4j 图存储类
# =========================================================

class Neo4jGraphRAGStore:
    """使用 Neo4j 作为后端存储的图存储类"""
    
    def __init__(self, uri: str = "bolt://localhost:7687", 
                 username: str = "neo4j", password: str = "password",
                 database: str = "neo4j"):
        """
        初始化 Neo4j 图存储
        :param uri: Neo4j 连接地址
        :param username: Neo4j 用户名
        :param password: Neo4j 密码
        :param database: 数据库名称
        """
        self.uri = uri
        self.username = username
        self.password = password
        self.database = database
        self.driver = None
        self._connect()
    
    def _connect(self):
        """连接到 Neo4j 数据库"""
        try:
            self.driver = GraphDatabase.driver(self.uri, auth=(self.username, self.password))
            # 验证连接
            self.driver.verify_connectivity()
            print(f"  [Store] Neo4j 图存储后端已初始化 (URI: {self.uri}, 数据库: {self.database})")
        except Exception as e:
            print(f"  [Store] Neo4j 连接失败: {e}")
            raise
    
    def close(self):
        """关闭数据库连接"""
        if self.driver:
            self.driver.close()
    
    def clear_database(self):
        """清空数据库中的所有节点和关系（用于测试）"""
        if self.driver:
            try:
                with self.driver.session(database=self.database) as session:
                    session.run("MATCH (n) DETACH DELETE n")
                print("  [Store] 已清空数据库")
            except Exception as e:
                print(f"  [Store] 清空数据库失败: {e}")
    
    def add_triplet(self, subject: str, relation: str, obj: str):
        """添加三元组到图谱"""
        if self.driver:
            with self.driver.session(database=self.database) as session:
                session.run("""
                    MERGE (a:Entity {name: $subj})
                    MERGE (b:Entity {name: $obj})
                    MERGE (a)-[r:RELATION {name: $rel}]->(b)
                """, subj=subject, obj=obj, rel=relation)
    
    def get_all_triplets(self):
        """获取所有三元组"""
        if self.driver:
            with self.driver.session(database=self.database) as session:
                results = session.run("MATCH (a)-[r]->(b) RETURN a.name, r.name, b.name")
                return [(record["a.name"], record["r.name"], record["b.name"]) for record in results]
        return []
    
    def get_node_count(self):
        """获取节点数量"""
        if self.driver:
            with self.driver.session(database=self.database) as session:
                result = session.run("MATCH (n) RETURN count(n) as count").single()
                return result["count"] if result else 0
        return 0
    
    def get_relation_count(self):
        """获取关系数量"""
        if self.driver:
            with self.driver.session(database=self.database) as session:
                result = session.run("MATCH ()-[r]->() RETURN count(r) as count").single()
                return result["count"] if result else 0
        return 0

# =========================================================
# 知识提取器
# =========================================================

class SimpleKGExtractor:
    """简化的知识图谱提取器，使用本地 LLM 从文本中抽取三元组"""
    
    def __init__(self, llm):
        self.llm = llm
    
    def extract(self, text: str) -> List[tuple]:
        """从文本中提取三元组 (subject, relation, object)"""
        prompt = f"""从以下文本中提取实体和关系，严格按照指定格式输出：

文本：{text}

输出格式要求：
1. 每行输出一个三元组
2. 格式为：实体1|关系|实体2
3. 不要添加任何额外解释文字
4. 关系用简短的中文描述

示例输出：
张三|父亲|李四
李四|工作单位|谷歌公司
"""
        response = self.llm.complete(prompt)
        triplets = []
        for line in response.text.strip().split('\n'):
            line = line.strip()
            if line and '|' in line:
                parts = line.split('|')
                if len(parts) >= 3:
                    subj = parts[0].strip()
                    rel = parts[1].strip()
                    obj = '|'.join(parts[2:]).strip()
                    triplets.append((subj, rel, obj))
        return triplets

# =========================================================
# 主程序：使用 Neo4j 的全本地 Ollama GraphRAG 流程
# =========================================================

def run_neo4j_graph_rag_workflow():
    """
    运行使用 Neo4j 作为后端存储的完整 GraphRAG 流程。
    从数据准备、本地驱动设置、索引构建到查询实战演示。
    """

    print("--- 开始 Neo4j 后端的本地 Ollama GraphRAG 流程 ---")
    print("\n运行环境提示：本地模型进行信息抽取耗时较长，请耐心等待。\n")

    # 配置 Ollama
    OLLAMA_MODEL = "qwen2.5"
    OLLAMA_BASE_URL = "http://localhost:11434"

    # 配置 Neo4j（使用用户提供的配置）
    NEO4J_URI = "bolt://localhost:7687"
    NEO4J_USERNAME = "neo4j"
    NEO4J_PASSWORD = "reins2011!"  # 用户提供的密码
    NEO4J_DATABASE = "neo4j"

    # =====================================================
    # 配置本地 LLM 和 Embedding
    # =====================================================
    print("\n[配置 LLM] 使用本地 Ollama...")
    local_llm = Ollama(
        model=OLLAMA_MODEL,
        base_url=OLLAMA_BASE_URL,
        temperature=0.1,
        request_timeout=300.0
    )
    Settings.llm = local_llm

    print("[配置 Embedding] 使用 Ollama 内置嵌入...")
    embed_model = OllamaEmbedding(
        model_name=OLLAMA_MODEL,
        base_url=OLLAMA_BASE_URL
    )
    Settings.embed_model = embed_model

    # =====================================================
    # 连接 Neo4j 数据库
    # =====================================================
    print("\n[配置 Neo4j] 连接到图数据库...")
    try:
        graph_store = Neo4jGraphRAGStore(
            uri=NEO4J_URI,
            username=NEO4J_USERNAME,
            password=NEO4J_PASSWORD,
            database=NEO4J_DATABASE
        )
        # 清空数据库（测试用）
        graph_store.clear_database()
    except Exception as e:
        print(f"✗ Neo4j 连接失败，请检查数据库配置和服务状态")
        print(f"详细错误：{e}")
        return

    # =====================================================
    # 知识提取器
    # =====================================================
    print("\n[配置提取器] 初始化知识图谱提取器...")
    extractor = SimpleKGExtractor(llm=local_llm)

    # =====================================================
    # 准备数据并构建索引
    # =====================================================
    print("\n[构建知识图谱] 从文档中提取三元组并存储到 Neo4j...")
    documents = [
        "张三是李四的父亲。李四在谷歌公司工作。",
        "谷歌公司位于美国加州山景城。王五是谷歌公司的 CEO。",
        "张三退休前是一名的士司机。王五提倡人工智能技术。"
    ]

    start_time_build = time.time()
    
    # 遍历文档提取知识并存储到 Neo4j
    for doc in documents:
        print(f"  处理文档: {doc[:30]}...")
        triplets = extractor.extract(doc)
        print(f"  提取到 {len(triplets)} 个三元组")
        
        for subj, rel, obj in triplets:
            print(f"    ({subj}, {rel}, {obj})")
            graph_store.add_triplet(subj, rel, obj)

    end_time_build = time.time()
    
    print(f"\n✓ 知识图谱构建完成！共耗时: {end_time_build - start_time_build:.2f} 秒")

    # =====================================================
    # 统计图谱信息
    # =====================================================
    print("\n[图谱统计]")
    print(f"  - 节点数: {graph_store.get_node_count()}")
    print(f"  - 边数: {graph_store.get_relation_count()}")
    
    print("\n  [图谱结构]")
    all_triplets = graph_store.get_all_triplets()
    for subj, rel, obj in all_triplets:
        print(f"    {subj} --{rel}--> {obj}")

    # =====================================================
    # 查询实战演示
    # =====================================================
    print("\n--- 查询实战演示 ---")

    def query_graph(question: str) -> str:
        """使用 LLM 和 Neo4j 知识图谱回答问题"""
        # 从 Neo4j 获取所有知识
        context_lines = []
        all_triplets = graph_store.get_all_triplets()
        for subj, rel, obj in all_triplets:
            context_lines.append(f"{subj} {rel} {obj}")
        
        context = "\n".join(context_lines)
        prompt = f"""根据以下知识图谱信息回答问题：

知识图谱：
{context}

问题：{question}

请根据提供的知识图谱信息回答问题，如果信息不足请说明。
"""
        response = local_llm.complete(prompt)
        return response.text.strip()

    test_questions = [
        "张三的职业是什么？",
        "李四在哪里工作？",
        "王五和谷歌公司是什么关系？",
        "张三和王五之间有什么关系？"
    ]

    for idx, question in enumerate(test_questions, 1):
        print(f"\n【问题 {idx}】: {question}")
        start_time = time.time()
        response = query_graph(question)
        end_time = time.time()
        print(f"  答案: {response}")
        print(f"  耗时: {end_time - start_time:.2f} 秒")

    # 关闭数据库连接
    graph_store.close()
    
    print("\n--- Neo4j 后端 GraphRAG 流程演示完成 ---")
    print("\n总结：")
    print("- 成功实现了使用 Neo4j 作为后端存储的 GraphRAG")
    print("- 使用 Ollama + Qwen2.5 模型，无需任何 API Key")
    print("- 知识图谱持久化存储在 Neo4j 中")
    print("- 支持基于图谱的智能查询")

if __name__ == "__main__":
    run_neo4j_graph_rag_workflow()
