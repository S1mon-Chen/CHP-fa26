#!/usr/bin/env python3
"""Send a real request through Spring Security and save the answer/tool trace."""
import argparse
import json
import urllib.error
import urllib.request
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("query", nargs="?", default="帮我查一下 ISBN 为 12345 的书还有多少本，顺便看看别家卖多少钱。")
parser.add_argument("--base-url", default="http://127.0.0.1:8080")
parser.add_argument("--output", type=Path)
args = parser.parse_args()
client = urllib.request.build_opener(urllib.request.ProxyHandler({}))
try:
    request = urllib.request.Request(args.base_url + "/api/ai/tool-chat",
        data=json.dumps({"query": args.query, "history": []}).encode(),
        headers={"Content-Type": "application/json"})
    with client.open(request, timeout=480) as response:
        result = json.load(response)
    evidence = {"query": args.query, "response": result}
    text = json.dumps(evidence, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text + "\n", encoding="utf-8")
    print(text)
except urllib.error.HTTPError as error:
    raise SystemExit(f"HTTP {error.code}: {error.read().decode()}")
except urllib.error.URLError as error:
    raise SystemExit(f"Request failed: {error.reason}")
