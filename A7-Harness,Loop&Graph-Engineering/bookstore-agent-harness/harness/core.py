"""显式状态机：模型建议，控制器校验，执行器观察。"""
import ast
import difflib
import hashlib
import json
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path


def digest(value):
    if not isinstance(value, str):
        value = json.dumps(value, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(value.encode()).hexdigest()


def validate(raw, limit):
    try:
        value = json.loads(raw)
        if not isinstance(value, dict) or set(value) != {"path", "code"}:
            raise ValueError("expected exactly path and code")
        if value["path"] != "bookstore.py":
            raise ValueError("only bookstore.py may be replaced")
        code = value["code"]
        if not isinstance(code, str) or not 0 < len(code.encode()) <= limit:
            raise ValueError("invalid code length")
        ast.parse(code)
        return code, None
    except (ValueError, TypeError, SyntaxError, RecursionError) as exc:
        return None, {"type": "VALIDATION_ERROR", "message": str(exc)}


class Tape:
    """请求完全匹配才回放；回放分支不会调用传入的函数。"""
    def __init__(self, manifest, recording=None):
        self.index = 0
        self.replay = recording is not None
        self.data = recording or {"manifest": manifest, "calls": []}
        if self.data["manifest"] != manifest:
            raise ValueError("replay manifest mismatch: task/code/tests/config changed")

    def call(self, kind, request, function):
        if self.replay:
            if self.index >= len(self.data["calls"]):
                raise ValueError("replay exhausted")
            entry = self.data["calls"][self.index]
            if entry["kind"] != kind or entry["request"] != request:
                raise ValueError("replay request mismatch")
        else:
            try:
                response = function(request)
                entry = {"kind": kind, "request": request, "response": response, "error": None}
            except Exception as exc:
                entry = {"kind": kind, "request": request, "response": None,
                         "error": {"type": type(exc).__name__, "message": str(exc)}}
            self.data["calls"].append(entry)
        self.index += 1
        if entry["error"]:
            raise RuntimeError(entry["error"]["message"])
        return entry["response"]


def manifest(root, config):
    files = [root / "TASK.md", root / "starter/bookstore.py",
             *sorted((root / "tests_business").glob("*.py"))]
    return {"version": 1, "files": {str(p.relative_to(root)): digest(p.read_text()) for p in files},
            "config": config}


def run(root, config, model, executor, out, recording=None, observation="docker",
        *, run_id=None, on_event=None, mode=None, scenario=None):
    """保留显式 Python 编排；节点行为与 LangGraph 共用 Runtime。"""
    from .runtime import Runtime
    runtime = Runtime(root, config, model, executor, out, run_id=run_id,
                      recording=recording, observation=observation, on_event=on_event)
    state = runtime.initialize(mode=mode, scenario=scenario, engine="python")
    try:
        runtime.check_recording()
        while state["status"] == "RUNNING":
            state = runtime.step(state["next"], state)
            if state["status"] == "TEST_PASSED":
                state["status"] = "SUCCESS"  # 原引擎保留第一阶段的业务成功语义，不交付。
        if runtime.tape.replay and runtime.tape.index != len(runtime.tape.data["calls"]):
            raise ValueError("replay contains unused calls")
    except Exception as exc:
        state["status"] = "EXECUTION_ERROR"
        runtime.emit(state, state["next"], "controller_error", error=type(exc).__name__, summary=str(exc))
    return runtime.finish(state)
