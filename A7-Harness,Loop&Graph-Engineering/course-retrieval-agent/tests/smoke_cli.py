"""对真实教学资料集做 CLI 冒烟，输出可复核记录；不修改 corpus。"""
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parent.parent


def call(*args):
    result=subprocess.run([sys.executable,'-m','app.cli','--json',*args],cwd=ROOT,capture_output=True,text=True,check=True)
    return json.loads(result.stdout)


def main():
    before={p.name:p.read_bytes() for p in (ROOT/'corpus').iterdir()}
    report={'check':call('check'),'snapshot':call('snapshot'),'documents':call('list'),'queries':{}}
    assert report['check']['visible_documents']==8
    queries=['HDFS 副本','Spark Stage Task','Flink 水位线','迟到消息','RowKey 多版本','zzzznonexistent987']
    for query in queries:
        result=call('search',query)
        assert bool(result['hits']) == (query != 'zzzznonexistent987')
        assert result==call('search',query)
        report['queries'][query]=result
    expected=['HDFS 文件块与副本','Spark 的 Job、Stage、Task','Flink 事件时间与水印','Flink 迟到数据与处理选择','HBase 的 RowKey、列族和多版本']
    for query,title in zip(queries,expected):
        assert report['queries'][query]['hits'][0]['title']==title
    hit=next(h for h in report['queries']['HDFS 副本']['hits'] if h['section_title']=='基础概念')
    report['read_example']=call('read',hit['document_id'],hit['chunk_id'])
    assert report['read_example']['chunks'][0]['content_hash']==hit['content_hash']
    assert len(call('schema')['tools'])==3
    assert before=={p.name:p.read_bytes() for p in (ROOT/'corpus').iterdir()}
    (ROOT/'docs/smoke-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS: 8 documents; 6 real queries; stable ordering; read/hash; schema; corpus unchanged')
    print('read example:',hit['document_id'],hit['chunk_id'])


if __name__=='__main__':main()
