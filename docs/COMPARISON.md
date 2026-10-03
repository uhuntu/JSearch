# Original vs Modern Design Comparison

Detailed comparison of the 2002 original and the modern reference design.

## Code Structure

| Metric | Original (2002) | Modern Reference |
|--------|-----------------|------------------|
| **Files** | 2 (JSApplet.java, SearchThread.java) | 13 |
| **Total lines** | 906 | ~1,800 (including tests) |
| **Max class size** | 906 lines (JSApplet) | 175 lines (HtmlBlockScraper) |
| **Avg class size** | 453 lines | 138 lines |
| **Inner classes** | 15 | 0 |
| **Static fields** | 20+ | 0 |

## Architecture

### Original: God Class
```
JSApplet (906 lines)
── UI Layout (setBounds everywhere)
├── HTTP Fetching (openStream)
├── Thread Management (manual)
├── Result State (4 duplicate stores)
├── i18n (changeLanguage switch)
└── Browser Launch (Runtime.exec)
```

### Modern: Component Separation
```
Engine              - Data model
EngineRepository    - Parsing and validation
PageFetcher         - HTTP interface
HttpPageFetcher     - HTTP implementation
BlockScraper        - Scraping interface
HtmlBlockScraper    - HTML implementation
JsonApiScraper      - JSON implementation
ResultCollector     - Thread-safe dedup
SearchService       - Orchestration
SearchResult        - Immutable result
```

## Concurrency

| Aspect | Original | Modern |
|--------|----------|--------|
| **Thread model** | 1 Thread per engine | ExecutorService fixed pool |
| **Thread count** | Unbounded | Configurable (default 4) |
| **Cancellation** | Polled boolean flag | Future.cancel(true) |
| **Interrupt response** | Between character reads | Immediate (interrupts I/O) |
| **Locking** | 5 nested synchronized blocks | Single lock in ResultCollector |
| **Deadlock risk** | High (lock ordering) | None (single lock) |
| **Race conditions** | TOCTOU, lost updates | None (atomic check-then-act) |

## Data Model

### Original
```java
// 4 duplicate stores for the same data
static Hashtable resultTable;  // URL -> {title, preview}
static Vector resultIndex;     // URL list (ordered)
List resultLi;                 // UI list
Label totalNumLa;              // Count

// Engine identity = URL (bug: collisions)
engDataHt.put(url, engineDetails);
```

### Modern
```java
// Single source of truth
ResultCollector {
    private final Set<String> seenUrls;
    private final List<SearchResult> results;
    private int count;
}

// Engine identity = (name, category)
Engine {
    String name;
    String category;
    String urlTemplate;
    Block resultBlock;
}
```

## Testing

| Aspect | Original | Modern |
|--------|----------|--------|
| **Tests** | 0 | 35 |
| **Test framework** | None | Custom (dependency-free) |
| **Fixtures** | None | Archive HTML pages |
| **Concurrency tests** | None | 8-thread dedup test |
| **Coverage** | Unknown | High (all components) |

## Encoding

| Aspect | Original | Modern |
|--------|----------|--------|
| **Source encoding** | GBK (implicit) | UTF-8 (explicit) |
| **Runtime encoding** | Platform default | UTF-8 forced |
| **Protection** | None | .gitattributes binary flag |

## Build

| Aspect | Original | Modern |
|--------|----------|--------|
| **Build tool** | Visual J++ 6.0 only | JDK 11+ (any platform) |
| **Dependencies** | JDK 1.3 | JDK 11+ |
| **Build command** | IDE only | 4 options (PS/Bash/Make/Docker) |
| **CI/CD** | None | GitHub Actions |

## Key Improvements

### 1. Identity Fix
**Original bug:** URL as key causes silent engine loss
```java
// Two engines with same URL → one overwrites the other
engDataHt.put("http://google.com/", englishGoogle);
engDataHt.put("http://google.com/", chineseGoogle); // overwrites!
```

**Modern fix:** (name, category) tuple
```java
// Both engines preserved
Engine english = new Engine("Google", "English", ...);
Engine chinese = new Engine("GB_Chinese", "Chinese", ...);
```

### 2. Concurrency Fix
**Original bug:** Per-instance lock provides no mutual exclusion
```java
synchronized void showResult() {
    // Locks on 'this' - each thread has different 'this'!
    // No actual synchronization between threads
}
```

**Modern fix:** Shared lock
```java
public boolean add(SearchResult result) {
    synchronized (this) {  // Single collector instance
        if (seenUrls.add(url)) {
            results.add(result);
            count++;
            return true;
        }
        return false;
    }
}
```

### 3. Cancellation Fix
**Original bug:** Flag checked between characters
```java
_stop = true;  // Set by UI thread
// Worker thread checks once per character read
if (_stop) break;  // May take seconds to respond
```

**Modern fix:** Interrupt-based
```java
future.cancel(true);  // Interrupts blocked I/O immediately
// Worker thread gets InterruptedException
```

### 4. Testability
**Original:** No tests, God class, static state
**Modern:** Interface-based, pure functions, dependency injection

```java
// Testable scraper
BlockScraper scraper = new HtmlBlockScraper();
List<SearchResult> results = scraper.scrape(engine, html);
// No network, no UI, no static state
```

## Performance

| Metric | Original | Modern |
|--------|----------|--------|
| **GC pressure** | High (20 finalize() overrides) | Low (no finalize) |
| **Thread overhead** | High (1 per engine) | Low (pooled) |
| **Lock contention** | High (5 nested locks) | Low (1 lock) |
| **Memory** | 4x duplication | 1x (single source) |

## Maintainability

| Metric | Original | Modern |
|--------|----------|--------|
| **Cyclomatic complexity** | High (nested switches) | Low (single responsibility) |
| **Cognitive load** | 906 lines in one file | ~140 lines per file |
| **Change impact** | Global (everything coupled) | Local (component isolated) |
| **Test feedback** | None | Instant (35 tests) |

## Conclusion

The modern reference design addresses every major issue in the original:
- ✅ Component separation (vs God class)
- ✅ Proper concurrency (vs manual threads)
- ✅ Correct identity model (vs URL key)
- ✅ Thread-safe dedup (vs races)
- ✅ Testable (vs untestable)
- ✅ Cross-platform build (vs Visual J++ only)
- ✅ Encoding protection (vs implicit GBK)

The original is preserved as a historical artifact. The modern design shows how it should have been built.
