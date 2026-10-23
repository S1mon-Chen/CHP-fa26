import json
from pathlib import Path
import pytest
from pydantic import ValidationError
from app.corpus import load_corpus, CorpusError, split_text
from app.tools import ToolService, export_schemas


def write(root, name='a.md', body='HDFS 副本。Spark Stage Task。水印 watermark。迟到数据 late data。RowKey 多版本。', title='课程'):
    path=root/name
    path.write_text(f'# {title}\n主题：基础\n编写日期：2026-10-12\n适用版本：基础概念\n\n## 内容\n\n{body}\n',encoding='utf-8')
    return path


@pytest.fixture
def corpus(tmp_path):
    root=tmp_path/'corpus';root.mkdir()
    write(root)
    write(root,'b.txt','另一份只有 HBase 列族的资料。',title='HBase')
    return root


def test_exact_reconstruction_lines_and_long_paragraph(corpus):
    path=write(corpus,body=('中文长段落。'*400)+'\n\n末尾内容\n第二行')
    snap=load_corpus(corpus)
    doc=next(d for d in snap.documents.values() if d.title=='课程')
    chunks=[c for c in snap.chunks.values() if c.document_id==doc.document_id]
    text=path.read_text()
    assert ''.join(c.text for c in chunks)==text
    for c in chunks:
        assert c.text==text[c.char_start:c.char_end]
        assert c.line_start==text[:c.char_start].count('\n')+1
        assert c.line_end==text[:c.char_end-1].count('\n')+1
        assert c.text in ''.join(text.splitlines(keepends=True)[c.line_start-1:c.line_end])
        assert len(c.text)<=600
    assert len(chunks)>5


def test_snapshot_stability_immutability_reload(corpus):
    first=load_corpus(corpus)
    assert first.corpus_snapshot_id==load_corpus(corpus).corpus_snapshot_id
    previous=tuple(first.chunks.values())
    with pytest.raises(TypeError):first.documents['x']=None
    with pytest.raises(ValidationError):previous[0].text='changed'
    (corpus/'a.md').write_text((corpus/'a.md').read_text()+'新内容')
    assert tuple(first.chunks.values())==previous
    second=load_corpus(corpus)
    assert second.corpus_snapshot_id!=first.corpus_snapshot_id
    assert set(second.documents)==set(first.documents)


def test_order_independent_snapshot(corpus,tmp_path):
    other=tmp_path/'other';other.mkdir()
    for p in sorted(corpus.iterdir(),reverse=True):(other/p.name).write_bytes(p.read_bytes())
    assert load_corpus(other).corpus_snapshot_id==load_corpus(corpus).corpus_snapshot_id


@pytest.mark.parametrize('kind',['file','directory','inside'])
def test_reject_symlinks(corpus,tmp_path,kind):
    target=tmp_path/'secret.txt';target.write_text('SECRET')
    (corpus/'link.md').symlink_to(corpus/'a.md' if kind=='inside' else (tmp_path if kind=='directory' else target))
    with pytest.raises(CorpusError,match='符号链接'):load_corpus(corpus)


@pytest.mark.parametrize('case',['encoding','empty','file_size','total_size','duplicate','metadata'])
def test_load_errors(corpus,case):
    opts={}
    if case=='encoding':(corpus/'a.md').write_bytes(b'\xff')
    if case=='empty':(corpus/'a.md').write_text('  \n')
    if case=='file_size':opts={'max_file_bytes':10}
    if case=='total_size':opts={'max_total_bytes':10}
    if case=='duplicate':write(corpus,'a.txt')
    if case=='metadata':(corpus/'a.md').write_text('没有元数据')
    with pytest.raises(CorpusError) as error:load_corpus(corpus,**opts)
    assert str(corpus) not in str(error.value)


def test_no_scan_outside_and_unsupported_ignored(corpus,tmp_path):
    (tmp_path/'outside.md').write_text('不应扫描')
    (corpus/'ignored.pdf').write_bytes(b'\xff')
    assert len(load_corpus(corpus).documents)==2


@pytest.mark.parametrize('query,term',[('HDFS 副本','hdfs'),('Spark Stage Task','task'),('Flink 水位线','watermark'),
                                    ('迟到消息','late data'),('行键','rowkey'),('RowKey 多版本','rowkey')])
def test_search_terms(corpus,query,term):
    service=ToolService(load_corpus(corpus))
    result=service.invoke_tool('search_documents',{'query':query})
    assert result['ok'] and result['hits']
    assert any(term in h['matched_terms'] for h in result['hits'])
    for hit in result['hits']:
        assert hit['snippet'] in service.snapshot.chunks[hit['chunk_id']].text
        assert hit['score']==sum(hit['term_scores'].values())
    assert result==service.invoke_tool('search_documents',{'query':query})


@pytest.mark.parametrize('query',['zzzznonexistent987','   ','的'])
def test_no_random_hits(corpus,query):
    assert ToolService(load_corpus(corpus)).invoke_tool('search_documents',{'query':query})['hits']==[]


def test_top_k_filter_and_access(corpus):
    snap=load_corpus(corpus);ids=list(snap.documents)
    service=ToolService(snap)
    assert len(service.invoke_tool('search_documents',{'query':'课程','top_k':1})['hits'])==1
    restricted=ToolService(snap,[ids[0]])
    assert {d['document_id'] for d in restricted.invoke_tool('list_documents',{})['documents']}=={ids[0]}
    assert all(h['document_id']==ids[0] for h in restricted.invoke_tool('search_documents',{'query':'HDFS HBase'})['hits'])
    assert service.invoke_tool('search_documents',{'query':'HDFS','document_ids':[ids[1]]})['hits']==[]
    denied_chunk=next(c.chunk_id for c in snap.chunks.values() if c.document_id==ids[1])
    for name,args in [('search_documents',{'query':'HDFS','document_ids':ids}),
                      ('read_document',{'document_id':ids[1],'chunk_ids':[denied_chunk]})]:
        assert restricted.invoke_tool(name,args)['error']['code']=='DOCUMENT_NOT_ALLOWED'
    assert ToolService(snap,[]).invoke_tool('list_documents',{})['documents']==[]


@pytest.mark.parametrize('name,args,code',[
 ('shell',{},'UNKNOWN_TOOL'),('list_documents',{'limti':2},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'HDFS','top_k':0},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'HDFS','top_k':6},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'HDFS','top_k':'3'},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'HDFS','top_k':True},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'长'*201},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':''},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'a','document_ids':['doc_'+'0'*16]*21},'INVALID_ARGUMENTS'),
 ('search_documents',{'query':'a','document_ids':['doc_'+'0'*16]},'DOCUMENT_NOT_ALLOWED'),
 ('list_documents',None,'INVALID_ARGUMENTS')])
def test_invalid(corpus,name,args,code):
    assert ToolService(load_corpus(corpus)).invoke_tool(name,args)['error']['code']==code


def test_read_mismatch_and_unknown(corpus):
    snap=load_corpus(corpus);svc=ToolService(snap);docs=list(snap.documents)
    chunk=next(c for c in snap.chunks.values() if c.document_id==docs[0])
    assert svc.invoke_tool('read_document',{'document_id':docs[0],'chunk_ids':[chunk.chunk_id]})['chunks'][0]['text']==chunk.text
    assert svc.invoke_tool('read_document',{'document_id':docs[1],'chunk_ids':[chunk.chunk_id]})['error']['code']=='CHUNK_DOCUMENT_MISMATCH'
    assert svc.invoke_tool('read_document',{'document_id':docs[0],'chunk_ids':['chunk_'+'0'*24]})['error']['code']=='CHUNK_NOT_FOUND'
    assert svc.invoke_tool('read_document',{'document_id':docs[0],'chunk_ids':[chunk.chunk_id]*3})['error']['code']=='INVALID_ARGUMENTS'


@pytest.mark.parametrize('doc_id',['../../secret','/etc/passwd','https://example.com','cat /etc/passwd'])
def test_path_never_opens_files(corpus,monkeypatch,doc_id):
    svc=ToolService(load_corpus(corpus))
    def forbidden(*args,**kwargs):raise AssertionError('工具不能再打开资料文件')
    monkeypatch.setattr(Path,'open',forbidden)
    assert svc.invoke_tool('read_document',{'document_id':doc_id,'chunk_ids':['chunk_'+'0'*24]})['error']['code']=='INVALID_ARGUMENTS'
    assert svc.invoke_tool('search_documents',{'query':'HDFS'})['ok']
    c=next(iter(svc.snapshot.chunks.values()))
    assert svc.invoke_tool('read_document',{'document_id':c.document_id,'chunk_ids':[c.chunk_id]})['ok']


def test_output_limit_and_schema(corpus):
    svc=ToolService(load_corpus(corpus),output_limit=256)
    c=next(iter(svc.snapshot.chunks.values()))
    result=svc.invoke_tool('read_document',{'document_id':c.document_id,'chunk_ids':[c.chunk_id]})
    raw=json.dumps(result,ensure_ascii=False)
    assert len(raw.encode())<=256 and json.loads(raw)['error']['code']=='OUTPUT_LIMIT_EXCEEDED'
    schemas=export_schemas()
    assert len(schemas)==3
    assert all(s['parameters']['additionalProperties'] is False for s in schemas)
    assert schemas[1]['parameters']['properties']['top_k']['maximum']==5


def test_cli_load_error_redacted(tmp_path,capsys):
    from app.cli import main
    config=tmp_path/'config.json';config.write_text(json.dumps({'corpus':'secret/missing'}))
    assert main(['--json','--config',str(config),'check'])==1
    result=json.loads(capsys.readouterr().out)
    assert result['error']['code']=='CORPUS_LOAD_ERROR'
    assert str(tmp_path) not in json.dumps(result)


def test_txt_and_crlf_lossless(corpus):
    raw='TXT 标题\r\n主题：测试\r\n编写日期：2026-10-12\r\n适用版本：基础\r\n\r\n'+('无标点长行'*240)+'\r\n\r\n末尾'
    (corpus/'c.txt').write_bytes(raw.encode())
    snap=load_corpus(corpus)
    doc=next(d for d in snap.documents.values() if d.title=='TXT 标题')
    chunks=[c for c in snap.chunks.values() if c.document_id==doc.document_id]
    assert ''.join(c.text for c in chunks)==raw
    assert all(len(c.text)<=600 for c in chunks)


@pytest.mark.parametrize('name,args',[('list_documents',{}),('search_documents',{'query':'HDFS'})])
def test_other_output_limits(corpus,name,args):
    result=ToolService(load_corpus(corpus),output_limit=256).invoke_tool(name,args)
    assert result['error']['code']=='OUTPUT_LIMIT_EXCEEDED'


def test_empty_directory(tmp_path):
    with pytest.raises(CorpusError,match='没有可加载'):load_corpus(tmp_path)


def test_custom_synonyms_stopwords(corpus):
    svc=ToolService(load_corpus(corpus),stops=('hdfs',),synonyms={'特定别名':('watermark',)})
    assert svc.invoke_tool('search_documents',{'query':'HDFS'})['hits']==[]
    assert any('watermark' in h['matched_terms'] for h in svc.invoke_tool('search_documents',{'query':'特定别名'})['hits'])
