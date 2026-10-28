#!/usr/bin/env python3
"""Exercise the running API with real HTTP, saving redacted request/response evidence."""
import argparse
import json
import os
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def call(base, path, *, token=None, data=None, cookie=None):
    headers = {}
    if token is not None:
        headers['Authorization'] = 'Bearer ' + token
    if cookie:
        headers['Cookie'] = cookie
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base + path, headers=headers,
        data=None if data is None else json.dumps(data).encode())
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return {'method': req.get_method(), 'path': path, 'status': response.status,
                'headers': dict(response.headers.items()),
                'body': json.loads(response.read().decode())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8084')
    parser.add_argument('--output-dir', type=Path, default=Path('evidence'))
    args = parser.parse_args()
    username = os.environ.get('BOOKSTORE_USERNAME', 'student')
    password = os.environ.get('BOOKSTORE_PASSWORD', 'bookstore-demo-2026')
    cases = []

    def check(name, response, status):
        assert response['status'] == status, (name, response)
        assert not any(key.lower() == 'set-cookie' for key in response['headers']), name
        cases.append({'case': name, 'expected': status, 'response': response, 'passed': True})

    check('No token', call(args.base_url, '/api/books/1'), 401)
    login = call(args.base_url, '/api/auth/login', data={'username': username, 'password': password})
    token = login['body'].get('accessToken')
    assert token, 'Login did not return a token'
    login['body']['accessToken'] = '[REDACTED]'
    check('Login issues JWT without session cookie', login, 200)
    valid = call(args.base_url, '/api/books/1', token=token)
    assert {k.lower(): v for k, v in valid['headers'].items()}.get('x-request-virtual') == 'true'
    assert valid['body']['id'] == 1
    check('Valid JWT on virtual request thread', valid, 200)
    check('Malformed token', call(args.base_url, '/api/books/1', token='not-a-jwt'), 401)
    position = token.rfind('.') + 1
    modified = token[:position] + ('B' if token[position] == 'A' else 'A') + token[position + 1:]
    check('Tampered signature', call(args.base_url, '/api/books/1', token=modified), 401)
    check('Wrong password', call(args.base_url, '/api/auth/login',
          data={'username': username, 'password': 'deliberately-wrong-password'}), 401)
    check('No implicit login session', call(args.base_url, '/api/books/1'), 401)
    check('Session cookie cannot authenticate', call(args.base_url, '/api/books/1', cookie='JSESSIONID=pretend-session'), 401)
    check('Missing book', call(args.base_url, '/api/books/999', token=token), 404)
    check('Invalid book ID', call(args.base_url, '/api/books/0', token=token), 400)
    check('Book collection', call(args.base_url, '/api/books', token=token), 200)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    result = {'timestamp': datetime.now(timezone.utc).isoformat(), 'baseUrl': args.base_url,
              'passed': len(cases), 'cases': cases}
    (args.output_dir / 'http-verification.json').write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    lines = [f"Time: {result['timestamp']}", f"Base URL: {args.base_url}", 'Tokens and passwords are omitted.', '']
    for case in cases:
        response = case['response']
        lines += [f"PASS {case['case']}", f"{response['method']} {response['path']}", f"HTTP {response['status']}"]
        lines += [f'{k}: {v}' for k, v in response['headers'].items()]
        lines += [json.dumps(response['body'], ensure_ascii=False, indent=2), '']
    lines += [f'PASS: {len(cases)}/{len(cases)}; no response sets a session cookie.']
    (args.output_dir / 'http-verification.txt').write_text('\n'.join(lines) + '\n')
    print('\n'.join(lines))


if __name__ == '__main__':
    main()
