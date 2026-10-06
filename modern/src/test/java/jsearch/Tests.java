package jsearch;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static int skipped;
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
        test("end marker inside a tag is not skipped", Tests::endMarkerInsideTag);

        // ResultCollector
        test("dedup by URL", Tests::collectorDedups);
        test("concurrent adds of the same URLs record each once", Tests::collectorConcurrentDedup);
        test("listener fires once per new result only", Tests::collectorListenerOncePerNew);

        // SearchService
        test("service dedups across engines and levels", Tests::serviceDedups);
        test("one failing engine does not sink the others", Tests::serviceIsolatesFailure);
        test("cancel interrupts a blocked fetch", Tests::serviceCancelInterrupts);

        // JSON API path: Json, JsonApiScraper, HttpPageFetcher
        test("brave-shaped JSON is scraped, tags stripped", Tests::braveJson);
        test("searxng-shaped JSON is scraped", Tests::searxngJson);
        test("JSON with no results yields empty list", Tests::jsonNoResults);
        test("non-JSON body throws instead of returning nothing", Tests::jsonGarbageThrows);
        test("JSON escapes and unicode decode", Tests::jsonEscapes);
        test("http fetcher returns body and sends scoped headers", Tests::httpFetcherHeaders);
        test("http fetcher rejects non-2xx", Tests::httpFetcherRejectsError);
        test("cancel interrupts a real in-flight HTTP request", Tests::httpFetcherCancel);

        // WebServer & REST API
        test("web server serves HTML UI and health API", Tests::webServerHealthAndUi);
        test("web server provides engines list", Tests::webServerEnginesApi);
        test("web server executes search query", Tests::webServerSearchApi);
        test("web server compare API detects legacy URL collision defect", Tests::webServerCompareApi);
        testArchive("web server serves a captured page only for the query it captured",
                Tests::webServerFixturesOnlyForTheirCapturedQuery);

        // The archive's own captured pages, as fixtures
        testArchive("shipped engine file yields three engines, not two", Tests::shippedEnginesSurvive);
        testArchive("shipped markers are the ones the analysis documents", Tests::shippedMarkers);
        testArchive("google_en.html yields the ten results in page order", Tests::googleEnPage);
        testArchive("the shipped markers also catch the pager", Tests::pagerIsScraped);
        testArchive("google_cn.html decodes as GBK, not mojibake", Tests::gbkDecodes);
        testArchive("baidu_cn.html yields all ten results", Tests::baiduPage);
        testArchive("baidu's end marker only ever appears inside a tag", Tests::baiduMarkerOnlyInTag);
        testArchive("lycos_en.html was captured but never shipped", Tests::lycosOrphaned);

        // The encoding the archive is preserved in
        testArchive("a UTF-8 copy of JSEngines.txt is caught", Tests::conversionIsCaught);
        testArchive("original files are still GBK and still present", Tests::originalFilesAreGbk);
        testArchive("modern sources and Markdown docs are UTF-8", Tests::archivalFilesAreUtf8);
        testArchive("every original text file has an encoding expectation", Tests::noUnlistedFiles);

        System.out.println();
        System.out.println(passed + " passed, " + failures.size() + " failed"
                + (skipped == 0 ? "" : ", " + skipped + " skipped"));
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

    private static void endMarkerInsideTag() {
        // Baidu's shipped end marker "ble>" only ever occurs as part of
        // "</table>". Stepping over a tag to its '>' loses it: block one then
        // runs to the end of the page and the second block is never seen.
        String html = "Xref=http://a>A</a>one</table>Xref=http://b>B</a>two</table>end";
        List<SearchResult> results = SCRAPER.scrape(engine("X", "ble>"), html);
        check(results.size() == 2, "expected 2, got " + results.size());
        check(results.get(0).preview().equals("one"), results.get(0).preview());
        check(results.get(1).url().equals("http://b"), results.get(1).url());
        check(results.get(1).preview().equals("two"), results.get(1).preview());
    }

    // ---- Archive fixtures ------------------------------------------------

    private static void shippedEnginesSurvive() {
        List<Engine> engines = ArchiveFixtures.shippedEngines();
        check(engines.size() == 3, "expected 3, got " + engines.size());
        // The original keyed on the URL, and two of these three share one, so it
        // loaded 2 from this very file.
        check(ArchiveFixtures.shippedEngine("Google").category().equals("English"), "English Google");
        check(ArchiveFixtures.shippedEngine("GB_Chinese").category().equals("Chinese"), "Chinese Google");
        check(ArchiveFixtures.shippedEngine("Baidu").category().equals("Chinese"), "Baidu");
    }

    private static void shippedMarkers() {
        Engine english = ArchiveFixtures.shippedEngine("Google");
        Engine chinese = ArchiveFixtures.shippedEngine("GB_Chinese");
        Engine baidu = ArchiveFixtures.shippedEngine("Baidu");
        check(english.resultBlock().start().equals("<p><"), english.resultBlock().toString());
        check(english.resultBlock().end().equals("k - "), english.resultBlock().toString());
        check(chinese.resultBlock().start().equals("<p><"), chinese.resultBlock().toString());
        check(chinese.resultBlock().end().equals("k - "), chinese.resultBlock().toString());
        check(baidu.resultBlock().start().equals(".</d"), baidu.resultBlock().toString());
        check(baidu.resultBlock().end().equals("ble>"), baidu.resultBlock().toString());
    }

    private static void googleEnPage() {
        List<SearchResult> results = SCRAPER.scrape(
                ArchiveFixtures.shippedEngine("Google"), ArchiveFixtures.page("google_en.html"));
        check(results.size() == 11, "expected 11 (ten results + the pager), got " + results.size());
        String[] urls = {
            "http://sun.com/java/",
            "http://java.apache.org/",
            "http://www.java-pro.com/",
            "http://javascript.internet.com/",
            "http://www.microsoft.com/java/",
            "http://developer.java.sun.com/developer/",
            "http://www.javaarchives.com/",
            "http://java.about.com/",
            "http://www.ibiblio.org/javafaq/javafaq.html",
            "http://www.anfyteam.com/",
        };
        for (int i = 0; i < urls.length; i++) {
            check(results.get(i).url().equals(urls[i]), "result " + (i + 1) + ": " + results.get(i).url());
            check(!results.get(i).title().isEmpty(), "result " + (i + 1) + " has no title");
            check(!results.get(i).preview().isEmpty(), "result " + (i + 1) + " has no preview");
        }
    }

    private static void pagerIsScraped() {
        // "<p><" also matches the pagination table, so Google's "上一页" link is
        // scraped as a result. Same markers, same algorithm, same false positive
        // as the original: recorded here so the behaviour is known, not hidden.
        List<SearchResult> results = SCRAPER.scrape(
                ArchiveFixtures.shippedEngine("Google"), ArchiveFixtures.page("google_en.html"));
        SearchResult last = results.get(results.size() - 1);
        check(last.url().equals("/search?q=java&hl=zh-CN&start=0&sa=N"), last.url());
        check(last.title().equals("上一页"), "pager title was " + last.title());
    }

    private static void gbkDecodes() {
        List<SearchResult> results = SCRAPER.scrape(
                ArchiveFixtures.shippedEngine("GB_Chinese"), ArchiveFixtures.page("google_cn.html"));
        check(results.size() == 11, "expected 11, got " + results.size());
        SearchResult first = results.get(0);
        check(first.url().equals("http://search.gznet.com/dir/11/02/04/1.html"), first.url());
        check(first.title().equals("广州视窗搜索引擎"),
                "mojibake, i.e. the page was not decoded as GBK: " + first.title());
        check(first.preview().contains("网上远程诊断与处理支持中心"), first.preview());
        check(results.get(10).title().equals("上一页"), results.get(10).title());
    }

    private static void baiduPage() {
        List<SearchResult> results = SCRAPER.scrape(
                ArchiveFixtures.shippedEngine("Baidu"), ArchiveFixtures.page("baidu_cn.html"));
        check(results.size() == 10, "expected 10, got " + results.size());
        check(results.get(0).url().equals("http://bbs.lstc.edu.cn/~jingsh/chaojilianjie.htm"),
                results.get(0).url());
        check(results.get(0).title().equals("超级连接"), results.get(0).title());
        for (SearchResult result : results) {
            check(result.url().startsWith("http"), "no url: [" + result.url() + "]");
            check(!result.title().isEmpty(), "no title for " + result.url());
        }
    }

    private static void baiduMarkerOnlyInTag() {
        // Evidence for readPreview(): every "ble>" in the Baidu page is the tail
        // of "</table>", so a scraper that steps over tags can never end a block.
        String html = ArchiveFixtures.page("baidu_cn.html");
        check(count(html, "ble>") == count(html, "</table>"),
                "ble> appears " + count(html, "ble>") + " times, </table> " + count(html, "</table>"));
    }

    private static void lycosOrphaned() {
        // Four pages were captured; the shipped engine file defines three engines
        // and none of them is Lycos, so this page has no markers at all.
        for (Engine engine : ArchiveFixtures.shippedEngines()) {
            check(!engine.urlTemplate().contains("lycos"), "Lycos was shipped after all: " + engine);
        }
    }

    // ---- EncodingGuard ----------------------------------------------------

    // Each of these is gated on the archive being present, which is also the
    // condition under which EncodingGuard has anything to look at.

    private static EncodingGuard guard() {
        return EncodingGuard.open()
                .orElseThrow(() -> new AssertionError("archive root not found"));
    }

    /**
     * The check is only worth having if it can see a conversion, so this one
     * performs the damage in memory: decode the shipped engine file as GBK,
     * re-encode it as UTF-8, and require that the guard no longer accepts it.
     */
    private static void conversionIsCaught() throws IOException {
        Charset gbk = Charset.forName("GBK");
        Path path = ArchiveFixtures.root().get().resolve("Releases").resolve("JSEngines.txt");
        byte[] original = Files.readAllBytes(path);
        check(EncodingGuard.classify(original, gbk) == EncodingGuard.Kind.GBK,
                "the shipped file should classify as GBK");
        byte[] converted = new String(original, gbk).getBytes(StandardCharsets.UTF_8);
        check(EncodingGuard.classify(converted, gbk) == EncodingGuard.Kind.UTF8,
                "a UTF-8 copy read as "
                        + EncodingGuard.classify(converted, gbk)
                        + ", so the guard would let the damage through");
    }

    private static void originalFilesAreGbk() {
        List<String> problems = guard().checkArchive();
        check(problems.isEmpty(), String.join("; ", problems));
    }

    private static void archivalFilesAreUtf8() {
        List<String> problems = guard().checkUtf8Files();
        check(problems.isEmpty(), String.join("; ", problems));
    }

    private static void noUnlistedFiles() {
        List<String> unlisted = guard().unlistedFiles();
        check(unlisted.isEmpty(), "unclassified original files: " + String.join("; ", unlisted));
    }

    private static int count(String text, String needle) {
        int n = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
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

    // ---- JSON API path ---------------------------------------------------

    private static final Engine API = new Engine("Api", "API", "http://api/?q=^", new Engine.Block("{", "}"));

    private static void braveJson() {
        String body = "{\"web\":{\"results\":[{\"url\":\"http://a\",\"title\":\"A <strong>hit</strong>\","
                + "\"description\":\"first\"},{\"title\":\"no url\"},{\"url\":\"http://b\",\"title\":\"B\"}]}}";
        List<SearchResult> r = JsonApiScraper.brave().scrape(API, body);
        check(r.size() == 2, "item without url must be skipped, got " + r.size());
        check(r.get(0).title().equals("A hit"), "tags not stripped: " + r.get(0).title());
        check(r.get(0).preview().equals("first"), "preview");
        check(r.get(1).preview().isEmpty(), "missing description should be empty");
    }

    private static void searxngJson() {
        String body = "{\"query\":\"q\",\"results\":[{\"url\":\"http://a\",\"title\":\"T\",\"content\":\"C\"}]}";
        List<SearchResult> r = JsonApiScraper.searxng().scrape(API, body);
        check(r.size() == 1 && r.get(0).preview().equals("C"), "searxng content field");
    }

    private static void jsonNoResults() {
        check(JsonApiScraper.brave().scrape(API, "{}").isEmpty(), "{}");
        check(JsonApiScraper.brave().scrape(API, "{\"web\":{}}").isEmpty(), "web without results");
        check(JsonApiScraper.brave().scrape(API, "[]").isEmpty(), "array root");
    }

    private static void jsonGarbageThrows() {
        expectThrows(IllegalArgumentException.class, () -> JsonApiScraper.brave().scrape(API, "<html>rate limited</html>"));
        expectThrows(IllegalArgumentException.class, () -> JsonApiScraper.brave().scrape(API, "{\"web\":"));
        expectThrows(IllegalArgumentException.class, () -> JsonApiScraper.brave().scrape(API, "{} extra"));
    }

    private static void jsonEscapes() {
        Object v = Json.parse("{\"s\":\"a\\\"b\\n\\u4e2d\",\"n\":-1.5e2, \"t\":true, \"z\":null}");
        java.util.Map<?, ?> m = (java.util.Map<?, ?>) v;
        check(m.get("s").equals("a\"b\n中"),"string escapes: " + m.get("s"));
        check(m.get("n").equals(-150.0), "number");
        check(Boolean.TRUE.equals(m.get("t")) && m.containsKey("z") && m.get("z") == null, "literals");
    }

    private static com.sun.net.httpserver.HttpServer server(
            com.sun.net.httpserver.HttpHandler handler) throws IOException {
        com.sun.net.httpserver.HttpServer s = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        s.createContext("/", handler);
        s.start();
        return s;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static void httpFetcherHeaders() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        com.sun.net.httpserver.HttpServer s = server(ex -> {
            seen.set(ex.getRequestHeaders().getFirst("X-Token") + "|" + ex.getRequestURI().getRawQuery());
            reply(ex, 200, "{\"ok\":\"中\"}");
        });
        try {
            String base = "http://127.0.0.1:" + s.getAddress().getPort();
            HttpPageFetcher f = new HttpPageFetcher().withHeader(base, "X-Token", "secret");
            check(f.fetch(base + "/x?q=a%20b").contains("中"), "body must decode as UTF-8");
            check(seen.get().equals("secret|q=a%20b"), "scoped header/query: " + seen.get());
            // a different prefix must not receive the token
            HttpPageFetcher other = new HttpPageFetcher().withHeader("http://elsewhere.invalid", "X-Token", "secret");
            other.fetch(base + "/x");
            check(seen.get().startsWith("null|"), "token leaked to unrelated URL: " + seen.get());
        } finally {
            s.stop(0);
        }
    }

    private static void httpFetcherRejectsError() throws Exception {
        com.sun.net.httpserver.HttpServer s = server(ex -> reply(ex, 429, "slow down"));
        try {
            String msg = expectThrows(IOException.class,
                    () -> new HttpPageFetcher().fetch("http://127.0.0.1:" + s.getAddress().getPort() + "/"));
            check(msg.contains("429"), "status missing from message: " + msg);
        } finally {
            s.stop(0);
        }
    }

    private static void httpFetcherCancel() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        com.sun.net.httpserver.HttpServer s = server(ex -> {
            entered.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // server shutting down
            }
            ex.close();
        });
        SearchService service = new SearchService(1, new HttpPageFetcher(), SCRAPER);
        try {
            String url = "http://127.0.0.1:" + s.getAddress().getPort() + "/?q=^";
            ResultCollector collector = new ResultCollector();
            List<Future<?>> futures = service.search(
                    EngineRepository.parse(record("k", "A", "C", url)), "q", 1, collector);
            check(entered.await(5, TimeUnit.SECONDS), "request never reached the server");
            long start = System.nanoTime();
            service.cancelAll(futures);
            service.shutdown();   // waits up to 2s for the worker to exit
            long ms = (System.nanoTime() - start) / 1_000_000;
            check(ms < 1500, "worker stayed blocked after cancel: " + ms + "ms");
        } finally {
            release.countDown();
            s.stop(0);
        }
    }

    // ---- WebServer -------------------------------------------------------

    private static void webServerHealthAndUi() throws Exception {
        try (WebServer ws = new WebServer(0)) {
            ws.start();
            HttpPageFetcher f = new HttpPageFetcher();
            String base = "http://127.0.0.1:" + ws.getPort();
            String ui = f.fetch(base + "/");
            check(ui.contains("JSearch") && ui.contains("Turns Search Engines into FIND Engines"), "UI page missing title/content");
            String health = f.fetch(base + "/api/health");
            check(health.contains("\"status\":\"ok\""), "health API returned: " + health);
        }
    }

    private static void webServerEnginesApi() throws Exception {
        try (WebServer ws = new WebServer(0)) {
            ws.start();
            HttpPageFetcher f = new HttpPageFetcher();
            String json = f.fetch("http://127.0.0.1:" + ws.getPort() + "/api/engines");
            check(json.contains("Google") && json.contains("Baidu"), "engines API missing key engines: " + json);
        }
    }

    private static void webServerSearchApi() throws Exception {
        try (WebServer ws = new WebServer(0)) {
            ws.start();
            HttpPageFetcher f = new HttpPageFetcher();
            String json = f.fetch("http://127.0.0.1:" + ws.getPort() + "/api/search?q=java");
            check(json.contains("\"totalUnique\":") && json.contains("\"results\":"), "search API structure error: " + json);
            check(!json.contains("\"totalUnique\":0"), "search API should return results for 'java'");
        }
    }

    private static void webServerCompareApi() throws Exception {
        try (WebServer ws = new WebServer(0)) {
            ws.start();
            HttpPageFetcher f = new HttpPageFetcher();
            String json = f.fetch("http://127.0.0.1:" + ws.getPort() + "/api/compare?q=java");
            check(json.contains("\"defectExplanation\":") && json.contains("\"lostEngines\":"), "compare API missing defect fields");
            check(json.contains("\"legacy\":") && json.contains("\"modern\":"), "compare API missing legacy/modern fields");
        }
    }

    /**
     * A captured page answers the query it was captured for, and only that one.
     *
     * <p>{@code fetchPageContent} used to return the 2001 Google capture for
     * whatever was asked, because it never looked at the query at all — its own
     * comment said the fixtures were for "java". So a search for "applet" came
     * back with ten 2001 links to sun.com, java.apache.org and Microsoft, and the
     * UI presented them as an answer to "applet".
     */
    private static void webServerFixturesOnlyForTheirCapturedQuery() throws Exception {
        try (WebServer ws = new WebServer(0)) {
            ws.start();
            HttpPageFetcher f = new HttpPageFetcher();
            String base = "http://127.0.0.1:" + ws.getPort();

            // The captured query still gets the real 2001 page.
            String java = f.fetch(base + "/api/search?q=java&engines=Google");
            check(java.contains("java-pro.com"),
                    "the 2001 Google capture should still answer 'java': " + java);

            // Any other query must not be answered with the capture. Note that
            // sun.com/java is deliberately NOT the probe here: the synthetic
            // pages reuse it as their deduplication example, so it appears in
            // both. java-pro.com exists only in the genuine capture.
            String other = f.fetch(base + "/api/search?q=" + encode("ZZQQXX-unicorn-9271") + "&engines=Google");
            check(!other.contains("java-pro.com") && !other.contains("javaarchives"),
                    "the 2001 Google capture was served for an unrelated query: " + other);
            check(other.contains("ZZQQXX-unicorn-9271"),
                    "results should be about the query actually asked: " + other);
        }
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
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

    /** Runs a test that needs the archive's own files; skips if they are absent. */
    private static void testArchive(String name, Body body) {
        if (!ArchiveFixtures.available()) {
            skipped++;
            System.out.println("  skip " + name + " (" + ArchiveFixtures.unavailableReason() + ")");
            return;
        }
        test(name, body);
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
