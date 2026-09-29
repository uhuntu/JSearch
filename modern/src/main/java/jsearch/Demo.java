package jsearch;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * A runnable demonstration of the reference design. No network, no applet, no
 * browser: the fetcher serves canned HTML, so this runs anywhere a JVM runs.
 *
 * <p>It exercises the three properties that the original design got wrong:
 * <ol>
 *   <li>two engines may share a URL without one destroying the other</li>
 *   <li>a genuinely duplicated engine is rejected loudly instead of silently</li>
 *   <li>deduplication and counting hold when engines finish concurrently</li>
 * </ol>
 */
public final class Demo {

    /** The exact shape that broke the original: two engines, one URL. */
    private static final String ENGINE_FILE = String.join("\n",
            "http://www.google.com/",
            "Google / Google",
            "English",
            "http://www.google.com/search?q=^&start=`0",
            "<r>",
            "</r>",
            "",
            "http://www.google.com/",
            "GB_Chinese Google",
            "Chinese",
            "http://www.google.com/search?q=^&lr=lang_zh-CN&start=`0",
            "<r>",
            "</r>",
            "");

    /** Three results, one URL appearing twice, to exercise dedup. */
    private static final String CANNED_PAGE = String.join("\n",
            "<html><body>",
            "<r>ref=\"http://example.com/a\"><b>First result</b></a>",
            "the first preview text</r>",
            "<r>ref=\"http://example.com/b\"><b>Second result</b></a>",
            "the second preview text</r>",
            "<r>ref=\"http://example.com/a\"><b>First result again</b></a>",
            "a duplicate url, must be dropped</r>",
            "</body></html>");

    public static void main(String[] args) throws Exception {
        partOne_enginesThatShareAUrl();
        partTwo_duplicateIdentityIsRejected();
        partThree_concurrentDedupAndCount();

        System.out.println();
        System.out.println("all demonstrations completed");
    }

    private static void partOne_enginesThatShareAUrl() {
        System.out.println("== 1. two engines sharing one URL ==");
        List<Engine> engines = EngineRepository.parse(ENGINE_FILE);
        System.out.println("   parsed " + engines.size() + " engines from a file that "
                + "gave the original 1 (last write wins on the URL key)");

        Map<String, Integer> byUrl = new HashMap<>();
        engines.forEach(engine -> byUrl.merge(engine.urlTemplate(), 1, Integer::sum));
        System.out.println("   distinct url templates: " + byUrl.size()
                + "  (both survive, because URL is not the identity)");
        engines.forEach(engine -> System.out.println("     - " + engine));
        System.out.println();
    }

    private static void partTwo_duplicateIdentityIsRejected() {
        System.out.println("== 2. a genuine duplicate is rejected, not overwritten ==");
        String duplicated = ENGINE_FILE + String.join("\n",
                "http://www.google.com/",
                "Google / Google",
                "English",
                "http://www.google.com/search?q=^&start=`1",
                "<r>",
                "</r>",
                "");
        try {
            EngineRepository.parse(duplicated);
            System.out.println("   FAILED: the duplicate was accepted");
        } catch (IllegalArgumentException expected) {
            System.out.println("   rejected loudly: " + expected.getMessage());
        }
        System.out.println();
    }

    private static void partThree_concurrentDedupAndCount() throws Exception {
        System.out.println("== 3. dedup and count under concurrency ==");
        List<Engine> engines = EngineRepository.parse(ENGINE_FILE);
        ResultCollector collector = new ResultCollector();
        collector.onResult(result -> System.out.println(
                "     + " + result.title() + "  <" + result.url() + ">  \"" + result.preview() + "\""));

        // Two engines, two levels each -> four tasks, all returning the same page.
        SearchService service = new SearchService(4, url -> CANNED_PAGE, new HtmlBlockScraper());
        List<Future<?>> running = service.search(engines, "西二在线", 2, collector);
        service.awaitAll(running, collector);
        service.shutdown();

        System.out.println("   distinct results recorded: " + collector.count());
        System.out.println("   expected 2 (three blocks parsed, one duplicate url dropped)");
        System.out.println("   first url: " + collector.results().get(0).url());
        System.out.println();
    }

    private Demo() {
    }
}
