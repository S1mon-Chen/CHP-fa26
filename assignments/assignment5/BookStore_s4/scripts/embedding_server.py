#!/usr/bin/env python3
"""Local, CPU-only Chinese embeddings. Model weights are cached, never submitted."""
import argparse
import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

MODEL = "BAAI/bge-small-zh-v1.5"


def main():
    from fastembed import TextEmbedding
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8092)
    parser.add_argument("--cache-dir", default=str(Path(__file__).resolve().parents[1] / ".model-cache"))
    args = parser.parse_args()
    model = TextEmbedding(model_name=MODEL, cache_dir=args.cache_dir, threads=2)

    class Handler(BaseHTTPRequestHandler):
        def respond(self, status, body):
            data = json.dumps(body, ensure_ascii=False, allow_nan=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self.respond(200 if self.path == "/health" else 404,
                         {"model": MODEL, "dimensions": 512, "ready": True})

        def do_POST(self):
            if self.path != "/embed":
                return self.respond(404, {"error": "NOT_FOUND"})
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 0 < size <= 131072:
                    raise ValueError("Invalid body size")
                body = json.loads(self.rfile.read(size))
                texts, mode = body["input"], body["mode"]
                if not isinstance(texts, list) or not 1 <= len(texts) <= 128:
                    raise ValueError("input must contain 1..128 texts")
                if any(not isinstance(t, str) or not t.strip() or len(t) > 1500 for t in texts):
                    raise ValueError("texts must contain 1..1500 characters")
                if mode not in {"query", "passage"}:
                    raise ValueError("mode must be query or passage")
            except (ValueError, TypeError, KeyError):
                return self.respond(400, {"error": "INVALID_ARGUMENTS"})
            try:
                # query_embed applies this model's retrieval query instruction.
                vectors = list(model.query_embed(texts) if mode == "query" else model.passage_embed(texts))
                self.respond(200, {"model": MODEL, "dimensions": 512,
                                   "embeddings": [v.tolist() for v in vectors]})
            except Exception:
                self.respond(503, {"error": "EMBEDDING_FAILED"})

    server = HTTPServer(("127.0.0.1", args.port), Handler)
    server.timeout = 30
    print(f"Ready: {MODEL}, 512 dimensions, http://127.0.0.1:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
