#!/usr/bin/env python3
"""Real HTTP + Kafka + SSE verification. The server and broker must already be running."""
import argparse, json, time, uuid
from pathlib import Path
from urllib.request import Request, build_opener, ProxyHandler
from urllib.error import HTTPError

parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:8080')
parser.add_argument('--output-dir',default='evidence-kafka')
args=parser.parse_args()
client=build_opener(ProxyHandler({}))
checks=[]

def check(name,condition):
    checks.append({'name':name,'passed':bool(condition)})
    if not condition: raise AssertionError(name)

def request(path,body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=Request(args.base_url+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try: response=client.open(req,timeout=25)
    except HTTPError as error: response=error
    with response:
        raw=response.read().decode()
        return response.status,dict(response.headers),raw

def parsed(path,body=None,token=None):
    code,headers,raw=request(path,body,token)
    return code,headers,json.loads(raw)

_,_,login=parsed('/api/auth/login',{'username':'student','password':'bookstore-demo-2026'})
token=login['accessToken']
check('anonymous order request is 401',parsed('/api/orders',{'items':[{'bookId':1,'quantity':1}]})[0]==401)
check('invalid quantity is 400',parsed('/api/orders',{'items':[{'bookId':1,'quantity':0}]},token)[0]==400)
check('unknown stream is 404',request('/api/orders/'+str(uuid.uuid4())+'/stream',token=token)[0]==404)
_,_,book=parsed('/api/books/1',token=token)
initial_stock=book['stock']
runs=[]
for kind,quantity,expected in [('success',1,'SUCCEEDED'),('failure',initial_stock+100,'FAILED')]:
    payload={'items':[{'bookId':1,'quantity':quantity}]}
    start=time.perf_counter()
    code,headers,accepted=parsed('/api/orders',payload,token)
    elapsed_ms=round((time.perf_counter()-start)*1000,2)
    check(kind+' accepted with queue ID',code==202 and accepted['status']=='QUEUED' and bool(accepted['queueId']))
    queue=accepted['queueId']
    check(kind+' returns before inventory operation',elapsed_ms<2000)
    if kind=='success':
        current=parsed('/api/books/1',token=token)[2]
        check('inventory unchanged immediately after 202',current['stock']==initial_stock)
        username='sse-check-'+uuid.uuid4().hex[:10]
        _,_,registered=parsed('/api/auth/register',{'username':username,'password':'test-password-123','email':username+'@test.invalid'})
        _,_,other_login=parsed('/api/auth/login',{'username':username,'password':'test-password-123'})
        check('other user cannot subscribe',request('/api/orders/'+queue+'/stream',token=other_login['accessToken'])[0]==403)
    code,headers,raw=request('/api/orders/'+queue+'/stream',token=token)
    finished_ms=round((time.perf_counter()-start)*1000,2)
    events=[json.loads(line[5:]) for line in raw.splitlines() if line.startswith('data:')]
    check(kind+' SSE content type',code==200 and 'text/event-stream' in headers.get('Content-Type',''))
    check(kind+' terminal callback',bool(events) and events[-1]['status']==expected)
    if kind=='success':
        order_id=events[-1]['orderId']
        check('successful push refers to committed order',parsed('/api/orders/'+str(order_id),token=token)[0]==200)
    else: check('failure has no order ID',events[-1]['orderId'] is None)
    _,_,replayed=request('/api/orders/'+queue+'/stream',token=token)
    replay_events=[json.loads(line[5:]) for line in replayed.splitlines() if line.startswith('data:')]
    check(kind+' late subscriber receives stored result',replay_events[-1]==events[-1])
    runs.append({'scenario':kind,'request':payload,'httpStatus':202,'response':accepted,'acceptedMs':elapsed_ms,
                 'finishedMs':finished_ms,'sse':raw,'events':events})
final_stock=parsed('/api/books/1',token=token)[2]['stock']
check('only successful order reduced stock',final_stock==initial_stock-1)
result={'baseUrl':args.base_url,'passed':all(c['passed'] for c in checks),'checks':checks,
        'initialStock':initial_stock,'finalStock':final_stock,'runs':runs}
out=Path(args.output_dir);out.mkdir(parents=True,exist_ok=True)
(out/'http-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
for run in runs:
    (out/(run['scenario']+'-stream.txt')).write_text(run['sse'])
print(f"PASS: {len(checks)} HTTP/SSE checks; evidence at {out}")
