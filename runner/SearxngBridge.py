#!/usr/bin/env python3
"""Translate SearXNG into JSearch's 2002 scraper dialect.

The untouched 2002 applet cannot read SearXNG's HTML, but it can read a page
written in its own dialect — the exact one DemoServer.py's canned page uses:

    <p><a ref=URL>TITLE</a><br>
    PREVIEW</p>

Blocks start with the 4-character marker '<p><', the URL follows 'ref='
unquoted and ends at '>', the title ends at '</a>', the block closes with
'</p>'. This adapter fetches SearXNG's JSON API for the requested query and
page and re-emits exactly that, so the 4-character sliding-window scraper in
Sources/SearchThread.java reads the live web without one line of change.

Usage: python3 SearxngBridge.py [port]     (default 8902)
"""
import json
import sys
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8902
SEARXNG = "http://127.0.0.1:8888/search?format=json"
UA = ("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")


def clean(text, is_url=False):
    """The 2002 parser's assumptions: a URL ends at '>' or a space, so no
    quotes or spaces may appear in it; a title containing '</a>' would end
    the title early; '<' and '>' anywhere are filtered by the scraper itself
    but removing them here keeps titles readable."""
    text = (text or "").replace("\r", " ").replace("\n", " ").strip()
    if is_url:
        return text.replace('"', "").replace(" ", "%20").replace(">", "%3E")
    return text.replace("</a>", "").replace("<", "").replace(">", "")


def page_for(query, pageno):
    url = SEARXNG + "&q=" + urllib.parse.quote(query) + "&pageno=" + str(pageno)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=8) as response:
        data = json.loads(response.read().decode("utf-8", "replace"))
    blocks = []
    for hit in data.get("results", []):
        href = clean(str(hit.get("url", "")), is_url=True)
        title = clean(str(hit.get("title", ""))) or "(untitled)"
        preview = clean(str(hit.get("content", "")))
        blocks.append("<p><a ref=%s>%s</a><br>\n%s</p>" % (href, title, preview))
    return "\n".join(blocks)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        query = params.get("q", [""])[0].strip()
        pageno = int(params.get("page", ["0"])[0] or 0) + 1  # applet pages from 0
        try:
            body = page_for(query, pageno).encode("utf-8")
        except Exception as exc:
            body = ("<p><a ref=http://127.0.0.1:%d/bridge-error>SearXNG bridge error: %s</a><br>\n%s</p>"
                    % (PORT, clean(type(exc).__name__), clean(str(exc)))).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[searxng-bridge]", fmt % args, flush=True)


if __name__ == "__main__":
    print("[searxng-bridge] translating SearXNG into the 2002 dialect on http://127.0.0.1:%d/"
          % PORT, flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
