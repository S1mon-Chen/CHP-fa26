#!/usr/bin/env python3
"""Real concurrent HTTP load generator; Python standard library only."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from collections import Counter
from datetime import datetime, timezone
import json
from pathlib import Path
import threading
import time
import urllib.request
import urllib.error

BASE = 'http://127.0.0.1:18087'

def request(path, method='GET', body=None, base=BASE):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=data, method=method,
                                 headers={'Content-Type': 'application/json'})
    # Independent opener per request; do not use environment proxies for localhost.
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    started = time.perf_counter()
    try:
        response = client.open(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read().decode()
        try: parsed = json.loads(raw)
        except ValueError: parsed = raw
        return {'status': response.code, 'headers': dict(response.headers), 'body': parsed,
                'elapsedMs': round((time.perf_counter() - started) * 1000, 3)}

def stats(base=BASE):
    result = request('/demo/stats', base=base)
    assert result['status'] == 200, result
    return result['body']

def burst(count=50, ids=(1,), base=BASE):
    before = stats(base)
    barrier = threading.Barrier(count)
    started = time.perf_counter()
    def worker(index):
        barrier.wait(timeout=15)
        offset = time.perf_counter() - started
        result = request(f'/api/books/{ids[index % len(ids)]}', base=base)
        return {'index': index + 1, 'sentAtMs': round(offset * 1000, 3), **result}
    with ThreadPoolExecutor(max_workers=count) as pool:
        responses = list(pool.map(worker, range(count)))
    duration = time.perf_counter() - started
    after = stats(base)
    counts = dict(Counter(str(r['status']) for r in responses))
    return {'time': datetime.now(timezone.utc).isoformat(), 'url': base + '/api/books/{id}',
            'clients': count, 'ids': list(ids), 'durationMs': round(duration * 1000, 3),
            'statusCounts': counts, 'before': before, 'after': after,
            'metricDelta': {k: after['metrics'][k] - v for k, v in before['metrics'].items()},
            'responses': responses}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default=BASE)
    parser.add_argument('--clients', type=int, default=50, choices=range(1,201), metavar='1..200')
    parser.add_argument('--output', default='evidence/loadtest.json')
    args = parser.parse_args()
    request('/api/books/1', base=args.base)
    time.sleep(1.3)
    result = burst(args.clients, base=args.base)
    dest = Path(args.output)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k:v for k,v in result.items() if k not in ('responses','before','after')}, ensure_ascii=False, indent=2))
    if args.clients > 5 and not result['statusCounts'].get('429'):
        raise SystemExit('没有观察到 HTTP 429，请确认限流配置。')

if __name__ == '__main__': main()
