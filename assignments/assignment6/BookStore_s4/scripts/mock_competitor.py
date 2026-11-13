#!/usr/bin/env python3
"""Teaching-only competitor API. No real store or real prices are queried."""
import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PRICES = {"12345": 45.50, "9787111421900": 72.00, "9780134685991": 89.00}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8091)
    parser.add_argument("--timeout-once", action="store_true")
    args = parser.parse_args()
    seen = set()
    lock = threading.Lock()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            url = urlparse(self.path)
            if url.path == "/health":
                return self.respond(200, {"status": "UP", "simulated": True})
            if url.path != "/prices":
                return self.respond(404, {"error": "NOT_FOUND"})
            isbn = parse_qs(url.query).get("isbn", [""])[0]
            with lock:
                delayed = args.timeout_once and isbn not in seen
                seen.add(isbn)
            if delayed:
                time.sleep(3)  # Longer than the Java client's default 1500 ms timeout.
            if isbn not in PRICES:
                return self.respond(404, {"error": "PRICE_NOT_FOUND"})
            self.respond(200, {"isbn": isbn, "competitor": "模拟书店 A", "price": PRICES[isbn],
                               "currency": "CNY", "simulated": True})

        def respond(self, status, data):
            payload = json.dumps(data, ensure_ascii=False).encode()
            try:
                self.send_response(status)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass  # Expected when demonstrating client timeouts.

    print(f"Simulated competitor API: http://127.0.0.1:{args.port}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
