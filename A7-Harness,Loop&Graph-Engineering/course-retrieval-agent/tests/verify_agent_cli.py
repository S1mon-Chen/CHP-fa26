"""九个场景使用真实 corpus/工具；只有 injection 显式使用虚构 fixture。"""
import json
from pathlib import Path
import subprocess
import sys
from app.scripted import SCENARIOS

ROOT=Path(__file__).resolve().parent.parent


def main():
    records=[]
    for scenario in SCENARIOS:
        p=subprocess.run([sys.executable,'-m','app.cli','agent','--json','ask','课堂演示问题',
                          '--mode','scripted','--scenario',scenario],cwd=ROOT,capture_output=True,text=True)
        result=json.loads(p.stdout)
        summary=result['summary']
        expected={'stalled':'STALLED','budget':'BUDGET_EXHAUSTED'}.get(scenario,'COMPLETED')
        assert summary['execution_status']==expected,(scenario,result)
        assert p.returncode==(0 if expected=='COMPLETED' else 1)
        records.append(summary)
        replayed=subprocess.run([sys.executable,'-m','app.cli','agent','--json','replay',summary['run_id']],cwd=ROOT,capture_output=True,text=True,check=True)
        assert json.loads(replayed.stdout)['summary']==summary
    (ROOT/'docs/agent-scripted-results.json').write_text(json.dumps(records,ensure_ascii=False,indent=2))
    print('PASS: nine scripted scenarios with real tools + nine history-only replays')
    for record in records:print(record['scenario'],record['execution_status'],record['run_id'])


if __name__=='__main__':main()
