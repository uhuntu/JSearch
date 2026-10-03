# JSearch Architecture Comparison

## Original Design (2002)

```mermaid
graph TB
    subgraph "JSApplet.java - 906 lines God Class"
        UI[UI Layout<br/>setBounds everywhere]
        HTTP[HTTP Fetching<br/>openStream]
        Thread[Thread Management<br/>static _stop flag]
        State[Result State<br/>4 duplicate stores]
        i18n[i18n<br/>changeLanguage switch]
        Browser[Browser Launch<br/>Runtime.exec]
    end
    
    UI --> HTTP
    HTTP --> Thread
    Thread --> State
    State --> UI
    UI --> i18n
    UI --> Browser
    
    subgraph "Data Flow"
        EngineFile[JSEngines.txt<br/>URL as key]
        ResultTable[Hashtable resultTable]
        ResultIndex[Vector resultIndex]
        ResultLi[List resultLi]
        TotalLabel[Label totalNumLa]
    end
    
    EngineFile --> HTTP
    HTTP --> ResultTable
    ResultTable --> ResultIndex
    ResultIndex --> ResultLi
    ResultLi --> TotalLabel
    
    style JSApplet fill:#ff6b6b
    style EngineFile fill:#ffd93d
```

## Modern Reference Design

```mermaid
graph TB
    subgraph "Component Separation"
        Engine[Engine<br/>id, name, category<br/>urlTemplate, block]
        Repo[EngineRepository<br/>parse, validate<br/>duplicate check]
        Fetcher[PageFetcher<br/>interface]
        HttpFetcher[HttpPageFetcher<br/>JDK HttpClient]
        Scraper[BlockScraper<br/>interface]
        HtmlScraper[HtmlBlockScraper<br/>pure function]
        JsonScraper[JsonApiScraper<br/>brave, searxng]
        Collector[ResultCollector<br/>thread-safe<br/>dedup, count]
        Service[SearchService<br/>ExecutorService<br/>fixed pool]
        Result[SearchResult<br/>immutable]
    end
    
    subgraph "Data Flow"
        Config[JSEngines.txt<br/>name+category key]
        Repo --> Engine
        Config --> Repo
        Engine --> Service
        Fetcher --> HttpFetcher
        Service --> Fetcher
        Scraper --> HtmlScraper
        Scraper --> JsonScraper
        Service --> Scraper
        Service --> Collector
        Collector --> Result
    end
    
    subgraph "Concurrency"
        Pool[ExecutorService<br/>newFixedThreadPool]
        Future[Future<br/>cancel true]
        Lock[Single Lock<br/>check-then-act]
    end
    
    Service --> Pool
    Pool --> Future
    Collector --> Lock
    
    style Engine fill:#95e1d3
    style Collector fill:#95e1d3
    style Service fill:#95e1d3
    style Result fill:#95e1d3
```

## Key Improvements

| Aspect | Original | Modern |
|--------|----------|--------|
| **Identity** | URL as key (collides) | (name, category) tuple |
| **Threading** | 1 Thread per engine, manual counter | ExecutorService fixed pool |
| **Cancellation** | Polled boolean flag | Future.cancel(true) interrupts |
| **Result State** | 4 duplicate stores | Single ResultCollector |
| **Dedup** | TOCTOU race | Atomic check-then-act under lock |
| **Scraper** | Inline, mutates statics | Pure function, testable |
| **Testing** | None | 35 tests, archive fixtures |
| **Encoding** | GBK implicit | UTF-8 explicit |
| **Build** | Visual J++ only | JDK 11+, platform-agnostic |
