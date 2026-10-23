"""JSONL 轨迹与独立大文本产物；最终 manifest 提供完整性检查，不提供签名认证。"""
import hashlib
import json
import os
from pathlib import Path
import re
from datetime import datetime,timezone

LOG_VERSION=1
PROTOCOL='ollama-native-tools-v1'
TERMINAL={'COMPLETED','BUDGET_EXHAUSTED','STALLED','MODEL_ERROR','TOOL_ERROR','ANSWER_VALIDATION_FAILED','DEADLINE_EXCEEDED'}


def encoded(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode('utf-8')


def sha(raw):return hashlib.sha256(raw).hexdigest()


def write_json(path,value):
    temporary=path.with_suffix('.tmp')
    with temporary.open('wb') as stream:
        stream.write(encoded(value));stream.flush();os.fsync(stream.fileno())
    temporary.replace(path)


class Recorder:
    def __init__(self,base,run_id):
        self.run_id=run_id
        self.folder=Path(base)/run_id
        self.folder.mkdir(parents=True,exist_ok=False)
        (self.folder/'artifacts').mkdir()
        self.seq=0
        for name in ('events.jsonl','model_interactions.jsonl'):(self.folder/name).touch()

    def append(self,name,value):
        with (self.folder/name).open('ab') as stream:
            stream.write(encoded(value)+b'\n');stream.flush();os.fsync(stream.fileno())

    def blob(self,value):
        data=encoded(value);digest=sha(data);name='artifacts/'+digest+'.json'
        if not (self.folder/name).exists():write_json(self.folder/name,value)
        return {'path':name,'sha256':digest}

    def event(self,phase,kind,round_no,attempts,elapsed,**extra):
        self.seq+=1
        event={'seq':self.seq,'run_id':self.run_id,'log_version':LOG_VERSION,
               'time':datetime.now(timezone.utc).isoformat(),'phase':phase,'type':kind,
               'decision_round':round_no,'tool_attempts':attempts,'elapsed_seconds':round(elapsed,6),
               'status':'RUNNING','call_id':None,'error_code':None,'artifact':None,**extra}
        self.append('events.jsonl',event)

    def finish(self,summary,evidence,answer):
        write_json(self.folder/'evidence.json',evidence)
        if answer is not None:write_json(self.folder/'answer.json',answer)
        write_json(self.folder/'summary.json',summary)
        files={str(p.relative_to(self.folder)):sha(p.read_bytes()) for p in self.folder.rglob('*') if p.is_file()}
        write_json(self.folder/'manifest.json',{'log_version':LOG_VERSION,'protocol':PROTOCOL,'complete':True,'files':files})


class ReplayError(ValueError):pass


def replay(base,run_id):
    """只验证/读取已落盘记录。不加载 corpus，不实例化模型或工具。"""
    try:
        if not re.fullmatch(r'[a-f0-9]{32}',run_id):raise ValueError()
        folder=Path(base)/run_id
        if folder.is_symlink():raise ValueError()
        manifest=json.loads((folder/'manifest.json').read_text())
        if manifest.get('log_version')!=LOG_VERSION or manifest.get('protocol')!=PROTOCOL or manifest.get('complete') is not True:raise ValueError()
        required={'events.jsonl','model_interactions.jsonl','config.json','evidence.json','summary.json'}
        if not required<=manifest['files'].keys():raise ValueError()
        for name,digest in manifest['files'].items():
            path=folder/name
            if Path(name).is_absolute() or not path.resolve().is_relative_to(folder.resolve()) or any(p.is_symlink() for p in [path,*path.parents] if p!=folder.parent):
                raise ValueError()
            if sha(path.read_bytes())!=digest:raise ValueError()
        events=[json.loads(line) for line in (folder/'events.jsonl').read_text().splitlines()]
        interactions=[json.loads(line) for line in (folder/'model_interactions.jsonl').read_text().splitlines()]
        summary=json.loads((folder/'summary.json').read_text())
        if summary['execution_status'] not in TERMINAL or summary['run_id']!=run_id:raise ValueError()
        if not events or events[-1]['type']!='finished' or events[-1]['status']!=summary['execution_status']:raise ValueError()
        for i,event in enumerate(events,1):
            if event['seq']!=i or event['run_id']!=run_id:raise ValueError()
        def reference(ref):
            if ref['path'] not in manifest['files'] or ref['sha256']!=manifest['files'][ref['path']]:raise ValueError()
        for event in events:
            if event.get('artifact'):reference(event['artifact'])
        for i,interaction in enumerate(interactions,1):
            if interaction['request_no']!=i:raise ValueError()
            reference(interaction['request'])
            if interaction.get('response'):reference(interaction['response'])
            elif not interaction.get('error_code'):raise ValueError()
        if len(interactions)!=summary['model_requests']:raise ValueError()
        evidence=json.loads((folder/'evidence.json').read_text())
        answer=json.loads((folder/'answer.json').read_text()) if 'answer.json' in manifest['files'] else None
        if summary['answer_status'] is not None and answer is None:raise ValueError()
        return {'ok':True,'observation':'历史观察：未调用模型或资料工具','summary':summary,
                'events':events,'evidence':evidence,'answer':answer}
    except (OSError,ValueError,KeyError,TypeError):
        raise ReplayError('记录缺失、损坏、未完成或协议版本不支持，拒绝完整回放') from None
