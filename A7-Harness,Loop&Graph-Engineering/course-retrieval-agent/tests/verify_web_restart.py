"""真实独立后端异常退出验证；临时资料与本地阻塞 HTTP fixture，不请求真实模型。"""
import json
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer

ROOT=Path(__file__).resolve().parent.parent


def main():
    hold=threading.Event()
    class Stub(BaseHTTPRequestHandler):
        def log_message(self,*_):pass
        def do_GET(self):
            data=json.dumps({'models':[{'name':'fixture-model'}]}).encode()
            self.send_response(200);self.end_headers();self.wfile.write(data)
        def do_POST(self):hold.wait(15)  # 故意让 worker 停在真实 HTTP 请求中。
    server=ThreadingHTTPServer(('127.0.0.1',0),Stub)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
    def api(path,data=None):
        req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=3) as response:return json.load(response)
    with tempfile.TemporaryDirectory(prefix='course-web-restart-') as tmp:
        root=Path(tmp).resolve();shutil.copytree(ROOT/'corpus',root/'corpus');shutil.copy(ROOT/'config.json',root/'config.json')
        (root/'agent-config.json').write_text(json.dumps({'model':'fixture-model','url':f'http://127.0.0.1:{server.server_port}'}))
        def launch():
            proc=subprocess.Popen([sys.executable,'-m','app.web','--port',str(port),'--root',str(root)],cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            for _ in range(100):
                try:api('/api/scenarios');return proc
                except OSError:time.sleep(.05)
            proc.kill();raise AssertionError('backend not ready')
        process=launch()
        try:
            run_id=api('/api/runs',{'question':'中断测试','mode':'live','request_id':'a'*32})['run_id']
            for _ in range(100):
                detail=api('/api/runs/'+run_id)
                if any(e['type']=='model_request' for e in detail['events']):break
                time.sleep(.05)
            else:raise AssertionError('worker never started request')
            process.kill();process.wait()
            # 监督管道 EOF 会终止 worker；重启不恢复它。
            time.sleep(.2)
            process=launch()
            restored=api('/api/runs/'+run_id)
            assert restored['summary']['execution_status']=='INTERRUPTED'
            assert restored['events'] and not restored['complete'] and not restored['active']
            print('PASS: independent backend SIGKILL/restart -> INTERRUPTED; partial events retained; no resume')
        finally:
            process.terminate();process.wait(timeout=10);hold.set();server.shutdown()


if __name__=='__main__':main()
