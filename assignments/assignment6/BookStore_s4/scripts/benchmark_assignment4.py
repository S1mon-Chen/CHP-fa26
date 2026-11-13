#!/usr/bin/env python3
"""Compare the same HTTP endpoint with virtual threads and a 10-thread Tomcat pool."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import secrets
import socket
import statistics
import subprocess
import time

from verify_assignment4 import call


def benchmark(base, count, concurrency, rounds):
    login = call(base, '/api/auth/login', data={'username': 'student', 'password': 'bookstore-demo-2026'})
    assert login['status'] == 200
    token = login['body']['accessToken']

    def one(_):
        start = time.perf_counter()
        response = call(base, '/api/books/1', token=token)
        headers = {k.lower(): v for k, v in response['headers'].items()}
        return {'ms': (time.perf_counter() - start) * 1000, 'status': response['status'],
                'virtual': headers.get('x-request-virtual') == 'true'}

    samples = []
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(one, range(min(20, count))))  # Warm the HTTP/JWT path in both configurations.
        for _ in range(rounds):
            start = time.perf_counter()
            requests = list(pool.map(one, range(count)))
            elapsed = time.perf_counter() - start
            durations = sorted(row['ms'] for row in requests)
            samples.append({'elapsedSeconds': round(elapsed, 4),
                            'requestsPerSecond': round(count / elapsed, 2),
                            'p50Ms': round(statistics.median(durations), 2),
                            'p95Ms': round(durations[max(0, int(len(durations) * .95) - 1)], 2),
                            'http200': sum(row['status'] == 200 for row in requests),
                            'virtualResponses': sum(row['virtual'] for row in requests)})
            assert samples[-1]['http200'] == count, samples[-1]
    return samples


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--jar', type=Path, default=Path('backend/target/backend-0.0.1-SNAPSHOT.jar'))
    parser.add_argument('--requests', type=int, default=100)
    parser.add_argument('--concurrency', type=int, default=100)
    parser.add_argument('--rounds', type=int, default=3)
    parser.add_argument('--output', type=Path, default=Path('evidence-jwt/benchmark.json'))
    args = parser.parse_args()
    if min(args.requests, args.concurrency, args.rounds) < 1:
        parser.error('requests, concurrency and rounds must be positive')
    jar = args.jar.resolve(strict=True)
    env = dict(os.environ, JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode(),
               BOOKSTORE_USERNAME='student', BOOKSTORE_PASSWORD='bookstore-demo-2026')
    result = {'timestamp': datetime.now(timezone.utc).isoformat(), 'platform': platform.platform(),
              'java': subprocess.check_output([args.java, '-version'], stderr=subprocess.STDOUT, text=True).strip(),
              'requestsPerRound': args.requests, 'clientConcurrency': args.concurrency,
              'rounds': args.rounds, 'simulatedIoMs': 200, 'platformPoolMaxThreads': 10,
              'note': 'Synthetic localhost comparison; client uses Python threads and fresh HTTP connections. Not a production capacity claim.',
              'results': {}}
    for mode, port in [('platform', 18085), ('virtual', 18084)]:
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', port))  # Fail if someone else's server occupies the port.
        log_path = Path('backend/target') / f'benchmark-{mode}.log'
        log_path.parent.mkdir(parents=True, exist_ok=True)
        with log_path.open('w') as log:
            process = subprocess.Popen([args.java, '-jar', str(jar), '--spring.profiles.active=demo,assignment4',
                f'--server.port={port}', f'--spring.threads.virtual.enabled={str(mode == "virtual").lower()}',
                '--server.tomcat.threads.max=10', '--server.tomcat.threads.min-spare=10',
                '--app.books.delay-ms=200'], env=env, stdout=log, stderr=subprocess.STDOUT)
            try:
                base = f'http://127.0.0.1:{port}'
                for _ in range(100):
                    if process.poll() is not None:
                        raise RuntimeError(f'{mode} server exited; inspect {log_path}')
                    try:
                        if call(base, '/api/books/1')['status'] == 401:
                            break
                    except OSError:
                        pass
                    time.sleep(.2)
                else:
                    raise RuntimeError(f'{mode} server did not start; inspect {log_path}')
                rows = benchmark(base, args.requests, args.concurrency, args.rounds)
                expected = args.requests if mode == 'virtual' else 0
                assert all(row['virtualResponses'] == expected for row in rows), rows
                result['results'][mode] = rows
                print(mode, json.dumps(rows, ensure_ascii=False), flush=True)
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    print(f'Saved {args.output}')


if __name__ == '__main__':
    main()
