import os
# 解决 macOS 下 Python 容易走代理导致 localhost 报错 502 Bad Gateway 的问题
# 注意：这必须在导入 ollama 库之前完成，以便 httpx 客户端在初始化时能够正确读取到配置。
os.environ["no_proxy"] = "localhost,127.0.0.1"

import json
import uuid
import ollama
import antigravity as ag

def run_local_agent_with_ollama():
    """
    使用本地运行的 Ollama 模型（支持 Tool Calling 的模型，如 qwen2.5）
    实现自动路由和调用本地技能（Skills）的示例。
    
    在运行前，请确保：
    1. Ollama 服务已启动（默认端口 11434）
    2. 已下载支持工具调用的模型，如：ollama pull qwen2.5:latest
    """
    
    # 1. 自动检测可用模型，优先使用 qwen2.5:latest 或 qwen3.5:9b
    model_name = os.environ.get("OLLAMA_MODEL")
    if not model_name:
        try:
            models_response = ollama.list()
            downloaded_names = [m.model for m in models_response.models]
            
            # 优先选择
            for preferred in ["qwen2.5:latest", "qwen3.5:9b", "qwen2.5:7b", "llama3.2:latest"]:
                if preferred in downloaded_names:
                    model_name = preferred
                    break
            
            if not model_name and downloaded_names:
                # 降级：使用第一个已下载的模型
                model_name = downloaded_names[0]
        except Exception as e:
            print(f"无法连接到 Ollama 服务，请确认 Ollama 已启动！错误: {e}")
            return
            
    if not model_name:
        print("未检测到本地有任何 Ollama 模型，请运行 `ollama pull qwen2.5` 下载模型。")
        return

    print(f"-> 正在使用本地 Ollama 模型: {model_name}\n")

    # 2. 初始化 AntiGravity 实例
    local_agent = ag.init()

    # 3. 定义声明给 Ollama 的工具集规范 (根据 .agent/skills 目录下的 info.md 规范设计)
    tools = [
        {
            'type': 'function',
            'function': {
                'name': 'weather_forecast',
                'description': '查询特定城市未来 1 到 7 天的天气状况或天气预报。',
                'parameters': {
                    'type': 'object',
                    'properties': {
                        'location': {
                            'type': 'string',
                            'description': '标准城市名称，例如 "上海", "北京"。'
                        },
                        'days': {
                            'type': 'integer',
                            'description': '预报天数，范围 1 到 7。'
                        },
                        'data_type': {
                            'type': 'string',
                            'enum': ['realtime', 'forecast'],
                            'description': '天气数据类型：realtime (查询实时天气), forecast (查询天气预报)。'
                        }
                    },
                    'required': ['location', 'data_type'],
                },
            },
        },
        {
            'type': 'function',
            'function': {
                'name': 'safe_payment',
                'description': '执行扣款交易支付，具备安全防重复支付的幂等保护。',
                'parameters': {
                    'type': 'object',
                    'properties': {
                        'request_id': {
                            'type': 'string',
                            'description': '全局唯一的流水号 UUID。为了幂等安全，如果用户未提供，必须自动生成一个。'
                        },
                        'user_id': {
                            'type': 'string',
                            'description': '被扣款用户的唯一标识 ID，例如 "user123"。'
                        },
                        'amount': {
                            'type': 'number',
                            'description': '扣款的金额（数值格式，如 10.5）。'
                        }
                    },
                    'required': ['request_id', 'user_id', 'amount'],
                },
            },
        }
    ]

    # 4. 定义发问测试集
    prompts = [
        "上海明天天气怎么样？",
        "帮我给用户 user_123 扣款 15 元钱（流水号：pay-998877）",
        "你今天心情怎么样？" # 这个应该不会触发工具，而是模型直接回答
    ]

    for prompt in prompts:
        print(f"【用户问】: {prompt}")
        messages = [{'role': 'user', 'content': prompt}]
        
        try:
            # 5. 调用 Ollama 获取初步响应并检测是否需要调用工具
            response = ollama.chat(
                model=model_name,
                messages=messages,
                tools=tools
            )
            
            assistant_message = response['message']
            
            # 检测是否有工具调用请求
            if assistant_message.get('tool_calls'):
                messages.append(assistant_message)
                
                # 逐个执行工具调用
                for tool_call in assistant_message['tool_calls']:
                    func_name = tool_call['function']['name']
                    args = tool_call['function']['arguments']
                    
                    print(f"  [Thinking] 匹配到本地技能: {func_name} | 参数: {args}")
                    
                    # 动态生成 request_id 的容错处理（防止模型忘记生成）
                    if func_name == 'safe_payment' and not args.get('request_id'):
                        args['request_id'] = str(uuid.uuid4())
                        print(f"  [System] 自动补全缺失的流水号: {args['request_id']}")
                    
                    # 6. 执行本地 AntiGravity 技能
                    try:
                        result_json_str = local_agent.call_skill(func_name, args)
                        print(f"  [Execute] 技能返回结果: {result_json_str}")
                    except Exception as err:
                        result_json_str = json.dumps({"status": "Failed", "error": str(err)})
                        print(f"  [Execute] 技能执行失败: {err}")
                    
                    # 将工具的执行结果反馈给上下文
                    messages.append({
                        'role': 'tool',
                        'name': func_name,
                        'content': result_json_str
                    })
                
                # 7. 再次询问 Ollama 让其结合工具执行结果生成最终的自然语言回复
                final_response = ollama.chat(
                    model=model_name,
                    messages=messages
                )
                print(f"【Agent答】: {final_response['message']['content']}\n" + "="*50)
                
            else:
                # 直答模式（未触发任何工具）
                print(f"【Agent答】: {assistant_message['content']}\n" + "="*50)
                
        except Exception as e:
            print(f"【运行出错】: {e}\n" + "="*50)

if __name__ == "__main__":
    run_local_agent_with_ollama()
