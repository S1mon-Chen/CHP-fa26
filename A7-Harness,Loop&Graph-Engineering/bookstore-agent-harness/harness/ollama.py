"""仅传输 JSON，不执行模型返回内容。"""
import json
import urllib.request


class Ollama:
    def __init__(self, config):
        self.config = config

    def __call__(self, request):
        c = self.config
        body = json.dumps({"model": c["model"], "stream": False, "format": "json",
                           "messages": [{"role": "user", "content": json.dumps(request, ensure_ascii=False)}],
                           "options": {"temperature": 0}}).encode()
        req = urllib.request.Request(c["url"].rstrip("/") + "/api/chat", body,
                                     {"Content-Type": "application/json"})
        # 同时限制响应大小，避免无限读取。
        with urllib.request.urlopen(req, timeout=c["model_timeout"]) as response:
            data = response.read(262145)
        if len(data) > 262144:
            raise ValueError("model response too large")
        result = json.loads(data)
        return {"raw": result["message"]["content"],
                "tokens": {"input": result.get("prompt_eval_count"),
                           "output": result.get("eval_count")}}
