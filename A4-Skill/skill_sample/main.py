import antigravity as ag

# 初始化 AntiGravity 实例 (默认会加载相关技能目录)
# 智能体会自动发现 .agent/skills/ 目录下的技能
agent = ag.init() 

# --- 测试天气技能 ---
print("\n--- Testing Weather Skill ---")
weather_params = {
    "location": "上海",
    "days": 1,
    "data_type": "realtime"
}
# 显式调用：call_skill("技能文件夹名称", 参数字典)
weather_result = agent.call_skill("weather_forecast", weather_params)
print(f"Weather Result: {weather_result}")

# --- 测试支付技能 (首次调用，成功) ---
print("\n--- Testing Payment Skill (Initial) ---")
# 生成一个唯一的 RequestID (在真实 Agent 中这通常由 LLM 完成)
import uuid
unique_id = str(uuid.uuid4()) 

payment_params = {
    "request_id": unique_id,
    "user_id": "user123",
    "amount": 10.5
}
payment_result_1 = agent.call_skill("safe_payment", payment_params)
print(f"Payment Result 1: {payment_result_1}")


# --- 测试支付技能 (重复调用相同ID，触发幂等跳过) ---
print("\n--- Testing Payment Skill (Retry with same ID) ---")
payment_result_2 = agent.call_skill("safe_payment", payment_params)
print(f"Payment Result 2: {payment_result_2}")