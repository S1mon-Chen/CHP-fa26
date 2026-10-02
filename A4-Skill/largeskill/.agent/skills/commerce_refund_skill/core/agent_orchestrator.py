# -*- coding: utf-8 -*-
import asyncio
import json
import urllib.request
import urllib.error
from commerce_refund_skill.tools.sub_tools import check_gps_match_api, get_sku_stock_api, create_refund_proposal_api
from commerce_refund_skill.prompts import agent_templates

class FinancialAgentOrchestrator:
    def __init__(self, user_context: dict):
        self.user_context = user_context
        self.state_store = {
            "current_step": "INIT",
            "clarified_address": None,
            "idempotency_key": "refund-123-20240522"
        }

    def _call_ollama(self, prompt: str, json_format: bool = False) -> str:
        url = "http://localhost:11434/api/generate"
        data = {
            "model": "qwen2.5:latest",
            "prompt": prompt,
            "stream": False,
            "options": {
                "temperature": 0.0
            }
        }
        if json_format:
            data["format"] = "json"
            
        headers = {"Content-Type": "application/json"}
        req = urllib.request.Request(
            url, 
            data=json.dumps(data).encode("utf-8"), 
            headers=headers, 
            method="POST"
        )
        try:
            proxy_handler = urllib.request.ProxyHandler({})
            opener = urllib.request.build_opener(proxy_handler)
            with opener.open(req, timeout=15) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                return res_data.get("response", "")
        except urllib.error.URLError as e:
            print(f"[Ollama 错误] 无法连接到 Ollama: {e}")
            raise e

    async def execute_workflow_stream(self, sku_id: str, refund_amount: float):
        """
        通过 async generator 实时弹射 Agent 内部的动态编排事件与思维链
        """
        # ---- 阶段 1: RAG 路由与初始化 ----
        mock_user_input = None
        yield {
            "stage": "RAG_ROUTING",
            "thought": "正在检索向量知识库... 成功加载优化路由与 refund_policy_Premium.md 约束守卫。",
            "action": "LOAD_POLICY"
        }
        await asyncio.sleep(0.6)  # 模拟大模型 CoT 推理停顿

        # ---- 阶段 2: 动态上下文感知与澄清模式 ----
        yield {
            "stage": "SECURITY_CHECK",
            "thought": "开始空间安全审计。正在调用 check_gps_match_api 校验实时定位...",
            "action": "INVOKE_TOOL",
            "tool": "check_gps_match_api"
        }
        await asyncio.sleep(0.4)
        
        gps_res = check_gps_match_api(
            current_gps=self.user_context["current_gps"], 
            home_address=self.user_context["home_address"]
        )
        
        if gps_res["status"] == "MISMATCH":
            # 遭遇异常，动态调整编排计划：插播澄清模式
            yield {
                "stage": "CLARIFICATION_MODE_START",
                "thought": "⚠️ 严重警告：用户实时 GPS 与系统注册地不符！触发动态澄清分支，挂起后续流水。正在使用 Ollama 生成安全追问语...",
                "action": "LLM_GENERATION"
            }
            
            prompt = agent_templates.GPS_CLARIFICATION_TEMPLATE.format(
                current_gps=self.user_context["current_gps"],
                home_address=self.user_context["home_address"]
            )
            try:
                clarification_question = await asyncio.to_thread(self._call_ollama, prompt)
                clarification_question = clarification_question.strip()
            except Exception as ollama_err:
                print(f"[Ollama 澄清生成失败] 降级使用默认回复: {ollama_err}")
                clarification_question = "好的，用户123。我收到您的请求。在处理之前，为了账户安全，您的定位系统显示您在上海，请问您现在的具体地址是哪里？"

            yield {
                "stage": "CLARIFICATION_MODE",
                "thought": "安全追问语生成成功，等待用户澄清中。",
                "action": "PROMPT_USER",
                "prompt_text": clarification_question
            }
            # 模拟等待用户输入
            await asyncio.sleep(1.5)
            
            mock_user_input = "我确实在上海出差，目前在陆家嘴软件园进行现场交付。"
            self.state_store["clarified_address"] = mock_user_input
            
            yield {
                "stage": "CONTEXT_ALIGNED",
                "thought": f"槽位填充完毕。用户输入: '{mock_user_input}'。地址上下文对齐成功，恢复主编排链路。",
                "action": "RESUME"
            }
            await asyncio.sleep(0.6)

        # ---- 阶段 3: 工具调用与硬约束校验 ----
        yield {
            "stage": "STOCK_AND_GUARDRAIL_CHECK",
            "thought": "并行发起物理库存校验与政策 Hard-Guardrails 审计...",
            "action": "INVOKE_TOOL",
            "tool": "get_sku_stock_api"
        }
        await asyncio.sleep(0.5)
        
        stock_res = get_sku_stock_api(product_id=sku_id)
        if not stock_res["in_stock"]:
            yield {"stage": "TERMINATED", "thought": "阻断：商品无货，流程终止。", "action": "EXIT"}
            return
        
        max_limit = 500.0  # 来自 RAG 缓存政策
        if refund_amount > max_limit:
            yield {"stage": "GUARDRAIL_INTERCEPT", "thought": f"安全硬拦截：金额 {refund_amount} 超限。", "action": "EXIT"}
            return
            
        yield {
            "stage": "GUARDRAIL_PASSED",
            "thought": f"风控审计通过。退款金额 {refund_amount} 元未触发自动拦截阀值 ({max_limit}元)。",
            "action": "CONTINUE"
        }
        await asyncio.sleep(0.4)

        # ---- 阶段 4: 金融级状态管理与故障隔离重试 ----
        idem_key = self.state_store["idempotency_key"]
        yield {
            "stage": "TRANSACTION_LOCK",
            "thought": f"进入事务锁定模式。生成全局唯一 RequestID: {idem_key}，准备调用退款单生成工具。",
            "action": "INVOKE_TOOL",
            "tool": "create_refund_proposal_api"
        }
        await asyncio.sleep(0.5)
        
        try:
            # 首次调用（内部代码会故意注入一次 TimeoutError）
            proposal_res = create_refund_proposal_api(
                user_id=self.user_context["user_id"], 
                amount=refund_amount, 
                idempotency_key=idem_key
            )
        except Exception as e:
            # 捕获子工具级故障，不崩溃，利用 LLM 重新规划能力优雅解析
            yield {
                "stage": "FAULT_DETECTION",
                "thought": f"💥 检测到子工具异常: {str(e)}。正在使用 Ollama 优雅解析底层系统错误与重新规划决策...",
                "action": "ERROR_PARSING"
            }
            
            prompt = agent_templates.ERROR_PARSING_TEMPLATE.format(raw_error=str(e))
            try:
                ollama_res = await asyncio.to_thread(self._call_ollama, prompt, json_format=True)
                decision = json.loads(ollama_res)
                thought_reason = decision.get("reason", f"故障隔离重试方案 Fail-Fast (模型生成原因: {decision})")
                is_retryable = decision.get("is_retryable", True)
                next_action = decision.get("next_action", "RETRY")
            except Exception as parse_err:
                thought_reason = f"决策解析异常，降级至默认重试逻辑: {parse_err}"
                is_retryable = True
                next_action = "RETRY"
                
            await asyncio.sleep(1.0)
            
            if is_retryable and next_action == "RETRY":
                yield {
                    "stage": "IDEMPOTENT_RETRY",
                    "thought": f"状态机根据 LLM 决策方案 (原因: {thought_reason})，判定当前流水号 {idem_key} 仍未完成。保持原 RequestID 不变，启动第二轮并发异步重试...",
                    "action": "RETRY_TOOL",
                    "tool": "create_refund_proposal_api"
                }
                await asyncio.sleep(0.8)
                
                # 第二次重试（内部代码会判定为成功）
                proposal_res = create_refund_proposal_api(
                    user_id=self.user_context["user_id"], 
                    amount=refund_amount, 
                    idempotency_key=idem_key
                )
            else:
                yield {
                    "stage": "TERMINATED",
                    "thought": f"Ollama 决策为不可重试错误，流程终止。原因: {thought_reason}",
                    "action": "EXIT"
                }
                return

        # ---- 阶段 5: 人工介入护栏 (HITL) 与最终追踪图数据返回 ----
        if proposal_res["status"] == "PENDING_APPROVAL":
            yield {
                "stage": "HITL_TRIGGERED",
                "thought": "事务成功落地。触发强制性 HITL 护栏，生成待审批订单，并输出全量编排时序追踪图（DAG Trace）。",
                "action": "FINALIZE"
            }
            await asyncio.sleep(0.4)
            
            # 最终的一致性决策输出
            final_trace = {
                "api_status": "SUCCESS",
                "execution_trace": {
                    "trace_id": "trace_id_20240522_123",
                    "has_fallback_occurred": True,
                    "dag_nodes": [
                        { "step": 1, "action": "RAG_POLICY_LOAD", "status": "COMPLETED" },
                        { "step": 2, "action": "CALL:check_gps_match_api", "status": "INTERRUPTED_BY_DRIFT" },
                        { "step": 3, "action": "SLOT_CLARIFY_DIALOG", "status": "COMPLETED", "input": mock_user_input },
                        { "step": 4, "action": "CALL:create_refund_proposal_api", "status": "FAILED_TIMEOUT" },
                        { "step": 5, "action": "RETRY:create_refund_proposal_api", "status": "SUCCESS_WITH_SAME_ID" }
                    ]
                },
                "final_response": f"UserID: 123 (Premium会员)。SKU库存充足。政策校验通过（{refund_amount}元 < 500元上限）。退款 Proposal 已创建，已路由至运营人员审批队列。"
            }
            yield {"stage": "COMPLETED", "payload": final_trace}

# ---- 异步演练入口 ----
async def main():
    mock_user_profile = {
        "user_id": "123",
        "user_level": "Premium",
        "current_gps": "中国, 上海市, 浦东新区",
        "home_address": "中国, 江苏省, 盐城市"
    }
    
    orchestrator = FinancialAgentOrchestrator(user_context=mock_user_profile)
    
    print("====== 🎬 开始动态编排实时事件流演示 ======\n")
    async for event in orchestrator.execute_workflow_stream(sku_id="SKU-A", refund_amount=100.0):
        stage = event.get("stage")
        thought = event.get("thought", "")
        action = event.get("action", "")
        
        if stage == "COMPLETED":
            print("\n====== 🏁 最终一致性决策结果与链路追踪树 ======")
            print(json.dumps(event["payload"], ensure_ascii=False, indent=2))
        elif stage == "CLARIFICATION_MODE":
            print(f"[{stage}] 🤖 思维: {thought}")
            print(f"         💬 此时 Agent 弹窗/语音优雅追问: \"{event['prompt_text']}\"")
        else:
            print(f"[{stage}] 🤖 思维: {thought} (执行动作: {action})")

if __name__ == "__main__":
    # 使用 asyncio 运行异步事件流
    asyncio.run(main())