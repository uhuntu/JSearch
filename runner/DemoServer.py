#!/usr/bin/env python3
"""Canned search-result page for the LocalDemo engine in runner/JSENGINES.TXT.

The page is written for JSearch's 2002 scraper, unchanged: result blocks start
with the 4-character marker '<p><', the URL follows 'ref=', the title ends at
'</a>', and each block closes with '</p>' — this engine's end marker. The
third block reuses the first block's URL so deduplication is visible.

Usage: python3 DemoServer.py [port]     (default port 8901)
"""
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8901

PAGE = (
    '<!DOCTYPE html><html><head><title>LocalDemo</title></head><body>'
    '<p><a ref=http://localhost:%d/jsearch-reborn>二十四年后的第一条结果</a><br>'
    '预览一：这条结果由一个2002年的Java Applet抓取，解析代码没有改动过一行。</p>'
    '<p><a ref=http://localhost:%d/second-result>第二条结果</a><br>'
    '预览二：页面由本地服务器提供，因为原本抓的搜索引擎早已不是这个样子了。</p>'
    '<p><a ref=http://localhost:%d/jsearch-reborn>重复网址应当被丢弃</a><br>'
    '预览三：这一条与第一条共用网址，结果列表里不应出现，总计应当是2。</p>'
    '</body></html>'
) % (PORT, PORT, PORT)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = PAGE.encode('utf-8')
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print('[demo-server]', fmt % args, flush=True)


if __name__ == '__main__':
    print('[demo-server] serving canned results on http://127.0.0.1:%d/' % PORT, flush=True)
    ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
