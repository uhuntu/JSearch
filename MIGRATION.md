# Not a product

`modern/` is a **design study**, not a starter kit for a search aggregator.

Do not deploy it. Do not treat Brave/SearXNG helpers, `WebServer`, or the JSON
scraper as a production path. Those pieces exist to show seams the 2002 applet
lacked (a fetcher interface, a scraper interface, interruptible `Future`s) and
to replay the URL-key collision against archived HTML.

If you want a live metasearch tool, start a different repository. This one stays
an archive.

For how the sketch is put together, see [modern/README.md](modern/README.md).
For why the original cannot run, see [README.md](README.md#why-this-cannot-be-revived).
