import time
import uuid
from typing import Dict, Any

def check_gps_match_api(current_gps: str, home_address: str) -> dict:
    """
    空间一致性校验 API
    """
    # 模拟上海与盐城的空间跨度校验
    if "上海" in current_gps and "盐城" in home_address:
        return {"status": "MISMATCH", "code": 401, "message": "GPS location drifts across provinces."}
    return {"status": "MATCH", "code": 200}

def get_sku_stock_api(product_id: str) -> dict:
    """
    标准库存查询 API
    """
    if product_id == "SKU-A":
        return {"product_id": "SKU-A", "in_stock": True, "quantity": 42}
    return {"product_id": product_id, "in_stock": False, "quantity": 0}

# 用全局变量模拟底层服务的高延迟/首次超时状态
_RETRIED_REQUESTS = set()

def create_refund_proposal_api(user_id: str, amount: float, idempotency_key: str) -> dict:
    """
    金融级退款单生成 API (带故障注入)
    """
    if not idempotency_key:
        raise ValueError("Missing critical parameter: idempotency_key")
        
    # 模拟 Panel 2：子工具级故障检测。首次运行超时，第二次重试成功。
    if idempotency_key not in _RETRIED_REQUESTS:
        _RETRIED_REQUESTS.add(idempotency_key)
        time.sleep(2.0)  # 模拟网络挂起
        raise TimeoutError("GATEWAY_TIMEOUT: Financial ledger container replica failed to respond within 1500ms.")
    
    # 二次重试成功后的事务落地逻辑
    return {
        "proposal_id": f"PROP-{uuid.uuid4().hex[:8].upper()}",
        "user_id": user_id,
        "amount": amount,
        "idempotency_key": idempotency_key,
        "status": "PENDING_APPROVAL",
        "msg": "Transaction log created successfully. Dispatched to HITL Queue."
    }