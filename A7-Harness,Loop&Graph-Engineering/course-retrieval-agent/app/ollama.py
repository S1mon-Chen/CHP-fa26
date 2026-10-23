"""Ollama 非流式原生工具调用；单次传输，有限重试由 Harness 统一计数。"""
import json
import re
import urllib.error
import urllib.request


class TransportError(Exception):
    pass


class ProtocolError(Exception):
    pass


def clean_response(value):
    """只持久化动作和可见回答；不保存 thinking/reasoning 字段。"""
    if not isinstance(value,dict) or not isinstance(value.get('message'),dict):
        raise ProtocolError('INVALID_MODEL_RESPONSE')
    message=value['message']
    content=message.get('content','')
    if not isinstance(content,str):raise ProtocolError('INVALID_MODEL_CONTENT')
    content=re.sub(r'<think>.*?(?:</think>|$)','',content,flags=re.S).strip()
    clean={'role':'assistant','content':content}
    if 'tool_calls' in message:
        calls=message['tool_calls']
        if not isinstance(calls,list) or len(calls)>100:raise ProtocolError('INVALID_TOOL_CALLS')
        normalized=[]
        for call in calls:
            if not isinstance(call,dict) or not isinstance(call.get('function'),dict):
                raise ProtocolError('INVALID_TOOL_CALL')
            f=call['function']
            if not isinstance(f.get('name'),str):raise ProtocolError('INVALID_TOOL_NAME')
            item={'function':{'name':f['name'],'arguments':f.get('arguments')}}
            if 'index' in f:item['function']['index']=f['index']
            if 'id' in call:
                if not isinstance(call['id'],str) or len(call['id'])>200:raise ProtocolError('INVALID_CALL_ID')
                item['id']=call['id']
            normalized.append(item)
        clean['tool_calls']=normalized
    return {'message':clean, 'prompt_eval_count':value.get('prompt_eval_count'),
            'eval_count':value.get('eval_count'), 'done':value.get('done')}


class OllamaClient:
    def __init__(self,config):
        self.config=config
        self.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def http(self,path,payload,timeout):
        request=urllib.request.Request(self.config.url.rstrip('/')+path,
            data=None if payload is None else json.dumps(payload,ensure_ascii=False).encode(),
            headers={'Content-Type':'application/json'})
        try:
            with self.opener.open(request,timeout=timeout) as response:
                raw=response.read(self.config.response_limit+1)
        except urllib.error.HTTPError as exc:
            if exc.code>=500:raise TransportError('OLLAMA_HTTP_SERVER_ERROR') from None
            raise ProtocolError('OLLAMA_HTTP_'+str(exc.code)) from None
        except (OSError,urllib.error.URLError):
            raise TransportError('OLLAMA_CONNECTION_ERROR') from None
        if len(raw)>self.config.response_limit:raise ProtocolError('MODEL_OUTPUT_LIMIT')
        try:return json.loads(raw)
        except (ValueError,UnicodeError):raise ProtocolError('INVALID_MODEL_JSON') from None

    def preflight(self,timeout):
        data=self.http('/api/tags',None,timeout)
        models=data.get('models',[]) if isinstance(data,dict) else []
        if self.config.model not in {m.get('name') for m in models}:
            raise ProtocolError('MODEL_NOT_INSTALLED')
        return {'connected':True,'model_installed':True,'model':self.config.model}

    def request(self,payload,timeout):
        return self.http('/api/chat',payload,timeout)
