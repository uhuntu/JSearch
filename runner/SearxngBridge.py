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

Environment:
    JSEARCH_SEARXNG  the upstream SearXNG JSON API to translate. Default
                     http://127.0.0.1:8888/search?format=json. Point it at a
                     fixture such as StubSearxng.py to exercise this bridge
                     without a live SearXNG or the network.
    DEEPSEEK_API_KEY enables the /deepseek/ routes (api.deepseek.com's
                     official Search API, the first keyed upstream). Without
                     the variable those routes serve a bridge-error block
                     saying so, and the applet shows it as the engine's
                     result - the same honesty the historical engines had.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8902
SEARXNG = os.environ.get("JSEARCH_SEARXNG", "http://127.0.0.1:8888/search?format=json")
# A base that already carries a query string (the default does: format=json)
# takes another '&'; a bare base takes the '?' it is missing.
SEP = "&" if "?" in SEARXNG else "?"
DEEPSEEK = "https://api.deepseek.com/v1/tools/search"
DEEPSEEK_FETCH = "https://api.deepseek.com/v1/tools/fetch"
DEEPSEEK_KEY = os.environ.get("DEEPSEEK_API_KEY", "")
# One billed DeepSeek call serves every level of one search: the API returns
# a single ranked list (no pagination), so page 00/10/20 are slices of one
# fetch kept here for two minutes.
DEEPSEEK_CACHE = {}
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
    url = SEARXNG + SEP + "q=" + urllib.parse.quote(query) + "&pageno=" + str(pageno)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=8) as response:
        data = json.loads(response.read().decode("utf-8", "replace"))
    blocks = []
    for hit in data.get("results", []):
        href = clean(str(hit.get("url", "")), is_url=True)
        title = clean(str(hit.get("title", ""))) or "(untitled)"
        preview = clean(str(hit.get("content", "")))
        blocks.append("<p><a ref=%s>%s</a><br>\n%s</p>" % (href, title, preview))
    # The scraper drops a block whose </p> lands on end-of-stream: after
    # analyseBlock() it steps one character and breaks when that fails, on the
    # assumption that a page's real end marker follows its last result. So the
    # blocks need something after them — DemoServer.py ends its page with
    # </body></html> for exactly this reason. Emit the same terminator, or the
    # last result of every page is silently lost.
    return "\n".join(blocks) + "\n</body></html>"


def deepseek_page(query, offset):
    now = time.time()
    cached = DEEPSEEK_CACHE.get(query)
    if not cached or now - cached[0] > 120:
        # need_content: the search response then carries each page's text, so
        # the applet's >>预览 shows the article instead of the ~100-char
        # teaser. Same per-request billing; the content is truncated here —
        # a 2002 parser and a small TextArea deserve a bound.
        payload = json.dumps({"query": query[:128], "count": 30,
                              "need_content": True}).encode("utf-8")
        request = urllib.request.Request(
            DEEPSEEK, data=payload, method="POST",
            headers={"Authorization": "Bearer " + DEEPSEEK_KEY,
                     "Content-Type": "application/json", "User-Agent": UA})
        with urllib.request.urlopen(request, timeout=8) as response:
            data = json.loads(response.read().decode("utf-8", "replace"))
        cached = (now, data.get("results", []))
        if len(DEEPSEEK_CACHE) > 16:
            DEEPSEEK_CACHE.clear()
        DEEPSEEK_CACHE[query] = cached
    blocks = []
    for hit in cached[1][offset:offset + 10]:
        href = clean(str(hit.get("url", "")), is_url=True)
        title = clean(str(hit.get("title", ""))) or "(untitled)"
        # Full text when DeepSeek could extract it, the teaser otherwise.
        preview = str(hit.get("content", "")) or str(hit.get("snippet", ""))
        blocks.append("<p><a ref=%s>%s</a><br>\n%s</p>"
                      % (href, title, clean(preview[:2000])))
    return "\n".join(blocks) + "\n</body></html>"


def error_block(label, exc):
    return ("<p><a ref=http://127.0.0.1:%d/bridge-error>%s error: %s</a><br>\n%s</p>"
            % (PORT, label, clean(type(exc).__name__), clean(str(exc))))


def deepseek_not_configured():
    return ("<p><a ref=http://127.0.0.1:%d/bridge-error>DeepSeek bridge: "
            "DEEPSEEK_API_KEY not set</a><br>\nthe keyed engine stays off until "
            "the variable exists</p>" % PORT).encode("utf-8")


def deepseek_fetch(url):
    """The Fetch API: one URL in, the page's body out as Markdown. This is
    what the applet's 工具 menu calls - the runner shows the text in >>预览."""
    payload = json.dumps({"url": url[:8192]}).encode("utf-8")
    request = urllib.request.Request(
        DEEPSEEK_FETCH, data=payload, method="POST",
        headers={"Authorization": "Bearer " + DEEPSEEK_KEY,
                 "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=20) as response:
        data = json.loads(response.read().decode("utf-8", "replace"))
    title = str(data.get("title", "")).strip()
    content = str(data.get("content", "")).strip()[:20000]
    if not content:
        return "（DeepSeek 未能从该页面提取出正文）"
    return ("# " + title + "\n\n" + content) if title else content


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(url.query)
        query = params.get("q", [""])[0].strip()
        # The 2002 templates carry the level in the tens digit: the shipped
        # records are Google's `start=`0` and Baidu's `pn=`0`, both 0-based
        # result offsets counting by ten. So the applet asks for page=00, 10,
        # 20 for levels 0, 1, 2 — one page of ten results per level. SearXNG
        # numbers its pages from one, so the offset has to be divided back out.
        # Handing SearXNG the raw value instead asks it for page 11 when level
        # 1 meant page 2, which returns nothing and says nothing. DeepSeek has
        # no pages at all, so its offset slices the one cached ranked list.
        try:
            offset = int(params.get("page", ["0"])[0] or 0)
        except ValueError:
            offset = 0
        if url.path.startswith("/deepseek/fetch"):
            label = "DeepSeek fetch"
            try:
                target = params.get("url", [""])[0].strip()
                if not DEEPSEEK_KEY:
                    body = deepseek_not_configured()
                elif not target:
                    body = "（缺少 url 参数）".encode("utf-8")
                else:
                    body = deepseek_fetch(target).encode("utf-8")
            except Exception as exc:
                body = ("DeepSeek fetch error: %s: %s"
                        % (type(exc).__name__, exc)).encode("utf-8")
        elif url.path.startswith("/deepseek"):
            label = "DeepSeek bridge"
            try:
                body = (deepseek_not_configured() if not DEEPSEEK_KEY
                        else deepseek_page(query, offset).encode("utf-8"))
            except Exception as exc:
                body = error_block(label, exc).encode("utf-8")
        else:
            label = "SearXNG bridge"
            try:
                body = page_for(query, offset // 10 + 1).encode("utf-8")
            except Exception as exc:
                body = error_block(label, exc).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("[searxng-bridge]", fmt % args, flush=True)


if __name__ == "__main__":
    print("[searxng-bridge] translating %s into the 2002 dialect on http://127.0.0.1:%d/"
          % (SEARXNG, PORT), flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
