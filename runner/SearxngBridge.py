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
DEEPSEEK_CHAT = "https://api.deepseek.com/chat/completions"
DEEPSEEK_MODEL = os.environ.get("JSEARCH_DEEPSEEK_MODEL", "deepseek-flash")
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


def deepseek_search_results(query):
    payload = json.dumps({"query": query[:128], "count": 30,
                          "need_content": True}).encode("utf-8")
    request = urllib.request.Request(
        DEEPSEEK, data=payload, method="POST",
        headers={"Authorization": "Bearer " + DEEPSEEK_KEY,
                 "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=8) as response:
        return json.loads(response.read().decode("utf-8", "replace")).get("results", [])


def deepseek_page(query, offset):
    now = time.time()
    cached = DEEPSEEK_CACHE.get(query)
    if not cached or now - cached[0] > 120:
        # need_content: the search response then carries each page's text, so
        # the applet's >>预览 shows the article instead of the ~100-char
        # teaser. Same per-request billing; the content is truncated here —
        # a 2002 parser and a small TextArea deserve a bound.
        cached = (now, deepseek_search_results(query))
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


def deepseek_ask(query):
    """The FreeAskInternet idea, minus the Docker: search DeepSeek's index
    (content included), hand the pages to deepseek-flash, return the
    synthesized answer with numbered sources. Reuses the search cache, so
    asking about a query the applet just searched bills only the chat call."""
    now = time.time()
    cached = DEEPSEEK_CACHE.get(query)
    if not cached or now - cached[0] > 120:
        cached = (now, deepseek_search_results(query))
        if len(DEEPSEEK_CACHE) > 16:
            DEEPSEEK_CACHE.clear()
        DEEPSEEK_CACHE[query] = cached
    refs = []
    for i, hit in enumerate(cached[1][:8], 1):
        body = (str(hit.get("content", "")).strip()
                or str(hit.get("snippet", "")).strip())[:1200]
        refs.append("[%d] %s\n%s\n%s" % (i, str(hit.get("title", "")),
                                         str(hit.get("url", "")), body))
    prompt = ("问题：" + query + "\n\n以下是编号的搜索结果，回答时用 [编号] 标注来源：\n\n"
              + "\n\n".join(refs))
    payload = json.dumps({
        "model": DEEPSEEK_MODEL,
        "messages": [
            {"role": "system",
             "content": "你是搜索问答助手。仅依据给出的搜索结果用中文回答，"
                        "关键句末尾用 [编号] 标注来源，回答末尾列出"
                        "“来源：[编号] 标题 URL”。若结果不足以回答，直接说明。"},
            {"role": "user", "content": prompt},
        ],
    }).encode("utf-8")
    request = urllib.request.Request(
        DEEPSEEK_CHAT, data=payload, method="POST",
        headers={"Authorization": "Bearer " + DEEPSEEK_KEY,
                 "Content-Type": "application/json", "User-Agent": UA})
    with urllib.request.urlopen(request, timeout=90) as response:
        data = json.loads(response.read().decode("utf-8", "replace"))
    return data["choices"][0]["message"]["content"]


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
        if url.path.startswith("/app/"):
            return self.serve_app_file(url.path)
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
        elif url.path.startswith("/deepseek/ask"):
            label = "DeepSeek ask"
            try:
                if not DEEPSEEK_KEY:
                    body = "DeepSeek ask：DEEPSEEK_API_KEY not set".encode("utf-8")
                elif not query:
                    body = "（缺少 q 参数）".encode("utf-8")
                else:
                    body = deepseek_ask(query).encode("utf-8")
            except Exception as exc:
                body = ("DeepSeek ask error: %s: %s"
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
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def serve_app_file(self, path):
        """Static files for the CheerpJ browser host: the compiled 2002
        classes, JSENGINES.TXT and the loader page, all same-origin with the
        bridge so the in-browser applet needs no CORS waivers of its own.
        The runner derives currUrl from its own class location, which here is
        /app/ - so /app/JSENGINES.TXT is exactly what getEngData fetches.
        Runtime files under cheerpj/4.3/ that are not on disk yet are pulled
        from the official CDN on first request and cached - after the first
        visit the page is fully offline."""
        root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cheerpj-app")
        rel = path[len("/app/"):] or "index.html"
        target = os.path.normpath(os.path.join(root, rel))
        if not target.startswith(root):
            body = b"forbidden"
            code, ctype = 403, "text/plain"
        else:
            if not os.path.isfile(target) and rel.startswith("cheerpj/"):
                self.provision_runtime(rel, target)
            elif (os.path.isfile(target) and os.path.getsize(target) == 0
                  and rel.startswith("cheerpj/")):
                # A zero-byte file is a poisoned cache entry from an earlier
                # flaky fetch; drop it and try again rather than serving empty
                # fonts and scripts forever.
                os.remove(target)
                self.provision_runtime(rel, target)
            if os.path.isfile(target):
                code = 200
                ext = target.rsplit(".", 1)[-1].lower()
                # js/mjs must be text/javascript: dynamic import() refuses modules
                # served with any other MIME type.
                ctype = {"html": "text/html", "txt": "text/plain",
                         "js": "text/javascript", "mjs": "text/javascript",
                         "json": "application/json",
                         "class": "application/octet-stream"}.get(ext, "application/octet-stream")
                if ctype.startswith("text/") and "octet" not in ctype:
                    ctype += "; charset=utf-8"
                # CheerpJ loads the JRE jars with HTTP Range requests and
                # refuses to run without them: "HTTP server does not support
                # the 'Range' header. CheerpJ cannot run." So the static
                # handler must answer 206 for every byte range it is asked.
                span = self.parse_range(self.headers.get("Range"), os.path.getsize(target))
                if span:
                    start, end = span
                    with open(target, "rb") as f:
                        f.seek(start)
                        body = f.read(end - start + 1)
                    self.send_response(206)
                    self.send_header("Content-Range", "bytes %d-%d/%d"
                                     % (start, end, os.path.getsize(target)))
                else:
                    body = open(target, "rb").read()
                    self.send_response(code)
                self.send_header("Content-Type", ctype)
                self.send_header("Accept-Ranges", "bytes")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            else:
                body = ("not found: " + rel).encode("utf-8", "replace")
                code, ctype = 404, "text/plain"
        self.send_response(code)
        if ctype.startswith("text/") and "octet" not in ctype:
            ctype += "; charset=utf-8"
        self.send_header("Content-Type", ctype)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    @staticmethod
    def parse_range(header, size):
        """'bytes=start-end' / 'bytes=start-' / 'bytes=-suffix' -> (start, end)
        inclusive, clamped to the file; None when absent or unsatisfiable."""
        if not header or not header.startswith("bytes="):
            return None
        spec = header[6:].split(",")[0].strip()
        start_text, dash, end_text = spec.partition("-")
        if not dash:
            return None
        try:
            if start_text == "":
                # suffix: the last N bytes
                start = max(0, size - int(end_text))
                end = size - 1
            else:
                start = int(start_text)
                end = int(end_text) if end_text else size - 1
        except ValueError:
            return None
        if start < 0 or start >= size or end < start:
            return None
        return start, min(end, size - 1)

    def provision_runtime(self, rel, target):
        """Best-effort CDN fetch for one missing CheerpJ runtime file, cached
        next to loader.js so the browser never has to leave this machine
        again. Only callable for paths under cheerpj/."""
        # Windows-invalid names (the JVM's lib/ext/* glob) can never be
        # cached; answer fast instead of failing five times on a '*' filename.
        if any(ch in rel for ch in '*:?"<>|'):
            print("[searxng-bridge] runtime path not cacheable here: %s" % rel, flush=True)
            return
        source = "https://cjrtnc.leaningtech.com/4.3/" + rel[len("cheerpj/4.3/"):]
        os.makedirs(os.path.dirname(target), exist_ok=True)
        for attempt in range(5):
            try:
                request = urllib.request.Request(source, headers={"User-Agent": UA})
                with urllib.request.urlopen(request, timeout=60) as response:
                    data = response.read()
                if len(data) < 64:
                    # An empty or stub body is the flaky proxy talking, not the
                    # runtime file; caching it would poison every later request
                    # with an empty font/JS payload.
                    print("[searxng-bridge] runtime fetch for %s returned %d bytes; retrying"
                          % (rel, len(data)), flush=True)
                    time.sleep(2)
                    continue
                tmp = target + ".part"
                open(tmp, "wb").write(data)
                os.replace(tmp, target)
                print("[searxng-bridge] self-hosted runtime file: %s (%d bytes)"
                      % (rel, len(data)), flush=True)
                return
            except Exception as exc:
                print("[searxng-bridge] runtime fetch attempt %d for %s failed: %s"
                      % (attempt + 1, rel, exc), flush=True)
                time.sleep(2)

    def log_message(self, fmt, *args):
        print("[searxng-bridge]", fmt % args, flush=True)


if __name__ == "__main__":
    print("[searxng-bridge] translating %s into the 2002 dialect on http://127.0.0.1:%d/"
          % (SEARXNG, PORT), flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
