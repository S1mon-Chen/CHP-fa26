"""显式白名单分发；不打包教师环境、全部运行记录或课件原 ZIP。"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT))
from app.recording import replay

def main():
    files=[]
    for folder in ('app','corpus','web','docs','tests','scripts'):
        for p in (ROOT/folder).rglob('*'):
            if p.is_file() and not p.is_symlink() and '__pycache__' not in p.parts and p.suffix not in ('.pyc','.pyo'):
                files.append(p)
    files.extend(ROOT/p for p in ('README.md','pyproject.toml','config.json','agent-config.json','.gitignore','start.sh','stop.sh'))
    selected=json.loads((ROOT/'docs/agent-scripted-results.json').read_text())
    for item in selected:
        run_id=item['run_id'];record=replay(ROOT/'runs',run_id)
        assert record['summary']['mode']=='scripted'
        files.extend(p for p in (ROOT/'runs'/run_id).rglob('*') if p.is_file() and not p.is_symlink())
    target=ROOT/'dist';target.mkdir(exist_ok=True)
    output=target/'course-retrieval-agent-student.zip'
    entries={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(set(files))}
    manifest={'files':entries,'history_run_ids':[r['run_id'] for r in selected],
              'excluded':['.venv','.web','.classroom','__pycache__','.pytest_cache','*.egg-info','live runs','other runs','dist','original lecture ZIP'],
              'note':'包含九个经过完整性校验的 scripted 历史记录；预设模型响应，真实资料工具。文档中的旧 live 验收仅是教师测试说明，相关运行原件不随包分发。'}
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
        for rel in entries:z.write(ROOT/rel,'course-retrieval-agent/'+rel)
        z.writestr('course-retrieval-agent/STUDENT_PACKAGE.json',json.dumps(manifest,ensure_ascii=False,indent=2))
    with zipfile.ZipFile(output) as z:assert z.testzip() is None
    (target/'course-retrieval-agent-student.zip.sha256').write_text(hashlib.sha256(output.read_bytes()).hexdigest()+'  '+output.name+'\n')
    print(f'{output}\n{len(entries)} files + manifest; {output.stat().st_size:,} bytes')

if __name__=='__main__':main()
