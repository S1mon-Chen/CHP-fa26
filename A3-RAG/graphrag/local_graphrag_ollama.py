"""
GraphRAG 课程 - 完整的全本地 Ollama 与 Qwen 模型实现
严格遵循先前 Page 15-16 的代码片段概念逻辑，实现 0 API Key 本地隐私 GraphRAG 实验。

注意：使用 9B 规格模型进行本地索引抽取需要耗费大量的 RAM (建议 >=16GB) 和时间。
      如果系统只有 CPU，构建过程可能会极其漫长。

前置支持需求:
1. 正在运行的本地 Ollama 服务 (`ollama serve`)
2. 已拉取的模型 (`ollama pull qwen2.5`)
"""

import os
import networkx as nx
import time
from typing import List, Optional

# LlamaIndex 核心组件
from llama_index.core import Document, Settings

# 具体实现组件（用于本地 LLM 和本地嵌入模型）
# 1. LLM: 使用 Ollama
from llama_index.llms.ollama import Ollama
# 2. Embedding: 使用 Ollama 内置嵌入功能 (完全免 Key，无需网络)
from llama_index.embeddings.ollama import OllamaEmbedding


# ---------------------------------------------------------
# [注意] 为了尊重先前课件 nomenclatura (概念命名)，
# 我们在这里模拟第 14-15 页提到的概念类结构，使其适用于全本地。
# ---------------------------------------------------------

class LocalGraphRAGStore:
    """模拟 Page 14/15 提到的 GraphRAGStore。使用 NetworkX 负责后端存储。"""
    def __init__(self, type: str = "networkx"):
        self.type = type
        if type == "networkx":
            # 在全本地模式下，默认使用内存中存储图谱结构。
            self.graph = nx.DiGraph()  # 使用有向图
        print(f"  [Store] 本地图存储后端已初始化 (NetworkX Memory)。")

    def add_triplet(self, subject: str, relation: str, obj: str):
        """添加三元组到图谱"""
        self.graph.add_node(subject)
        self.graph.add_node(obj)
        self.graph.add_edge(subject, obj, relation=relation)

    def get_graph(self):
        """获取图谱对象"""
        return self.graph

# =========================================================
# 知识提取器：模拟 Slide 中提到的 kg_extractors 概念
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
# 主程序：全本地 Ollama GraphRAG 流程
# =========================================================

def run_complete_local_graph_rag_workflow():
    """
    运行基于全本地 Ollama 和 Qwen 模型的完整 GraphRAG 流程。
    从数据准备、本地驱动设置、索引构建到查询实战演示。
    无需任何 API Key。
    """

    print("--- 开始本地 Ollama GraphRAG 流程 (无需外部 Key) ---")
    print("\n运行环境提示：本地模型进行信息抽取耗时较长，请耐心等待。建议 16GB RAM。\n")

    # 配置 Ollama 模型名称 (请确保该模型已在本地通过 `ollama pull qwen2.5` 拉取)
    OLLAMA_MODEL = "qwen2.5"
    # 配置 Ollama 服务地址 (默认是 localhost:11434)
    OLLAMA_BASE_URL = "http://localhost:11434"

    # =====================================================
    # 核心构建路径 (Indexing Path) - 基于先前 Page 15 内容概念，适配本地
    # =====================================================
    print("\n[构建索引] 正在本地 Ollama 环境下执行代码级核心逻辑剖析...")

    # --- 步骤 0: 配置本地 Embedding 模型 (免 Key) ---
    print("  > 步骤 0: 配置本地 Embedding 模型 (免 Key，使用 Ollama 内置嵌入)...")
    embed_model = OllamaEmbedding(
        model_name=OLLAMA_MODEL,
        base_url=OLLAMA_BASE_URL,
        request_timeout=60.0
    )
    Settings.embed_model = embed_model

    # --- 步骤 1: LLM 配置 (适配先前 Slide 的"LLM配置"，实例化 Ollama) ---
    print(f"  > 步骤 1: LLM 配置 (使用本地 {OLLAMA_MODEL} 模型)...")
    local_llm = Ollama(
        model=OLLAMA_MODEL,
        base_url=OLLAMA_BASE_URL,
        temperature=0.1,
        request_timeout=300.0
    )
    Settings.llm = local_llm

    # --- 步骤 2: 配置存储后端 (保持概念逻辑名称与 Slide 15 连贯) ---
    print("  > 步骤 2: 配置存储后端 (初始化先前提到的 Store 类)...")
    graph_store = LocalGraphRAGStore(type="networkx")

    # --- 步骤 3: 定义图提取器 ---
    print("  > 步骤 3: 定义图提取器...")
    extractor = SimpleKGExtractor(llm=local_llm)
    print("  > 提示：本地模式下，将使用 SimpleKGExtractor 调用 Ollama LLM 进行知识抽取...")

    # --- 步骤 4: 准备数据并构建索引 ---
    print("  > 步骤 4: 准备数据并构建索引...")
    documents = [
        Document(text="张三是李四的父亲。李四在谷歌公司工作。"),
        Document(text="谷歌公司位于美国加州山景城。王五是谷歌公司的 CEO。"),
        Document(text="张三退休前是一名的士司机。王五提倡人工智能技术。")
    ]

    print("    [耗时预警] 这步会调用本地模型遍历所有文档进行知识抽取...")
    start_time_build = time.time()

    # 遍历文档提取知识
    for doc in documents:
        print(f"    处理文档: {doc.text[:30]}...")
        triplets = extractor.extract(doc.text)
        print(f"    提取到 {len(triplets)} 个三元组")
        for subj, rel, obj in triplets:
            print(f"      ({subj}, {rel}, {obj})")
            graph_store.add_triplet(subj, rel, obj)

    end_time_build = time.time()
    graph = graph_store.get_graph()
    print(f"✓ 全本地 GraphRAG 知识图谱索引构建完成。共耗时: {end_time_build - start_time_build:.2f} 秒。")
    print(f"  - 节点数: {len(graph.nodes)}")
    print(f"  - 边数: {len(graph.edges)}")

    # =====================================================
    # 查询增强路径 (Retrieval-Generation Path) - 基于先前 Page 16 内容概念
    # =====================================================
    print("\n[查询引擎设置] 配置查询引擎...")
    print("  > 使用本地 LLM 作为查询引擎...")

    def local_query(question: str) -> str:
        """本地搜索模式：基于图谱信息回答问题"""
        # 获取图谱信息作为上下文
        context_lines = []
        for u, v, data in graph.edges(data=True):
            context_lines.append(f"{u} {data.get('relation', '')} {v}")
        
        context = "\n".join(context_lines)
        prompt = f"""根据以下知识图谱信息回答问题：

知识图谱：
{context}

问题：{question}

请根据提供的知识图谱信息回答问题，如果信息不足请说明。
"""
        response = local_llm.complete(prompt)
        return response.text.strip()

    def global_query(question: str) -> str:
        """全局搜索模式：基于原始文档回答问题"""
        context = "\n".join([doc.text for doc in documents])
        prompt = f"""根据以下文档内容回答问题：

文档内容：
{context}

问题：{question}

请根据提供的文档内容回答问题。
"""
        response = local_llm.complete(prompt)
        return response.text.strip()

    print("✓ 查询引擎配置完成。")

    # =====================================================
    # 查询实战演示
    # =====================================================
    print("\n--- 查询实战演示 ---")

    test_questions = [
        "张三的职业是什么？",
        "李四在哪里工作？",
        "王五和谷歌公司是什么关系？",
        "张三和王五之间有什么关系？"
    ]

    for idx, question in enumerate(test_questions, 1):
        print(f"\n【问题 {idx}】: {question}")

        # 本地搜索模式 (基于图谱)
        print("  [本地搜索模式 (基于图谱)]")
        start_time = time.time()
        local_response = local_query(question)
        end_time = time.time()
        print(f"    答案: {local_response}")
        print(f"    耗时: {end_time - start_time:.2f} 秒")

        # 全局搜索模式 (基于文档)
        print("  [全局搜索模式 (基于文档)]")
        start_time = time.time()
        global_response = global_query(question)
        end_time = time.time()
        print(f"    答案: {global_response}")
        print(f"    耗时: {end_time - start_time:.2f} 秒")

    print("\n--- 全本地 Ollama GraphRAG 流程演示完成 ---")
    print("\n总结：")
    print("- 成功实现了全本地的 GraphRAG 知识图谱构建与查询")
    print("- 使用 Ollama + Qwen2.5 模型，无需任何 API Key")
    print("- 使用 Ollama 内置嵌入功能")
    print("- 支持本地搜索（基于图谱）和全局搜索（基于文档）两种查询模式")

if __name__ == "__main__":
    run_complete_local_graph_rag_workflow()
