"""Harness 配置与答案契约，和第一轮工具参数彼此独立。"""
from typing import Literal
from urllib.parse import urlsplit
from pydantic import Field, model_validator
from .schemas import StrictModel


class AgentConfig(StrictModel):
    model: str = Field(default='qwen2.5:7b', min_length=1, max_length=128)
    url: str = 'http://127.0.0.1:11434'
    protocol: Literal['ollama-native-tools-v1'] = 'ollama-native-tools-v1'
    model_timeout: float = Field(default=60.0, ge=0.05, le=300)
    transport_retries: int = Field(default=1, ge=0, le=3)
    deadline_seconds: float = Field(default=180.0, ge=0.05, le=1800)
    max_decisions: int = Field(default=8, ge=1, le=32)
    max_tool_attempts: int = Field(default=10, ge=1, le=100)
    max_calls_per_round: int = Field(default=3, ge=1, le=10)
    stall_threshold: int = Field(default=2, ge=2, le=5)
    answer_repairs: int = Field(default=1, ge=0, le=1)
    evidence_recovery: int = Field(default=1, ge=0, le=1)
    tool_timeout: float = Field(default=5.0, ge=0.01, le=60)
    response_limit: int = Field(default=262144, ge=4096, le=1048576)
    num_predict: int = Field(default=2048, ge=128, le=8192)
    num_ctx: int = Field(default=16384, ge=4096, le=65536)

    @model_validator(mode='after')
    def local_only(self):
        url=urlsplit(self.url)
        if url.scheme!='http' or url.hostname not in ('127.0.0.1','localhost','::1') or url.username or url.password or url.query or url.fragment or url.path not in ('','/'):
            raise ValueError('只允许可信配置中的本机 HTTP Ollama 地址')
        return self


class Claim(StrictModel):
    text: str = Field(min_length=1,max_length=2000)
    evidence_ids: list[str] = Field(min_length=1,max_length=10)


class Answer(StrictModel):
    answer_status: Literal['answered','partial','insufficient_evidence']
    claims: list[Claim] = Field(max_length=20)
    missing_information: list[str] = Field(max_length=20)

    @model_validator(mode='after')
    def coherent(self):
        if any(not c.text.strip() for c in self.claims) or any(not m.strip() for m in self.missing_information):
            raise ValueError('不允许空白结论或缺失信息')
        if self.answer_status=='insufficient_evidence' and (self.claims or not self.missing_information):
            raise ValueError('资料不足时不生成实质结论，并列出缺失信息')
        if self.answer_status in ('answered','partial') and not self.claims:
            raise ValueError('回答需要有证据支持的结论')
        if self.answer_status=='partial' and not self.missing_information:
            raise ValueError('部分回答需要列出缺失信息')
        if self.answer_status=='answered' and self.missing_information:
            raise ValueError('仍有缺失信息应标为 partial')
        return self


class Halt(Exception):
    def __init__(self,status,code):
        self.status,self.code=status,code
        super().__init__(code)
