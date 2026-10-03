# FAQ

## General

### What is JSearch?
JSearch is a Java applet from 1999-2002 that aggregated search results from multiple engines (Google, Baidu, etc.) into a single de-duplicated list. This repository is an **archive** of that software.

### Can I run the original applet?
**No.** Applets were removed from browsers (2015-2021) and from the JDK (deprecated in JDK 9, removed in JDK 11). The scraped engines are also dead or blocking.

### What is `modern/`?
A **reference design** showing how JSearch should have been built with modern practices: component separation, `ExecutorService`, immutable results, and 35 tests. It compiles and runs on JDK 11+, but doesn't connect to any network.

### Is this a revival?
**No.** The original source is frozen. `modern/` is a design study, not a port.

---

## Technical

### Why are some files marked as `binary` in `.gitattributes`?
The original Java sources and engine files are **GBK-encoded** (Chinese legacy encoding). Marking them as binary prevents accidental UTF-8 conversion that would permanently corrupt them.

### How do I read the original source?
```bash
iconv -f GBK -t UTF-8 Sources/JSApplet.java | less
```

### What was the "deadlock patch"?
The March 2002 patch tried to fix a deadlock by using `synchronized void showResult()`, but this locks on `this` (each thread instance), providing **zero mutual exclusion** between threads. The real fix (shared static lock) was merged later.

### Why does the engine file lose engines?
`getEngData()` uses the **URL as Hashtable key**. Two engines sharing a URL (English Google and Chinese Google both use `http://www.google.com/`) causes one to overwrite the other silently.

### How many tests are there?
**35 tests** covering:
- Engine parsing (5 tests)
- Engine identity and URL building (3 tests)
- HTML scraping (5 tests)
- Result collection and dedup (3 tests)
- Search service concurrency (3 tests)
- JSON API scraping (7 tests)
- Archive fixtures (9 tests)

---

## Contributing

### Can I fix bugs in the original code?
**No.** `Sources/` and `versions/` are frozen artifacts. The only exception is the documented deadlock patch in `Sources/SearchThread.java`.

### Can I add features to `modern/`?
**Yes.** Bug fixes, new tests, performance improvements, and new scraper implementations are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

### How do I run tests?
```powershell
cd modern
.\build.ps1 -Test
```
Or on Linux/Mac:
```bash
cd modern
./build.sh -t
```

### What JDK version is required?
**JDK 11 or later.** The reference design uses `var`, `List.of()`, and other modern Java features.

---

## Historical

### Why is the class named `JSApplet` but the project is `JSearch`?
The class was named when the code was purely an applet. The product was always called **JSearch**. The 2002 release ships `JSearch.cab` containing `JSApplet.class`.

### What happened to the author?
Hunt Lin developed JSearch from 1999-2002 while in Xiamen, China. The project was documented against the Chinese national standard **GB8567-88**. The archive was recovered and reorganized in 2026.

### Why GB8567-88?
GB8567-88 is the Chinese national standard for software documentation. The project includes all 8 required documents: feasibility study, project plan, requirements spec, design spec, detailed design, source code list, test plan, and user manual.

### What engines did JSearch support?
The shipped `JSEngines.txt` defines three engines:
- **Google** (English)
- **GB_Chinese Google** (Chinese) — *silently dropped due to URL key collision*
- **Baidu** (Chinese)

Four HTML pages were captured on 2001-12-27: `google_en.html`, `google_cn.html`, `baidu_cn.html`, and `lycos_en.html` (never shipped).

---

## Troubleshooting

### "GBK charset not available"
Use a full JDK from [Adoptium](https://adoptium.net/). Minimal JDK installations may exclude GBK.

### Tests fail with "archive root not found"
Run tests from the `modern/` directory, or set `-Djsearch.archive=<path to repo root>`.

### Compilation fails
Ensure JDK 11+:
```bash
java -version
```

### `.kilo` directory keeps appearing
This is created by Kilo IDE. It's now in `.gitignore`. Delete it manually if needed:
```powershell
Remove-Item -Recurse -Force .kilo
```
