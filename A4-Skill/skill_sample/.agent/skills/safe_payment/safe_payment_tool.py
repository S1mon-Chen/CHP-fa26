import json
import random

# 模拟全局状态存储系统（在真实生产环境中，这通常是 Redis 缓存或数据库底表）
# 用于存储已经成功处理过的 request_id 及其执行结果，确保状态安全
mock_idempotency_storage = {}

def check_request_exists(request_id):
    """
    检查请求流水号是否已经存在（幂等性校验核心逻辑）
    """
    if request_id in mock_idempotency_storage:
        return {
            "exists": True,
            "cached_result": mock_idempotency_storage[request_id]
        }
    return {"exists": False}

def execute(parameters):
    """
    AntiGravity 规范：技能执行的核心入口函数
    接收 parameters 字典，返回结构化的 JSON 字符串
    """
    
    # ---------------------------------------------------------
    # 1. 输入端参数格式强强验证 (Input Validation)
    # ---------------------------------------------------------
    try:
        request_id = parameters.get("request_id")
        user_id = parameters.get("user_id")
        amount = parameters.get("amount")

        # 验证必填参数是否存在（硬性契约：严禁自行猜测参数）
        if not request_id or not user_id or amount is None:
            return json.dumps({
                "status": "Failed",
                "error_code": "MISSING_PARAMETERS",
                "message": "缺乏必要参数。'request_id', 'user_id' 和 'amount' 均为必填项，请重新确认或追问用户。"
            }, ensure_ascii=False)
        
        # 验证金额的有效性与边界 (Value Validity)
        try:
            amount = float(amount)
        except (ValueError, TypeError):
            return json.dumps({
                "status": "Failed",
                "error_code": "INVALID_AMOUNT_TYPE",
                "message": "参数 'amount' 格式错误，必须为有效的数字。"
            }, ensure_ascii=False)

        if amount <= 0:
            return json.dumps({
                "status": "Failed",
                "error_code": "INVALID_AMOUNT_VALUE",
                "message": "交易失败：支付金额必须大于 0 元。"
            }, ensure_ascii=False)

        # 硬性约束边界：单次最大金额不得超过 500 元
        if amount > 500:
            return json.dumps({
                "status": "Failed",
                "error_code": "FINANCIAL_LIMIT_EXCEEDED",
                "message": "触发财务限额硬性约束：单次交易金额不得超过 500 元，本次操作已被系统拦截。"
            }, ensure_ascii=False)

    except Exception as e:
        # 异常信息的优雅解析（把底层的报错转为 LLM 可理解的提示，而不是直接崩溃）
        return json.dumps({
            "status": "Failed",
            "error_code": "PARAMETER_PRECHECK_EXCEPTION",
            "message": f"参数预检查时发生未知异常: {str(e)}"
        }, ensure_ascii=False)

    # ---------------------------------------------------------
    # 2. 幂等性检查 (Idempotency Check)
    # ---------------------------------------------------------
    # 防止 LLM 因网络抖动或超时引发推理重试 Loop，从而导致灾难性的多次扣款
    id_check = check_request_exists(request_id)
    if id_check["exists"]:
        cached_res = id_check["cached_result"]
        
        # 发现流水号重复：跳过实际执行，直接返回缓存的成功结果，保证“结果一致性”
        return json.dumps({
            "status": "Idempotent_Skip",
            "error_code": "DUPLICATE_REQUEST_DETECTED",
            "message": f"幂等保护触发：检测到流水号 [{request_id}] 之前已处理成功。系统已自动跳过重复扣款，安全返回原始状态。",
            "original_transaction_id": cached_res.get("transaction_id"),
            "detail": cached_res.get("detail")
        }, ensure_ascii=False)

    # ---------------------------------------------------------
    # 3. 模拟底层核心 API 执行 (Core Execution)
    # ---------------------------------------------------------
    try:
        # 在真实场景中，这里会通过 requests.post("https://api.bank.com/pay", json=...) 调用银行接口
        # 此处使用 Mock 逻辑模拟核心支付通道
        
        # 故意模拟一个随机的通道超时，用以验证错误处理（实际开发中可注释掉）
        # if random.choice([True, False]): raise ConnectionError("Bank Gateway Timeout")

        transaction_id = f"TXN_{random.randint(100000, 999999)}"
        success_detail = f"成功为用户 [{user_id}] 完成金额为 {amount} 元的扣款交易。"

        # ---------------------------------------------------------
        # 4. 状态安全保存 (Save State)
        # ---------------------------------------------------------
        # 一旦执行成功，必须立即将 request_id 及其结果写入状态库，锁定安全边界
        mock_idempotency_storage[request_id] = {
            "transaction_id": transaction_id,
            "detail": success_detail
        }

        return json.dumps({
            "status": "Executed_Success",
            "transaction_id": transaction_id,
            "message": "交易最终执行成功，状态已安全写入底表。",
            "detail": success_detail
        }, ensure_ascii=False)

    except Exception as e:
        # 输出端异常信息的优雅解析
        # 明确告诉 LLM 当前流水未扣款，让它具备重规划能力（使用原 ID 重试是安全的），而不是粗暴报错给用户
        return json.dumps({
            "status": "Failed",
            "error_code": "BANK_API_CHANNEL_TIMEOUT",
            "message": f"底层支付通道超时或网络异常，当前流水 [RequestID: {request_id}] 并未扣款。请携带原 RequestID 重新发起尝试，幂等系统将确保资金安全。",
            "system_error": str(e)
        }, ensure_ascii=False)