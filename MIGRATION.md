# Migration Guide

How to adapt the `modern/` reference design for production use.

## Overview

The reference design demonstrates **how JSearch should have been built**. It is
not a production-ready search aggregator, but the architecture is sound and can
be adapted.

## What you get

- **Engine parsing** — `EngineRepository` handles the engine file format
- **HTML scraping** — `HtmlBlockScraper` extracts results from HTML pages
- **JSON API scraping** — `JsonApiScraper` supports Brave and SearXNG formats
- **Concurrent search** — `SearchService` with `ExecutorService` fixed pool
- **Thread-safe dedup** — `ResultCollector` with single-lock check-then-act
- **Cancellation** — `Future.cancel(true)` interrupts blocked fetches

## What you need to add

### 1. UI Layer

The reference design has no UI. Add:
- **Swing/JavaFX** for desktop
- **Spring Boot + Thymeleaf** for web
- **REST API** for microservice

The `SearchViewModel` pattern from the original analysis would work well:
```java
public class SearchViewModel {
    private final SearchService service;
    private final ResultCollector collector;
    
    public SearchViewModel(SearchService service) {
        this.service = service;
        this.collector = new ResultCollector();
        collector.onResult(this::onNewResult);
        collector.onComplete(this::onSearchComplete);
        collector.onError(this::onSearchError);
    }
    
    private void onNewResult(SearchResult result) {
        // Update UI on EDT
        SwingUtilities.invokeLater(() -> {
            // Add result to table
        });
    }
}
```

### 2. Engine Configuration

The reference design reads from a text file. For production:
- **Database** — Store engines in a table with validation
- **YAML/JSON config** — Easier to maintain than the custom format
- **Admin UI** — Let users add/remove engines

Example YAML:
```yaml
engines:
  - name: Google
    category: English
    urlTemplate: "https://www.google.com/search?q=^&start=`0"
    blockStart: "<div class=\"g\">"
    blockEnd: "</div>"
    scraper: html
    
  - name: Brave
    category: API
    urlTemplate: "https://api.search.brave.com/res/v1/web/search?q=^"
    scraper: json-brave
    headers:
      X-Subscription-Token: "${BRAVE_API_KEY}"
```

### 3. Rate Limiting

The reference design has no rate limiting. Add:
- **Per-engine rate limits** — e.g., 1 request/second for Brave API
- **Global rate limit** — e.g., 10 requests/second total
- **Retry with backoff** — Handle 429 Too Many Requests

```java
public class RateLimitedFetcher implements PageFetcher {
    private final PageFetcher delegate;
    private final Map<String, RateLimiter> limiters;
    
    public String fetch(String url) throws IOException {
        String engine = extractEngine(url);
        limiters.get(engine).acquire();
        return delegate.fetch(url);
    }
}
```

### 4. Caching

Add caching to avoid repeated searches:
- **In-memory cache** — `ConcurrentHashMap<String, CachedResult>`
- **Disk cache** — Store results for common queries
- **TTL** — Expire cache entries after N minutes

```java
public class CachingFetcher implements PageFetcher {
    private final PageFetcher delegate;
    private final Cache<String, String> cache;
    
    public String fetch(String url) throws IOException {
        return cache.get(url, () -> delegate.fetch(url));
    }
}
```

### 5. Authentication

For API-based engines:
- **Environment variables** — `BRAVE_API_KEY`, `SEARXNG_URL`
- **Secret managers** — AWS Secrets Manager, HashiCorp Vault
- **OAuth** — For engines requiring user authentication

The reference design's `HttpPageFetcher.withHeader()` supports per-URL-prefix
headers, so API keys are never sent to unrelated hosts.

### 6. Logging

Add structured logging:
```java
public class LoggingCollector extends ResultCollector {
    private static final Logger log = LoggerFactory.getLogger(LoggingCollector.class);
    
    @Override
    public boolean add(SearchResult result) {
        boolean added = super.add(result);
        if (added) {
            log.info("New result: {} from {}", result.url(), result.engine().name());
        }
        return added;
    }
}
```

### 7. Metrics

Track search performance:
- **Search duration** — Time from start to complete
- **Results per engine** — How many results each engine returns
- **Error rate** — Failed engines vs successful
- **Cache hit rate** — If caching is implemented

Use Micrometer or Dropwizard Metrics for production.

### 8. Testing

The reference design has 35 tests. Add:
- **Integration tests** — Test against real APIs (with rate limiting)
- **Load tests** — Simulate concurrent searches
- **Contract tests** — Verify engine response formats

## Architecture decisions

### Why `ExecutorService` over manual threads?
- **Pool reuse** — Threads are expensive to create
- **Cancellation** — `Future.cancel(true)` interrupts blocked I/O
- **Error handling** — Exceptions don't kill the pool
- **Monitoring** — Easy to track active/completed tasks

### Why single lock in `ResultCollector`?
- **Simplicity** — One lock is easier to reason about
- **Correctness** — Check-then-act is atomic
- **Performance** — Lock contention is low (results arrive slowly)

### Why immutable `SearchResult`?
- **Thread safety** — No synchronization needed for reads
- **Predictability** — Results don't change after creation
- **Cacheability** — Safe to store and share

## Example: Web search service

```java
@SpringBootApplication
public class SearchWebApp {
    
    @Bean
    public SearchService searchService() {
        return new SearchService(4, new HttpPageFetcher(), new HtmlBlockScraper());
    }
    
    @RestController
    public class SearchController {
        
        @Autowired
        private SearchService service;
        
        @GetMapping("/search")
        public Flux<SearchResult> search(@RequestParam String q) {
            ResultCollector collector = new ResultCollector();
            List<Engine> engines = loadEngines();
            List<Future<?>> futures = service.search(engines, q, 1, collector);
            
            return Flux.create(sink -> {
                collector.onResult(sink::next);
                collector.onComplete(() -> sink.complete());
                collector.onError(sink::error);
            });
        }
    }
}
```

## Deployment

### Docker
```dockerfile
FROM eclipse-temurin:11-jdk
COPY modern/out /app
CMD ["java", "-cp", "/app", "com.example.SearchApp"]
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jsearch
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: jsearch
        image: jsearch:latest
        env:
        - name: BRAVE_API_KEY
          valueFrom:
            secretKeyRef:
              name: api-keys
              key: brave
```

## Checklist

- [ ] Add UI layer (Swing/JavaFX/Web)
- [ ] Move engine config to database or YAML
- [ ] Implement rate limiting
- [ ] Add caching
- [ ] Set up authentication for APIs
- [ ] Add structured logging
- [ ] Add metrics
- [ ] Write integration tests
- [ ] Deploy with Docker/Kubernetes

## Resources

- [Reference design](modern/README.md)
- [Architecture diagrams](docs/ARCHITECTURE.md)
- [Analysis of original design](docs/ANALYSIS.md)
- [Quick start guide](QUICKSTART.md)
