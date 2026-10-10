package jsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Runs a live query through the same architecture the archive modes use &mdash;
 * {@link Engine} records, {@link SearchService}'s fan-out,
 * {@link ResultCollector}'s dedup &mdash; with the live engine set and a real
 * fetcher.
 *
 * <p>Two wrappers around the fetch-and-scrape path add what 2026 needs and the
 * archive never had. Every engine's outcome is reported &mdash; how many
 * results, how long, and why it returned nothing &mdash; because "one engine
 * failing is data, not a crash" is only honest if the failure is visible. And a
 * response that parses but is about nothing is dropped by
 * {@link RelevanceCheck} instead of shown as results.
 */
public final class LiveSearch {

    /** One engine's report for one query. {@code note} is null on success. */
    public static final class EngineReport {

        private final String engine;
        private final int results;
        private final long ms;
        private final String note;

        EngineReport(String engine, int results, long ms, String note) {
            this.engine = engine;
            this.results = results;
            this.ms = ms;
            this.note = note;
        }

        public String engine() {
            return engine;
        }

        public int results() {
            return results;
        }

        public long ms() {
            return ms;
        }

        public String note() {
            return note;
        }
    }

    /** The merged outcome: deduplicated results, per-engine reports, counts. */
    public static final class Outcome {

        private final List<SearchResult> results;
        private final List<EngineReport> reports;
        private final int rawCount;
        private final int duplicatesDropped;
        private final long durationMs;

        Outcome(List<SearchResult> results, List<EngineReport> reports, int rawCount,
                int duplicatesDropped, long durationMs) {
            this.results = results;
            this.reports = reports;
            this.rawCount = rawCount;
            this.duplicatesDropped = duplicatesDropped;
            this.durationMs = durationMs;
        }

        public List<SearchResult> results() {
            return results;
        }

        public List<EngineReport> reports() {
            return reports;
        }

        public int rawCount() {
            return rawCount;
        }

        public int duplicatesDropped() {
            return duplicatesDropped;
        }

        public long durationMs() {
            return durationMs;
        }
    }

    /**
     * Searches every live engine for {@code query} at one level each &mdash;
     * the APIs page by their own limit, not by a level digit &mdash; and merges
     * the results.
     *
     * @param fetcher the real network fetcher; tests pass a canned one
     */
    public static Outcome run(String query, int concurrency, PageFetcher fetcher) {
        List<Engine> engines = LiveEngines.engines();
        ResultCollector collector = new ResultCollector();
        List<EngineReport> reports = Collections.synchronizedList(new ArrayList<>());
        List<Integer> kept = Collections.synchronizedList(new ArrayList<>());

        PageFetcher reportingFetcher = url -> {
            long start = System.currentTimeMillis();
            try {
                return fetcher.fetch(url);
            } catch (Exception failure) {
                reports.add(new EngineReport(engineFor(url), 0,
                        System.currentTimeMillis() - start, describe(failure)));
                // PageFetcher declares IOException; keep the original as the
                // cause so nothing about the failure is lost
                if (failure instanceof IOException) {
                    throw (IOException) failure;
                }
                throw new IOException(failure);
            }
        };

        BlockScraper guarded = (engine, body) -> {
            long start = System.currentTimeMillis();
            List<SearchResult> hits;
            try {
                hits = LiveEngines.dispatch().scrape(engine, body);
            } catch (RuntimeException failure) {
                reports.add(new EngineReport(engine.name(), 0,
                        System.currentTimeMillis() - start, describe(failure)));
                throw failure;
            }
            String note = null;
            if (!RelevanceCheck.relevant(query, hits)) {
                note = "response failed the relevance check (query terms absent)"
                        + " — likely a bot-block page";
                hits = List.of();
            }
            kept.add(hits.size());
            reports.add(new EngineReport(engine.name(), hits.size(),
                    System.currentTimeMillis() - start, note));
            return hits;
        };

        long start = System.currentTimeMillis();
        SearchService service = new SearchService(concurrency, reportingFetcher, guarded);
        List<Future<?>> running = service.search(engines, query, 1, collector);
        service.awaitAll(running, collector);
        service.shutdown();

        int raw = kept.stream().mapToInt(Integer::intValue).sum();
        return new Outcome(collector.results(), inEngineOrder(reports), raw,
                Math.max(0, raw - collector.count()), System.currentTimeMillis() - start);
    }

    /** Reports in engine order, so the UI reads the same way on every run. */
    private static List<EngineReport> inEngineOrder(List<EngineReport> reports) {
        List<EngineReport> ordered = new ArrayList<>();
        for (Engine engine : LiveEngines.engines()) {
            for (EngineReport report : reports) {
                if (report.engine().equals(engine.name())) {
                    ordered.add(report);
                }
            }
        }
        return ordered;
    }

    /** The engine a URL belongs to, by the fixed prefix before the query slot. */
    private static String engineFor(String url) {
        for (Engine engine : LiveEngines.engines()) {
            String prefix = engine.urlTemplate().substring(0, engine.urlTemplate().indexOf('^'));
            if (url.startsWith(prefix)) {
                return engine.name();
            }
        }
        return "?";
    }

    private static String describe(Exception failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private LiveSearch() {
    }
}
