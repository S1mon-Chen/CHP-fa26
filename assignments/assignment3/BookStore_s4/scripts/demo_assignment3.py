#!/usr/bin/env python3
"""Call the real application and save reproducible ReAct/RAG evidence (no API secrets)."""
import argparse
import datetime
import http.cookiejar
import json
import urllib.request
from pathlib import Path

DEFAULT_QUERY = "我想买一本关于微服务的书，另外如果我买了不喜欢，拆了塑封还能退吗？"


class Client:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = self.get("/api/auth/csrf")

    def get(self, path):
        with self.http.open(self.base + path, timeout=60) as response:
            return json.load(response)

    def post(self, path, body):
        request = urllib.request.Request(self.base + path, data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json", self.csrf["headerName"]: self.csrf["token"]})
        with self.http.open(request, timeout=600) as response:
            return json.load(response)


def save(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("query", nargs="?", default=DEFAULT_QUERY)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--output-dir", type=Path, default=Path("evidence-rag"))
    parser.add_argument("--name", default="live-complex")
    parser.add_argument("--evaluate", action="store_true", help="Run local dense retrieval checks without LLM calls")
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    client = Client(args.base_url)
    manifest = client.get("/api/ai/shopping/index")
    save(args.output_dir / "index-manifest.json", manifest)
    if args.evaluate:
        cases = [
            ("买来不喜欢，已经拆开塑封，还能无理由退吗？", "P02-1"),
            ("拆开后发现缺页，签收十天，能退换吗？", "P03-1"),
            ("退回去的书验收以后多久退款？", "P04-1"),
            ("会员注册收费吗，积分什么时候发放，多久过期？", "P05-1"),
            ("银卡会员达到多少消费可以升级，图书打几折？", "P06-1"),
            ("电子书下载后能无理由退款吗？", "P07-1"),
            ("签收三天，塑封没拆，书完好，不想要了能退吗？", "P01-1"),
        ]
        results = []
        for query, expected in cases:
            response = client.post("/api/ai/shopping/policy-search", {"query": query, "topK": 3})
            ids = [m["chunk_id"] for m in response["matches"]]
            results.append({"query": query, "expected": expected, "retrieved": ids,
                            "hit_at_1": bool(ids) and ids[0] == expected, "hit_at_3": expected in ids,
                            "response": response})
        report = {"embedding_model": manifest["model"], "cases": results,
                  "hit_at_1": sum(r["hit_at_1"] for r in results) / len(results),
                  "hit_at_3": sum(r["hit_at_3"] for r in results) / len(results)}
        save(args.output_dir / "retrieval-evaluation.json", report)
        print(f"Dense retrieval: Hit@1={report['hit_at_1']:.3f}; Hit@3={report['hit_at_3']:.3f}; n={len(results)}")
        if not all(r["hit_at_3"] for r in results):
            raise SystemExit("Some expected clauses were not retrieved; inspect retrieval-evaluation.json")
        return
    response = client.post("/api/ai/shopping/chat", {"query": args.query, "history": []})
    evidence = {"recorded_at": datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
                "kind": "live_application_and_model_response", "query": args.query, "response": response}
    save(args.output_dir / f"{args.name}.json", evidence)
    lines = [f"User: {args.query}", f"Embedding: {manifest['model']}, dimensions={manifest['dimensions']}, chunks={manifest['chunk_count']}"]
    for step in response["steps"]:
        lines.extend([f"\nRound {step['round']}", "Thought: " + step["thought"],
                      "Action: " + json.dumps(step["action"], ensure_ascii=False),
                      "Observation: " + json.dumps(step["observation"], ensure_ascii=False)])
    lines.extend(["\nFinal Answer: " + response["answer"], f"Model requests: {response['modelRequests']}"])
    text = "\n".join(lines) + "\n"
    (args.output_dir / f"{args.name}.txt").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
