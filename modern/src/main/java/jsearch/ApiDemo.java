package jsearch;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Runs a real query against a search API. Needs the network and a key, so unlike
 * {@link Demo} it is not run by the tests.
 *
 * <pre>
 *   BRAVE_API_KEY=...  java -cp out jsearch.ApiDemo "your query"
 *   SEARXNG_URL=http://localhost:8080  java -cp out jsearch.ApiDemo "your query"
 * </pre>
 */
public final class ApiDemo {

    private static final Engine.Block NO_MARKERS = new Engine.Block("{", "}");

    public static void main(String[] args) {
        String query = args.length > 0 ? String.join(" ", args) : "java";
        String braveKey = System.getenv("BRAVE_API_KEY");
        String searxng = System.getenv("SEARXNG_URL");
        if (braveKey == null && searxng == null) {
            System.err.println("set BRAVE_API_KEY and/or SEARXNG_URL");
            System.exit(2);
        }

        HttpPageFetcher fetcher = new HttpPageFetcher();
        // Brave and SearXNG both answer JSON, but with different shapes; run one
        // service per provider so each gets the right scraper.
        if (braveKey != null) {
            String base = "https://api.search.brave.com/res/v1/web/search";
            fetcher.withHeader(base, "X-Subscription-Token", braveKey);
            run(new Engine("Brave", "API", base + "?q=^&count=10&offset=`", NO_MARKERS),
                    fetcher, JsonApiScraper.brave(), query, 2);
        }
        if (searxng != null) {
            // SearXNG pages are 1-based and level starts at 0, so one level only.
            run(new Engine("SearXNG", "API", searxng + "/search?q=^&format=json&pageno=1", NO_MARKERS),
                    fetcher, JsonApiScraper.searxng(), query, 1);
        }
    }

    private static void run(Engine engine, PageFetcher fetcher, BlockScraper scraper, String query, int levels) {
        ResultCollector collector = new ResultCollector();
        collector.onResult(r -> System.out.println(r.title() + "\n  " + r.url() + "\n  " + r.preview()));
        collector.onError(e -> System.err.println(engine + " failed: " + e));
        SearchService service = new SearchService(2, fetcher, scraper);
        List<Future<?>> running = service.search(Arrays.asList(engine), query, levels, collector);
        service.awaitAll(running, collector);
        service.shutdown();
        System.out.println(engine + ": " + collector.count() + " results");
    }

    private ApiDemo() {
    }
}
