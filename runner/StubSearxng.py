#!/usr/bin/env python3
"""Fixture SearXNG for the runner's live path.

SearxngBridge.py translates a real SearXNG's JSON into JSearch's 2002 dialect.
This stands in for that SearXNG when JSEARCH_SEARXNG points at it, so the whole
trail — applet, bridge, live-shaped page, 2002 scraper — runs offline and
repeatably. Verify.sh's --live and --combined modes start it.

What it serves, and why:

  * SearXNG's JSON shape: {"results": [{"url", "title", "content"}, ...]},
    which is all the bridge reads.
  * Page-aware, because paging is the interesting part: page 1 carries three
    results, page 2 two more, page 3 and beyond none — a real SearXNG past its
    last page. The 2002 applet asks for a new page per level (the backtick in
    the engine template becomes the level digit), so a run with --levels=2
    exercises the original's own paging loop and must end up with five results.
  * Page 1's first result reuses DemoServer.py's first URL
    (http://localhost:8901/jsearch-reborn) on purpose. A run that searches
    LocalDemo and SearXNG together therefore hands the original two engines
    with one URL in common, and the applet's own result list shows whether its
    cross-engine dedup still works: four results, not five.
  * ASCII titles and previews, because every assertion in verify.sh is on text
    this prints verbatim. The Chinese fixtures live in DemoServer.py, which
    proves the same path decodes the other way.

Usage: python3 StubSearxng.py [port]     (default port 8903)
"""
import json
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8903

# Shared with DemoServer.py's first block, deliberately.
SHARED_URL = "http://localhost:8901/jsearch-reborn"

PAGES = {
    1: [
        {"url": SHARED_URL,
         "title": "Twenty-four years later, the first result",
         "content": "Scraped by a 2002 Java applet whose parser has not changed by one line."},
        {"url": "https://example.org/stub-second",
         "title": "Second stub result",
         "content": "Served by StubSearxng.py so the live path is checkable without the network."},
        {"url": "https://example.org/stub-third",
         "title": "Third stub result",
         "content": "Three blocks on page one, three distinct URLs, nothing deduplicated here."},
    ],
    2: [
        {"url": "https://example.org/stub-page-two-first",
         "title": "First result of page two",
         "content": "Returned only for pageno=2, which is how the applet's level loop pages."},
        {"url": "https://example.org/stub-page-two-second",
         "title": "Second result of page two",
         "content": "The backtick in the engine template becomes the level digit, 0 then 1."},
    ],
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        query = params.get("q", [""])[0]
        pageno = int(params.get("pageno", ["1"])[0] or 1)
        results = PAGES.get(pageno, [])
        body = json.dumps({"query": query, "number_of_results": len(results),
                           "results": results},
                          ensure_ascii=False).encode("utf-8")
        print("[stub-searxng] q=%r pageno=%d -> %d results" % (query, pageno, len(results)),
              flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[stub-searxng]", fmt % args, flush=True)


if __name__ == "__main__":
    print("[stub-searxng] serving a fixture SearXNG on http://127.0.0.1:%d/ (page 1: 3 results,"
          " page 2: 2 more, page 3+: none)" % PORT, flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
