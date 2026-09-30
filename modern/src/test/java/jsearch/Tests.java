package jsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dependency-free tests: no JUnit, so they run on a bare JDK like everything else
 * here. Each test is a named lambda; any exception or failed check fails it, and
 * the process exits non-zero if any test fails.
 */
public final class Tests {

    private interface Body {
        void run() throws Exception;
    }

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // EngineRepository
        test("two engines sharing a URL both survive", Tests::sharedUrlBothSurvive);
        test("duplicate identity is rejected with both line numbers", Tests::duplicateIdentityRejected);
        test("truncated record is rejected", Tests::truncatedRecordRejected);
        test("empty file is rejected", Tests::emptyFileRejected);
        test("CRLF line endings parse like LF", Tests::crlfParses);

        // Engine
        test("identity ignores URL", Tests::identityIgnoresUrl);
        test("urlFor substitutes query and level", Tests::urlForSubstitutes);
        test("blank engine fields are rejected", Tests::blankFieldsRejected);

        // HtmlBlockScraper
        test("scrapes blocks in document order", Tests::scrapesInOrder);
        test("markers shorter than four characters match", Tests::shortMarkersMatch);
        test("page with no blocks yields empty list", Tests::noBlocksIsEmpty);
        test("truncated block terminates and does not throw", Tests::truncatedBlockTerminates);

        // ResultCollector
        test("dedup by URL", Tests::collectorDedups);
        test("concurrent adds of the same URLs record each once", Tests::collectorConcurrentDedup);
        test("listener fires once per new result only", Tests::collectorListenerOncePerNew);

        // SearchService
        test("service dedups across engines and levels", Tests::serviceDedups);
        test("one failing engine does not sink the others", Tests::serviceIsolatesFailure);
        test("cancel interrupts a blocked fetch", Tests::serviceCancelInterrupts);

        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("  FAIL " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ---- EngineRepository ------------------------------------------------

    private static String record(String key, String name, String category, String url) {
        return String.join("\n", key, name, category, url, "<r>", "</r>", "");
    }

    private static void sharedUrlBothSurvive() {
        String file = record("http://www.google.com/", "Google", "English", "http://g/?q=^")
                + record("http://www.google.com/", "GB_Chinese Google", "Chinese", "http://g/?q=^&zh");
        List<Engine> engines = EngineRepository.parse(file);
        check(engines.size() == 2, "expected 2 engines, got " + engines.size());
        check(engines.get(0).name().equals("Google"), "first engine");
        check(engines.get(1).name().equals("GB_Chinese Google"), "second engine");
    }

    private static void duplicateIdentityRejected() {
        String file = record("k1", "Google", "English", "http://g/1")
                + record("k2", "Google", "English", "http://g/2");
        String message = expectThrows(IllegalArgumentException.class, () -> EngineRepository.parse(file));
        check(message.contains("duplicate"), message);
        check(message.contains("lines 1 and 7"), "line numbers missing: " + message);
    }

    private static void truncatedRecordRejected() {
        String message = expectThrows(IllegalArgumentException.class,
                () -> EngineRepository.parse("key\nname\ncategory\n"));
        check(message.contains("truncated"), message);
    }

    private static void emptyFileRejected() {
        expectThrows(IllegalArgumentException.class, () -> EngineRepository.parse("\n\n"));
    }

    private static void crlfParses() {
        String lf = record("k", "Name", "Cat", "http://x/?q=^");
        List<Engine> viaCrlf = EngineRepository.parse(lf.replace("\n", "\r\n"));
        check(viaCrlf.size() == 1, "size");
        check(viaCrlf.get(0).urlTemplate().equals("http://x/?q=^"), "carriage return leaked into url");
        check(viaCrlf.get(0).resultBlock().end().equals("</r>"), "carriage return leaked into marker");
    }

    // ---- Engine ----------------------------------------------------------

    private static Engine engine(String name, String category, String url, String start, String end) {
        return new Engine(name, category, url, new Engine.Block(start, end));
    }

    private static Engine engine(String start, String end) {
        return engine("E", "C", "http://e/?q=^", start, end);
    }

    private static void identityIgnoresUrl() {
        Engine a = engine("Google", "English", "http://x/1", "<r>", "</r>");
        Engine b = engine("Google", "English", "http://x/2", "<a>", "</a>");
        Engine c = engine("Google", "Chinese", "http://x/1", "<r>", "</r>");
        check(a.equals(b) && a.hashCode() == b.hashCode(), "same name+category must be equal");
        check(!a.equals(c), "different category must differ even with the same URL");
    }

    private static void urlForSubstitutes() {
        Engine e = engine("E", "C", "http://e/?q=^&start=`0", "<r>", "</r>");
        check(e.urlFor("a b", 3).equals("http://e/?q=a+b&start=30"), e.urlFor("a b", 3));
        check(e.urlFor("西", 0).equals("http://e/?q=%E8%A5%BF&start=00"), e.urlFor("西", 0));
    }

    private static void blankFieldsRejected() {
        expectThrows(IllegalArgumentException.class, () -> engine(" ", "C", "u", "<r>", "</r>"));
        expectThrows(IllegalArgumentException.class, () -> engine("N", "", "u", "<r>", "</r>"));
        expectThrows(IllegalArgumentException.class, () -> engine("N", "C", "  ", "<r>", "</r>"));
    }

    // ---- HtmlBlockScraper ------------------------------------------------

    private static final BlockScraper SCRAPER = new HtmlBlockScraper();

    private static String block(String url, String title, String preview) {
        return "<r>ref=\"" + url + "\"><b>" + title + "</b></a>" + preview + "</r>";
    }

    private static void scrapesInOrder() {
        String html = "<html>" + block("http://a", "First", "pa") + "junk"
                + block("http://b", "Second", "pb") + "</html>";
        List<SearchResult> results = SCRAPER.scrape(engine("<r>", "</r>"), html);
        check(results.size() == 2, "expected 2, got " + results.size());
        check(results.get(0).url().equals("http://a"), results.get(0).url());
        check(results.get(0).title().equals("First"), results.get(0).title());
        check(results.get(0).preview().equals("pa"), results.get(0).preview());
        check(results.get(1).url().equals("http://b"), results.get(1).url());
    }

    private static void shortMarkersMatch() {
        // The original's hard-wired four-character window could never match these.
        String html = "<p>ref=http://a>T</a>pv</p>";
        List<SearchResult> results = SCRAPER.scrape(engine("<p>", "</p>"), html);
        check(results.size() == 1, "3-char markers: expected 1, got " + results.size());
        html = "Xref=http://a>T</a>pvY";
        results = SCRAPER.scrape(engine("X", "Y"), html);
        check(results.size() == 1, "1-char markers: expected 1, got " + results.size());
    }

    private static void noBlocksIsEmpty() {
        check(SCRAPER.scrape(engine("<r>", "</r>"), "<html>nothing here</html>").isEmpty(), "not empty");
        check(SCRAPER.scrape(engine("<r>", "</r>"), "").isEmpty(), "empty input not empty");
    }

    private static void truncatedBlockTerminates() {
        // Each of these ends mid-block; the scraper must return, not spin or throw.
        String[] truncated = {
            "<r>", "<r>ref=", "<r>ref=\"http://a", "<r>ref=\"http://a\"><b>Ti", "<r>ref=http://a>T</a>pre",
        };
        for (String html : truncated) {
            final String page = html;
            assertCompletesWithin(2000, () -> SCRAPER.scrape(engine("<r>", "</r>"), page));
        }
    }

    // ---- ResultCollector -------------------------------------------------

    private static SearchResult result(String url) {
        return new SearchResult(engine("<r>", "</r>"), url, "t", "p");
    }

    private static void collectorDedups() {
        ResultCollector c = new ResultCollector();
        check(c.add(result("http://a")), "first add must be new");
        check(!c.add(result("http://a")), "second add must be a duplicate");
        check(c.add(result("http://b")), "different url must be new");
        check(c.count() == 2, "count " + c.count());
        check(c.contains("http://a") && !c.contains("http://z"), "contains");
    }

    private static void collectorConcurrentDedup() throws Exception {
        final int threads = 8;
        final int urls = 500;
        ResultCollector c = new ResultCollector();
        AtomicInteger accepted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < urls; i++) {
                    if (c.add(result("http://example.com/" + i))) {
                        accepted.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();
        check(c.count() == urls, "count " + c.count() + ", expected " + urls);
        check(accepted.get() == urls, "add() reported new " + accepted.get() + " times, expected " + urls);
        check(c.results().size() == urls, "results().size()");
    }

    private static void collectorListenerOncePerNew() {
        ResultCollector c = new ResultCollector();
        AtomicInteger fired = new AtomicInteger();
        c.onResult(r -> fired.incrementAndGet());
        c.add(result("http://a"));
        c.add(result("http://a"));
        c.add(result("http://b"));
        check(fired.get() == 2, "listener fired " + fired.get() + " times");
    }

    // ---- SearchService ---------------------------------------------------

    private static final String ONE_RESULT_PAGE = block("http://same", "T", "p");

    private static void serviceDedups() throws Exception {
        List<Engine> engines = EngineRepository.parse(
                record("k", "A", "C", "http://a/?q=^&s=`0") + record("k", "B", "C", "http://b/?q=^&s=`0"));
        SearchService service = new SearchService(3, url -> ONE_RESULT_PAGE, SCRAPER);
        try {
            ResultCollector collector = new ResultCollector();
            AtomicInteger completed = new AtomicInteger();
            collector.onComplete(completed::incrementAndGet);
            List<Future<?>> futures = service.search(engines, "q", 2, collector);
            check(futures.size() == 4, "2 engines x 2 levels = 4 tasks, got " + futures.size());
            service.awaitAll(futures, collector);
            check(collector.count() == 1, "4 pages, one URL: count " + collector.count());
            check(completed.get() == 1, "onComplete fired " + completed.get() + " times");
        } finally {
            service.shutdown();
        }
    }

    private static void serviceIsolatesFailure() throws Exception {
        List<Engine> engines = EngineRepository.parse(
                record("k", "Bad", "C", "http://bad/?q=^") + record("k", "Good", "C", "http://good/?q=^"));
        PageFetcher fetcher = url -> {
            if (url.startsWith("http://bad/")) {
                throw new IOException("boom");
            }
            return ONE_RESULT_PAGE;
        };
        SearchService service = new SearchService(2, fetcher, SCRAPER);
        try {
            ResultCollector collector = new ResultCollector();
            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicInteger completed = new AtomicInteger();
            collector.onError(error::set);
            collector.onComplete(completed::incrementAndGet);
            service.awaitAll(service.search(engines, "q", 1, collector), collector);
            check(collector.count() == 1, "good engine's result lost: count " + collector.count());
            check(error.get() instanceof IOException, "error not reported: " + error.get());
            check(completed.get() == 1, "completion not fired after a failure");
        } finally {
            service.shutdown();
        }
    }

    private static void serviceCancelInterrupts() throws Exception {
        List<Engine> engines = EngineRepository.parse(record("k", "A", "C", "http://a/?q=^"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        PageFetcher blocking = url -> {
            entered.countDown();
            try {
                Thread.sleep(60_000);           // stands in for a blocked socket read
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw new IOException("interrupted", e);
            }
            return "";
        };
        SearchService service = new SearchService(1, blocking, SCRAPER);
        try {
            ResultCollector collector = new ResultCollector();
            List<Future<?>> futures = service.search(engines, "q", 1, collector);
            check(entered.await(5, TimeUnit.SECONDS), "fetch never started");
            service.cancelAll(futures);
            check(interrupted.await(5, TimeUnit.SECONDS), "blocked fetch was not interrupted");
            service.awaitAll(futures, collector);   // cancelled is not an error; must not hang
        } finally {
            service.shutdown();
        }
    }

    // ---- harness ---------------------------------------------------------

    private static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  ok   " + name);
        } catch (Throwable t) {
            failures.add(name + ": " + t);
            System.out.println("  FAIL " + name + ": " + t);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String expectThrows(Class<? extends Throwable> type, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return String.valueOf(t.getMessage());
            }
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private static void assertCompletesWithin(long millis, Runnable work) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> f = pool.submit(work);
            try {
                f.get(millis, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                throw new AssertionError("did not terminate within " + millis + "ms");
            } catch (java.util.concurrent.ExecutionException e) {
                throw new AssertionError("threw " + e.getCause(), e.getCause());
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
