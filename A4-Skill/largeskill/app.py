# -*- coding: utf-8 -*-
import asyncio
import streamlit as st
import time
import json

# Setup page layout and title
st.set_page_config(
    page_title="电商退款智能流式编排看板",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom premium styling using CSS (glassmorphism, outfit font, custom bubbles, and glowing alerts)
st.markdown("""
<style>
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;800&family=JetBrains+Mono:wght@400;700&display=swap');

/* Main layouts and font */
html, body, [class*="css"], .stApp {
    font-family: 'Outfit', -apple-system, BlinkMacSystemFont, sans-serif;
    background-color: #0b0c10;
    color: #e4e6eb;
}

/* Glassmorphism Panel styles */
.glass-panel {
    background: rgba(255, 255, 255, 0.03);
    border: 1px solid rgba(255, 255, 255, 0.08);
    border-radius: 16px;
    padding: 24px;
    margin-bottom: 20px;
    backdrop-filter: blur(12px);
    box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
}

.title-gradient {
    background: linear-gradient(135deg, #a78bfa, #3b82f6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    font-weight: 800;
    font-size: 2.2rem;
    margin-bottom: 5px;
}

/* Glowing animations for ledger status */
@keyframes pulse-red {
    0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); border-color: rgba(239, 68, 68, 0.5); }
    70% { box-shadow: 0 0 0 15px rgba(239, 68, 68, 0); border-color: rgba(239, 68, 68, 0.8); }
    100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); border-color: rgba(239, 68, 68, 0.5); }
}

@keyframes pulse-green {
    0% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.4); border-color: rgba(34, 197, 94, 0.5); }
    70% { box-shadow: 0 0 0 15px rgba(34, 197, 94, 0); border-color: rgba(34, 197, 94, 0.8); }
    100% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0); border-color: rgba(34, 197, 94, 0.5); }
}

@keyframes pulse-blue {
    0% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4); border-color: rgba(59, 130, 246, 0.5); }
    70% { box-shadow: 0 0 0 15px rgba(59, 130, 246, 0); border-color: rgba(59, 130, 246, 0.8); }
    100% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); border-color: rgba(59, 130, 246, 0.5); }
}

.status-card {
    border-radius: 12px;
    padding: 16px;
    margin-top: 10px;
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid rgba(255, 255, 255, 0.05);
    transition: all 0.3s ease;
}

.status-card.active-blue {
    animation: pulse-blue 2s infinite;
    background: rgba(30, 58, 138, 0.2);
}

.status-card.active-red {
    animation: pulse-red 1.5s infinite;
    background: rgba(127, 29, 29, 0.2);
}

.status-card.active-green {
    animation: pulse-green 2s infinite;
    background: rgba(20, 83, 45, 0.2);
}

/* Chat bubble aesthetics */
.chat-bubble {
    background: rgba(255, 255, 255, 0.04);
    border-radius: 12px;
    padding: 12px 16px;
    margin-bottom: 12px;
    border-left: 4px solid #3b82f6;
    animation: fadeIn 0.5s ease-out;
}

.chat-bubble.mismatch {
    border-left-color: #f59e0b;
    background: rgba(245, 158, 11, 0.04);
}

.chat-bubble.fault {
    border-left-color: #ef4444;
    background: rgba(239, 68, 68, 0.04);
}

.chat-bubble.retry {
    border-left-color: #a78bfa;
    background: rgba(167, 139, 250, 0.04);
}

.chat-bubble.success {
    border-left-color: #10b981;
    background: rgba(16, 185, 129, 0.04);
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(5px); }
    to { opacity: 1; transform: translateY(0); }
}

.mono-text {
    font-family: 'JetBrains Mono', monospace;
    font-size: 0.9rem;
    color: #a78bfa;
}

.step-label {
    font-weight: 600;
    color: #94a3b8;
    margin-bottom: 5px;
    display: inline-block;
    padding: 2px 8px;
    border-radius: 6px;
    background: rgba(255,255,255,0.05);
}
</style>
""", unsafe_allow_html=True)

# App Header
st.markdown('<div class="title-gradient">Commerce Agent Orchestrator</div>', unsafe_allow_html=True)
st.markdown('<p style="color:#94a3b8; font-size:1.1rem; margin-top:-5px; margin-bottom: 25px;">本地 Ollama 驱动的反欺诈、容错重试及动态事件流看板</p>', unsafe_allow_html=True)

# ----------------- SIDEBAR (LEFT) -----------------
st.sidebar.markdown('<div style="font-size:1.3rem; font-weight:800; color:#a78bfa; margin-bottom:15px;">🎮 编排控制器 (Inputs)</div>', unsafe_allow_html=True)

sku_id = st.sidebar.text_input("📦 退款商品 SKU ID", value="SKU-A", help="SKU-A 库存充足，其他商品默认为无货")
refund_amount = st.sidebar.slider("💰 退款金额 (元)", min_value=10.0, max_value=800.0, value=100.0, step=10.0, help="政策规定限额为 500.0 元，超过将触发 Guardrail 拦截")

st.sidebar.markdown("---")
st.sidebar.markdown("**🗺️ 空间安全上下文设定**")
gps_city = st.sidebar.selectbox("当前实时 GPS 城市", ["上海", "盐城", "北京", "深圳"])
registered_city = st.sidebar.selectbox("系统注册居住地城市", ["盐城", "上海", "北京", "深圳"])

st.sidebar.markdown("---")
simulate_timeout = st.sidebar.checkbox("💥 模拟网络超时故障", value=True, help="勾选后，首次发起交易将发生超时异常并挂起，依靠 Ollama 智能解析并触发幂等重试。")

run_button = st.sidebar.button("🚀 启动 Agent 编排 (Run Workflow)", use_container_width=True)

# Create layout: Middle (CoT) and Right (Ledger)
col_cot, col_ledger = st.columns([3, 2])

# Placeholders
with col_cot:
    st.markdown('<div style="font-size:1.2rem; font-weight:600; color:#3b82f6; margin-bottom:12px;">💬 智能体思维流 (Agent Thoughts & CoT)</div>', unsafe_allow_html=True)
    cot_container = st.empty()

with col_ledger:
    st.markdown('<div style="font-size:1.2rem; font-weight:600; color:#10b981; margin-bottom:12px;">📊 金融状态账本 (State Ledger)</div>', unsafe_allow_html=True)
    ledger_container = st.empty()

def clean_html(html_str: str) -> str:
    return "\n".join(line.strip() for line in html_str.split("\n"))

# Helper to render the State Ledger UI
def render_ledger(stage="STANDBY", idempotency_key="N/A", error_reason=None, trace=None, progress=0.0):
    status_class = "active-blue"
    status_text = "🟢 空闲 (Standby)"
    
    if stage in ["RAG_ROUTING", "SECURITY_CHECK", "CLARIFICATION_MODE_START", "CLARIFICATION_MODE", "CONTEXT_ALIGNED"]:
        status_text = "🛡️ 正在进行安全与合规校验..."
        status_class = "active-blue"
    elif stage == "FAULT_DETECTION":
        status_text = "⚠️ 警报: 子系统 API 超时崩溃！"
        status_class = "active-red"
    elif stage == "IDEMPOTENT_RETRY":
        status_text = "🔄 LLM 重规划: 幂等异步重试中..."
        status_class = "active-blue"
    elif stage == "HITL_TRIGGERED":
        status_text = "✍️ 事务已落地，等待运营最终审批 (HITL)..."
        status_class = "active-green"
    elif stage == "COMPLETED":
        status_text = "✅ 编排流水顺利完成"
        status_class = "active-green"
    elif stage in ["GUARDRAIL_INTERCEPT", "TERMINATED"]:
        status_text = "🛑 风控拦截 / 阻断退出"
        status_class = "active-red"

    # Progress bar HTML
    prog_color = "#3b82f6"
    if status_class == "active-red":
        prog_color = "#ef4444"
    elif status_class == "active-green":
        prog_color = "#10b981"

    progress_bar_html = f"""
    <div style="background: rgba(255,255,255,0.05); border-radius: 10px; height: 8px; width: 100%; margin-top: 15px; overflow: hidden;">
        <div style="background: {prog_color}; height: 100%; width: {progress * 100}%; transition: width 0.3s ease;"></div>
    </div>
    """

    ledger_html = f"""
    <div class="glass-panel">
        <div class="step-label">幂等交易流水号 (Idempotency Key)</div>
        <div class="mono-text" style="font-size: 1.2rem; font-weight: bold; margin-bottom: 15px;">{idempotency_key}</div>
        
        <div class="step-label">当前运行步骤</div>
        <div class="mono-text" style="color: #e2e8f0; margin-bottom: 20px; font-size: 1.1rem; font-weight: bold;">{stage}</div>
        
        <div class="step-label">安全账电机状态</div>
        <div class="status-card {status_class}">
            <div style="font-size: 1.1rem; font-weight: 600;">{status_text}</div>
            {f'<div style="font-size: 0.85rem; opacity: 0.8; margin-top: 5px;">原因: {error_reason}</div>' if error_reason else ''}
        </div>
        {progress_bar_html}
    </div>
    """
    
    # Render traces if finished
    if trace:
        ledger_html += f"""
        <div class="glass-panel">
            <div class="step-label">🎬 时序追踪 DAG Trace</div>
            <pre style="background: rgba(15,23,42,0.6); color: #38bdf8; border: 1px solid rgba(255,255,255,0.08); border-radius: 8px; padding: 12px; font-family: 'JetBrains Mono', monospace; font-size: 0.8rem; overflow: auto; max-height: 250px;">{json.dumps(trace, indent=2, ensure_ascii=False)}</pre>
        </div>
        """
        
    ledger_container.markdown(clean_html(ledger_html), unsafe_allow_html=True)

# Render initial state
render_ledger()
cot_container.markdown(clean_html("""
<div class="glass-panel" style="text-align: center; color: #94a3b8; padding: 40px;">
    🏮 点击左侧面板的 <b>“启动 Agent 编排”</b> 开启实时流式决策演示。
</div>
"""), unsafe_allow_html=True)

# ----------------- EXECUTION LOOP -----------------
if run_button:
    # Build current run context
    mock_user_profile = {
        "user_id": "123",
        "user_level": "Premium",
        "current_gps": f"中国, {gps_city}市, 示范区",
        "home_address": f"中国, {registered_city}省市, 注册地"
    }
    
    # Generate idempotency key
    # If simulate_timeout is False, we add it to the sub_tools retired requests list to skip timeout
    from commerce_refund_skill.tools.sub_tools import _RETRIED_REQUESTS
    idem_key = f"refund-123-{int(time.time())}"
    
    if not simulate_timeout:
        _RETRIED_REQUESTS.add(idem_key)
    else:
        _RETRIED_REQUESTS.discard(idem_key)

    # Initialize import of orchestrator inside execution
    from commerce_refund_skill.core.agent_orchestrator import FinancialAgentOrchestrator
    orchestrator = FinancialAgentOrchestrator(user_context=mock_user_profile)
    # Set the generated key in state
    orchestrator.state_store["idempotency_key"] = idem_key

    # Live thoughts aggregator
    thoughts_list = []
    
    # Define workflow run steps and progress weights
    total_stages = 10
    current_stage_idx = 0
    
    async def run_agent():
        global current_stage_idx
        async for event in orchestrator.execute_workflow_stream(sku_id=sku_id, refund_amount=refund_amount):
            stage = event.get("stage")
            thought = event.get("thought", "")
            action = event.get("action", "")
            
            # Map styling depending on the event
            bubble_class = "chat-bubble"
            if stage == "CLARIFICATION_MODE":
                bubble_class = "chat-bubble mismatch"
            elif stage == "FAULT_DETECTION":
                bubble_class = "chat-bubble fault"
            elif stage == "IDEMPOTENT_RETRY":
                bubble_class = "chat-bubble retry"
            elif stage == "COMPLETED":
                bubble_class = "chat-bubble success"

            if stage != "COMPLETED":
                # Regular thoughts stream
                new_thought_html = f"""
                <div class="{bubble_class}">
                    <div style="font-weight: bold; color: #a78bfa; font-size: 0.9rem; margin-bottom: 4px;">[{stage}] Action: {action}</div>
                    <div>{thought}</div>
                    {f'<div style="margin-top: 8px; padding: 10px; background: rgba(0,0,0,0.2); border-radius: 8px; border-left: 3px solid #f59e0b; font-style: italic; font-size: 0.95rem;">💬 追问用户: "{event["prompt_text"]}"</div>' if stage == "CLARIFICATION_MODE" else ''}
                </div>
                """
                thoughts_list.append(new_thought_html)
                
                # Combine all and render in CoT column
                full_cot_html = f'<div class="glass-panel" style="max-height: 600px; overflow-y: auto;">' + "".join(thoughts_list) + '</div>'
                cot_container.markdown(clean_html(full_cot_html), unsafe_allow_html=True)
                
                # Update ledger state
                err_reason = None
                if stage == "FAULT_DETECTION":
                    err_reason = "GATEWAY_TIMEOUT (ledger container replica down)"
                
                current_stage_idx += 1
                progress_val = min(current_stage_idx / total_stages, 0.95)
                render_ledger(stage=stage, idempotency_key=idem_key, error_reason=err_reason, progress=progress_val)
                
            else:
                # Flow completed
                final_payload = event.get("payload", {})
                new_thought_html = f"""
                <div class="chat-bubble success">
                    <div style="font-weight: bold; color: #10b981; font-size: 0.9rem; margin-bottom: 4px;">[COMPLETED] SUCCESS</div>
                    <div>事务最终一致性协商达成，成功输出 Trace。</div>
                </div>
                """
                thoughts_list.append(new_thought_html)
                full_cot_html = f'<div class="glass-panel" style="max-height: 600px; overflow-y: auto;">' + "".join(thoughts_list) + '</div>'
                cot_container.markdown(clean_html(full_cot_html), unsafe_allow_html=True)
                
                render_ledger(stage="COMPLETED", idempotency_key=idem_key, trace=final_payload, progress=1.0)

    # Run the async loop inside Streamlit
    asyncio.run(run_agent())
