"""
GraphRAG 后端 API 服务
使用 Flask + Neo4j + Ollama 实现知识图谱问答
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
from llama_index.llms.ollama import Ollama
from llama_index.core import VectorStoreIndex, Document as LlamaDocument, Settings
from llama_index.embeddings.ollama import OllamaEmbedding
import networkx as nx
from neo4j import GraphDatabase
import json
import os
from pathlib import Path

app = Flask(__name__)
CORS(app, resources={r"/api/*": {"origins": "*"}})  # 允许跨域请求

# 配置
NEO4J_URI = "bolt://localhost:7687"
NEO4J_USERNAME = "neo4j"
NEO4J_PASSWORD = "reins2011!"
NEO4J_DATABASE = "neo4j"

# 初始化 Ollama LLM
llm = Ollama(model="qwen2.5", base_url="http://localhost:11434")

# 初始化 Embedding 模型和 RAG 索引
Settings.embed_model = OllamaEmbedding(model_name="mxbai-embed-large", base_url="http://localhost:11434")
Settings.llm = llm

# 全局变量：RAG索引和文档存储
rag_index = None
rag_documents = []
DATA_DIR = Path(__file__).parent.parent / "data"

# Neo4j 连接
class Neo4jConnection:
    def __init__(self):
        self.driver = GraphDatabase.driver(
            NEO4J_URI,
            auth=(NEO4J_USERNAME, NEO4J_PASSWORD),
            database=NEO4J_DATABASE
        )
    
    def close(self):
        self.driver.close()
    
    def query(self, cypher, parameters=None):
        with self.driver.session() as session:
            result = session.run(cypher, parameters)
            return [record.data() for record in result]

neo4j_conn = Neo4jConnection()

def extract_triplets(text: str) -> list:
    """使用 Ollama 从文本中提取三元组"""
    prompt = f"""从以下文本中提取实体和关系，严格按照指定格式输出：

文本：{text}

输出格式：每行一个三元组，使用 | 分隔，格式为：
subject|relation|object

重点提取以下类型的关系：
1. 人与人之间的关系（父亲、母亲、朋友、同事等）
2. 人与公司之间的关系（工作单位、CEO、创始人、员工等）
3. 公司与地点之间的关系（位于、总部位于等）
4. 公司与类型之间的关系（是...公司、行业为...等）
5. 人与职位之间的关系（担任、任职等）
6. 人与技术/观点之间的关系（提倡、支持、反对等）
7. 任何其他重要关系

示例输出：
苹果公司|行业|科技公司
蒂姆·库克|担任职位|苹果公司CEO
谷歌公司|位于|美国加州山景城

注意：
1. 只输出三元组，不要输出其他解释文字
2. 确保实体和关系简洁准确
3. "是"可以转化为"类型"、"行业"、"职位"等更具体的表述
4. 如果没有找到明确的实体关系，可以不输出
"""
    response = llm.complete(prompt)
    triplets = []
    for line in response.text.strip().split('\n'):
        line = line.strip()
        if '|' in line:
            parts = line.split('|')
            if len(parts) >= 3:
                subject = parts[0].strip()
                relation = parts[1].strip()
                obj = parts[2].strip()
                if subject and relation and obj:
                    triplets.append((subject, relation, obj))
    return triplets

def build_graph_from_neo4j():
    """从 Neo4j 构建 NetworkX 图用于路径查找"""
    G = nx.DiGraph()
    # 查询所有关系
    results = neo4j_conn.query("MATCH (a)-[r]->(b) RETURN a, type(r) as relation, b")
    for record in results:
        subject = list(record['a'].values())[0] if isinstance(record['a'], dict) else str(record['a'])
        relation = record['relation']
        obj = list(record['b'].values())[0] if isinstance(record['b'], dict) else str(record['b'])
        G.add_node(subject)
        G.add_node(obj)
        G.add_edge(subject, obj, relation=relation)
    return G

def find_entity_in_graph(G: nx.DiGraph, entity_name: str) -> str:
    """在图中查找匹配的实体名称"""
    entity_name_lower = entity_name.lower()

    # 精确匹配
    if entity_name in G.nodes:
        return entity_name

    # 模糊匹配：检查是否是某个实体的子串
    for node in G.nodes():
        if entity_name_lower in node.lower() or node.lower() in entity_name_lower:
            return node

    # 检查简称（如"库克"匹配"蒂姆·库克"）
    for node in G.nodes():
        node_parts = node.replace('·', ' ').replace('.', ' ').split()
        query_parts = entity_name.replace('·', ' ').replace('.', ' ').split()
        # 检查是否有共同的词
        for np in node_parts:
            for qp in query_parts:
                if len(qp) >= 2 and len(np) >= 2 and (qp in np or np in qp):
                    return node

    return None

def find_entities_by_type(G: nx.DiGraph, entity_type: str) -> tuple:
    """根据类型查找实体及其相关信息（如查找所有科技公司及在其工作的人）"""
    entities = []
    related_info = []

    # 1. 首先查找与类型相关的实体
    for u, v, data in G.edges(data=True):
        relation = data.get('relation', '').lower()
        # 检查是否是类型关系
        if any(t in relation for t in ['类型', '行业', '类别', '分类']):
            if entity_type.lower() in v.lower():
                entities.append(u)
                # 查找与该实体相关的人（通过工作单位等关系）
                for neighbor in G.neighbors(u):
                    edge_rel = G[u][neighbor].get('relation', '').lower()
                    # 检查是否是工作关系
                    if any(w in edge_rel for w in ['工作单位', '工作', '任职', '担任', '员工', 'CEO', '创始人', '负责人']):
                        related_info.append(f"{u} {edge_rel} {neighbor}")
                # 反向查找
                for predecessor in G.predecessors(u):
                    edge_rel = G[predecessor][u].get('relation', '').lower()
                    if any(w in edge_rel for w in ['工作单位', '工作', '任职', '担任', '员工', 'CEO', '创始人', '负责人']):
                        related_info.append(f"{predecessor} {edge_rel} {u}")

        # 检查是否实体名称包含类型词
        if entity_type.lower() in u.lower():
            entities.append(u)

    # 2. 对于每个找到的实体，查找与它有工作关系的其他人
    for entity in entities:
        # 查找所有直接相关的人（通过工作关系）
        for predecessor in G.predecessors(entity):
            edge_rel = G[predecessor][entity].get('relation', '').lower()
            # 如果这个关系涉及工作/人，添加这条信息
            if any(w in edge_rel for w in ['工作单位', '工作', '任职', '担任', '员工', 'CEO', '创始人', '负责人', '职位']):
                related_info.append(f"{predecessor} {edge_rel} {entity}")

        for neighbor in G.neighbors(entity):
            edge_rel = G[entity][neighbor].get('relation', '').lower()
            if any(w in edge_rel for w in ['工作单位', '工作', '任职', '担任', '员工', 'CEO', '创始人', '负责人', '职位']):
                related_info.append(f"{entity} {edge_rel} {neighbor}")

        # 3. 查找通过职位节点间接连接的实体（如"苹果公司CEO" -> "苹果公司"）
        for node in G.nodes():
            # 检查是否是该实体的职位变体（如"苹果公司CEO"是"苹果公司"的职位）
            if entity in node and node != entity:
                # 查找与这个职位节点相关的人
                for predecessor in G.predecessors(node):
                    edge_rel = G[predecessor][node].get('relation', '').lower()
                    if any(w in edge_rel for w in ['担任', '任职', '是']):
                        related_info.append(f"{predecessor} {edge_rel} {node} (即 {entity} 的职位)")

    return list(set(entities)), list(set(related_info))

def find_multi_hop_paths(G: nx.DiGraph, entities: list, max_hops: int = 3) -> list:
    """查找多跳路径"""
    context = []
    paths_found = set()
    matched_entities = set()

    for entity in entities:
        # 查找匹配的实体
        matched_entity = find_entity_in_graph(G, entity)
        if matched_entity:
            matched_entities.add(matched_entity)
        else:
            # 如果查询的实体不在图中，尝试在其他实体的名称中找
            for node in G.nodes():
                if entity.lower() in node.lower():
                    matched_entities.add(node)

    # 如果查询的是库克，也要搜索包含"库克"的实体
    for entity in entities:
        if '库克' in entity or 'Cook' in entity.lower():
            for node in G.nodes():
                if '库克' in node or 'Cook' in node:
                    matched_entities.add(node)

    for entity in matched_entities:
        if entity not in G.nodes:
            continue

        # 1跳：直接邻居
        for neighbor in G.neighbors(entity):
            edge_data = G[entity][neighbor]
            relation = edge_data.get('relation', '')
            path = f"{entity} {relation} {neighbor}"
            if path not in paths_found:
                context.append(path)
                paths_found.add(path)

        for predecessor in G.predecessors(entity):
            edge_data = G[predecessor][entity]
            relation = edge_data.get('relation', '')
            path = f"{predecessor} {relation} {entity}"
            if path not in paths_found:
                context.append(path)
                paths_found.add(path)

        # 2跳：邻居的邻居
        if max_hops >= 2:
            for neighbor in G.neighbors(entity):
                for second_neighbor in G.neighbors(neighbor):
                    if second_neighbor != entity:
                        edge1 = G[entity][neighbor]
                        edge2 = G[neighbor][second_neighbor]
                        rel1 = edge1.get('relation', '')
                        rel2 = edge2.get('relation', '')
                        path = f"{entity} {rel1} {neighbor} {rel2} {second_neighbor}"
                        if path not in paths_found:
                            context.append(f"{entity} --({rel1})--> {neighbor} --({rel2})--> {second_neighbor}")
                            paths_found.add(path)

        # 2跳：反向邻居的邻居
        if max_hops >= 2:
            for predecessor in G.predecessors(entity):
                for second_predecessor in G.predecessors(predecessor):
                    if second_predecessor != entity:
                        edge1 = G[second_predecessor][predecessor]
                        edge2 = G[predecessor][entity]
                        rel1 = edge1.get('relation', '')
                        rel2 = edge2.get('relation', '')
                        path = f"{second_predecessor} {rel1} {predecessor} {rel2} {entity}"
                        if path not in paths_found:
                            context.append(f"{second_predecessor} --({rel1})--> {predecessor} --({rel2})--> {entity}")
                            paths_found.add(path)

    return context

def local_search(query: str, G: nx.DiGraph) -> tuple:
    """本地搜索：基于知识图谱回答问题（支持多跳推理和类型查询）"""
    # 使用 LLM 解析问题，识别实体
    prompt = f"""分析以下问题，识别其中的主要实体和概念：

问题：{query}

请先识别问题中明确提到的实体名称（如人名、公司名、地名等）。
然后思考：问题中是否涉及某种类型或类别（如"科技公司"、"CEO"、"人"等）？

请分两部分输出：
1. 明确实体（每行一个）
2. 类型/类别（每行一个，用[类型]标记）

例如：
问题：谁在科技公司工作？
输出：
谷歌公司
苹果公司
[类型]科技公司
"""
    response = llm.complete(prompt)

    # 解析实体和类型
    lines = [line.strip() for line in response.text.strip().split('\n') if line.strip()]
    entities = []
    types = []

    for line in lines:
        if line.startswith('[类型]') or line.startswith('[类别]'):
            types.append(line.replace('[类型]', '').replace('[类别]', '').strip())
        else:
            entities.append(line)

    # 查找实体对应的多跳路径
    context = find_multi_hop_paths(G, entities, max_hops=2)

    # 如果问题涉及类型/类别，查找该类型下的所有实体
    for t in types:
        type_entities, related_info = find_entities_by_type(G, t)
        for te in type_entities:
            # 查找该实体的所有关系
            if te in G.nodes:
                for neighbor in G.neighbors(te):
                    edge_data = G[te][neighbor]
                    relation = edge_data.get('relation', '')
                    path = f"{te} {relation} {neighbor}"
                    if path not in context:
                        context.append(path)

                for predecessor in G.predecessors(te):
                    edge_data = G[predecessor][te]
                    relation = edge_data.get('relation', '')
                    path = f"{predecessor} {relation} {te}"
                    if path not in context:
                        context.append(path)

        # 添加相关的类型信息
        for info in related_info:
            if info not in context:
                context.append(info)

    # 如果仍然没有找到相关信息
    if not context:
        return None, "在知识图谱中未找到相关信息"

    context_str = "\n".join(context)

    # 使用 LLM 基于上下文回答
    prompt = f"""根据以下知识图谱信息回答问题：

知识图谱信息：
{context_str}

问题：{query}

请基于以上知识图谱信息回答问题。如果发现实体之间存在关联路径（如 A -> B -> C），请在回答中描述这个关系路径。
"""
    response = llm.complete(prompt)
    return context_str, response.text.strip()

@app.route('/api/query', methods=['POST'])
def api_query():
    """处理用户查询的 API 端点"""
    try:
        data = request.json
        query = data.get('query', '')
        
        if not query:
            return jsonify({'error': '查询内容不能为空'}), 400
        
        # 构建图
        G = build_graph_from_neo4j()
        
        if G.number_of_nodes() == 0:
            return jsonify({'error': '知识图谱为空，请先导入数据'}), 400
        
        # 本地搜索
        context, answer = local_search(query, G)
        
        return jsonify({
            'query': query,
            'answer': answer,
            'context': context,
            'nodes_count': G.number_of_nodes(),
            'edges_count': G.number_of_edges()
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/import', methods=['POST'])
def api_import():
    """导入文档数据到知识图谱"""
    try:
        data = request.json
        documents = data.get('documents', [])
        
        if not documents:
            return jsonify({'error': '文档列表为空'}), 400
        
        total_triplets = 0
        
        for doc in documents:
            text = doc.get('content', '')
            if not text:
                continue
            
            # 提取三元组
            triplets = extract_triplets(text)
            
            # 存入 Neo4j
            for subject, relation, obj in triplets:
                neo4j_conn.query(
                    "MERGE (a:Entity {name: $subject}) "
                    "MERGE (b:Entity {name: $object}) "
                    "MERGE (a)-[r:`" + relation + "`]->(b)",
                    {"subject": subject, "object": obj}
                )
                total_triplets += 1
        
        # 获取统计信息
        G = build_graph_from_neo4j()
        
        return jsonify({
            'message': f'成功导入 {total_triplets} 个三元组',
            'nodes_count': G.number_of_nodes(),
            'edges_count': G.number_of_edges()
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/import-file', methods=['POST'])
def api_import_file():
    """从文本文件导入文档数据到知识图谱"""
    try:
        data = request.json
        file_path = data.get('file_path', '')
        
        if not file_path:
            return jsonify({'error': '文件路径不能为空'}), 400
        
        # 解析文件路径
        if not os.path.isabs(file_path):
            base_dir = Path(__file__).parent.parent
            full_path = base_dir / file_path
        else:
            full_path = Path(file_path)
        
        if not full_path.exists():
            return jsonify({'error': f'文件不存在: {full_path}'}), 400
        
        # 读取文件内容
        with open(full_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 按行分割，每行作为一个文档
        lines = [line.strip() for line in content.split('\n') if line.strip()]
        
        if not lines:
            return jsonify({'error': '文件中没有内容'}), 400
        
        total_triplets = 0
        
        for line in lines:
            # 提取三元组
            triplets = extract_triplets(line)
            
            # 存入 Neo4j
            for subject, relation, obj in triplets:
                neo4j_conn.query(
                    "MERGE (a:Entity {name: $subject}) "
                    "MERGE (b:Entity {name: $object}) "
                    "MERGE (a)-[r:`" + relation + "`]->(b)",
                    {"subject": subject, "object": obj}
                )
                total_triplets += 1
        
        # 获取统计信息
        G = build_graph_from_neo4j()
        
        return jsonify({
            'message': f'成功从文件导入 {total_triplets} 个三元组',
            'nodes_count': G.number_of_nodes(),
            'edges_count': G.number_of_edges(),
            'documents_count': len(lines)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/status', methods=['GET'])
def api_status():
    """检查服务状态"""
    try:
        G = build_graph_from_neo4j()
        return jsonify({
            'status': 'running',
            'nodes_count': G.number_of_nodes(),
            'edges_count': G.number_of_edges()
        })
    except Exception as e:
        return jsonify({'status': 'error', 'message': str(e)}), 500

@app.route('/api/graph', methods=['GET'])
def api_graph():
    """获取图谱结构数据"""
    try:
        G = build_graph_from_neo4j()
        
        nodes = [{'id': node, 'name': node} for node in G.nodes]
        edges = []
        for u, v, data in G.edges(data=True):
            edges.append({
                'source': u,
                'target': v,
                'relation': data.get('relation', '')
            })
        
        return jsonify({
            'nodes': nodes,
            'edges': edges
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ==================== 普通 RAG 功能 ====================

def build_rag_index(documents: list):
    """构建普通RAG索引"""
    global rag_index, rag_documents
    
    # 创建 LlamaIndex 文档
    llama_docs = []
    for doc in documents:
        llama_docs.append(LlamaDocument(text=doc))
    
    rag_documents = documents
    rag_index = VectorStoreIndex.from_documents(llama_docs)
    return rag_index

def rag_query(query: str) -> str:
    """普通RAG查询"""
    if rag_index is None:
        return "RAG索引未构建，请先导入数据"
    
    query_engine = rag_index.as_query_engine()
    response = query_engine.query(query)
    return str(response)

def get_rag_context(query: str) -> str:
    """获取RAG检索到的上下文"""
    if rag_index is None:
        return ""
    
    retriever = rag_index.as_retriever(similarity_top_k=3)
    nodes = retriever.retrieve(query)
    context = []
    for node in nodes:
        context.append(f"[相关性: {node.score:.2f}] {node.text}")
    return "\n".join(context)

@app.route('/api/rag-query', methods=['POST'])
def api_rag_query():
    """普通RAG查询 API"""
    try:
        data = request.json
        query = data.get('query', '')
        
        if not query:
            return jsonify({'error': '查询内容不能为空'}), 400
        
        if rag_index is None:
            return jsonify({'error': 'RAG索引未构建，请先导入数据'}), 400
        
        # 获取检索到的上下文
        context = get_rag_context(query)
        # 获取答案
        answer = rag_query(query)
        
        return jsonify({
            'query': query,
            'answer': answer,
            'context': context,
            'documents_count': len(rag_documents)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/rag-import', methods=['POST'])
def api_rag_import():
    """导入文档到RAG索引"""
    try:
        data = request.json
        documents = data.get('documents', [])
        
        if not documents:
            return jsonify({'error': '文档列表为空'}), 400
        
        # 构建RAG索引
        build_rag_index(documents)
        
        return jsonify({
            'message': f'成功导入 {len(documents)} 个文档到RAG索引',
            'documents_count': len(rag_documents)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/rag-import-file', methods=['POST'])
def api_rag_import_file():
    """从文件导入文档到RAG索引"""
    try:
        data = request.json
        file_path = data.get('file_path', '')
        
        if not file_path:
            return jsonify({'error': '文件路径不能为空'}), 400
        
        # 解析文件路径
        if not os.path.isabs(file_path):
            base_dir = Path(__file__).parent.parent
            full_path = base_dir / file_path
        else:
            full_path = Path(file_path)
        
        if not full_path.exists():
            return jsonify({'error': f'文件不存在: {full_path}'}), 400
        
        # 读取文件内容
        with open(full_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # 按行分割，每行作为一个文档
        lines = [line.strip() for line in content.split('\n') if line.strip()]
        
        if not lines:
            return jsonify({'error': '文件中没有内容'}), 400
        
        # 构建RAG索引
        build_rag_index(lines)
        
        return jsonify({
            'message': f'成功从文件导入 {len(lines)} 个文档到RAG索引',
            'documents_count': len(rag_documents)
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/compare-query', methods=['POST'])
def api_compare_query():
    """对比查询：同时使用GraphRAG和普通RAG回答"""
    try:
        data = request.json
        query = data.get('query', '')
        
        if not query:
            return jsonify({'error': '查询内容不能为空'}), 400
        
        # GraphRAG 回答
        G = build_graph_from_neo4j()
        graph_result = {}
        if G.number_of_nodes() > 0:
            try:
                graph_context, graph_answer = local_search(query, G)
                graph_result = {
                    'answer': graph_answer,
                    'context': graph_context,
                    'nodes_count': G.number_of_nodes(),
                    'edges_count': G.number_of_edges()
                }
            except Exception as e:
                graph_result = {'error': str(e)}
        else:
            graph_result = {'error': '知识图谱为空'}
        
        # 普通 RAG 回答
        rag_result = {}
        if rag_index is not None:
            try:
                rag_context = get_rag_context(query)
                rag_answer = rag_query(query)
                rag_result = {
                    'answer': rag_answer,
                    'context': rag_context,
                    'documents_count': len(rag_documents)
                }
            except Exception as e:
                rag_result = {'error': str(e)}
        else:
            rag_result = {'error': 'RAG索引未构建'}
        
        return jsonify({
            'query': query,
            'graphrag': graph_result,
            'rag': rag_result
        })
    
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print("🚀 启动 GraphRAG API 服务...")
    print("📡 服务地址: http://localhost:5001")
    print("📊 API 端点:")
    print("  - POST /api/query          - GraphRAG 查询问答")
    print("  - POST /api/rag-query      - 普通RAG 查询问答")
    print("  - POST /api/compare-query  - 对比查询（同时返回两者结果）")
    print("  - POST /api/import         - 导入文档到知识图谱")
    print("  - POST /api/rag-import     - 导入文档到RAG索引")
    print("  - POST /api/import-file    - 从文件导入文档到知识图谱")
    print("  - POST /api/rag-import-file- 从文件导入文档到RAG索引")
    print("  - GET  /api/status         - 服务状态")
    print("  - GET  /api/graph          - 图谱结构")
    app.run(host='0.0.0.0', port=5001, debug=True)
