"""CLI 与网页共用的装配层；只有 Harness 定义决策逻辑。"""
from pathlib import Path
import fcntl
from .agent import Harness
from .cli import Settings
from .corpus import load_corpus
from .tools import ToolService
from .ollama import OllamaClient
from .scripted import ScriptedModel


def service_factory(root,settings,mode,scenario,model):
    def load():
        corpus=Path(settings.corpus)
        if not corpus.is_absolute():corpus=root/corpus
        if mode=='scripted' and scenario=='injection':corpus=Path(__file__).resolve().parent.parent/'tests/fixtures/injection'
        snap=load_corpus(corpus,settings.max_file_bytes,settings.max_total_bytes)
        allowed=settings.allowed_document_ids
        if mode=='scripted' and scenario=='unauthorized':
            hidden=next((d.document_id for d in snap.documents.values() if 'Flink' in d.title),next(iter(snap.documents)))
            model.unauthorized_document_id=hidden
            allowed=sorted(set(snap.documents if allowed is None else allowed)-{hidden})
        return ToolService(snap,allowed,settings.output_limit,settings.stopwords,settings.synonyms)
    return load


def execute(question,config,settings_path,runs,mode,scenario=None,run_id=None,settings=None,execution_fd=None):
    control=Path(settings_path).absolute().parent/'.web'
    control.mkdir(exist_ok=True)
    slot=None
    if execution_fd is None:
        slot=(control/'execution.lock').open('a')
        try:fcntl.flock(slot,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:
            slot.close()
            raise ValueError('已有活动运行（CLI/网页共用执行槽）') from None
    try:
        return _execute(question,config,settings_path,runs,mode,scenario,run_id,settings)
    finally:
        if slot:slot.close()


def _execute(question,config,settings_path,runs,mode,scenario,run_id,settings):
    model=OllamaClient(config) if mode=='live' else ScriptedModel(scenario)
    def factory():
        actual=settings or Settings.model_validate_json(Path(settings_path).read_text())
        return service_factory(Path(settings_path).absolute().parent,actual,mode,scenario,model)()
    return Harness(config,factory,model,runs,mode,scenario,run_id=run_id).run(question)
