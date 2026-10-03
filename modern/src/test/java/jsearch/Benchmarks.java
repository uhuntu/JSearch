package jsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performance benchmarks for the reference design.
 * Run with: java -cp out jsearch.Benchmarks
 */
public final class Benchmarks {

    private static final int WARMUP = 1000;
    private static final int ITERATIONS = 10000;

    public static void main(String[] args) {
        System.out.println("JSearch Performance Benchmarks");
        System.out.println("==============================");
        System.out.println();

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            benchmarkEngineParse();
            benchmarkResultCollector();
            benchmarkScraper();
        }

        // Benchmarks
        bench("EngineRepository.parse()", Benchmarks::benchmarkEngineParse);
        bench("ResultCollector.add() x100", Benchmarks::benchmarkResultCollector);
        bench("HtmlBlockScraper.scrape()", Benchmarks::benchmarkScraper);
        bench("Engine.urlFor()", Benchmarks::benchmarkUrlFor);

        System.out.println();
        System.out.println("Note: These are microbenchmarks. Real-world performance");
        System.out.println("depends on network I/O, which dominates search latency.");
    }

    private static void bench(String name, Runnable task) {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            task.run();
        }
        long elapsed = System.nanoTime() - start;
        double ms = elapsed / 1_000_000.0;
        double perOp = ms / ITERATIONS;
        System.out.printf("%-30s %8.2f ms total, %6.4f ms/op%n", name, ms, perOp);
    }

    private static void benchmarkEngineParse() {
        String file = String.join("\n",
            "http://google.com/", "Google", "English", "http://g/?q=^", "<r>", "</r>", "",
            "http://baidu.com/", "Baidu", "Chinese", "http://b/?w=^", "<r>", "</r>", ""
        );
        EngineRepository.parse(file);
    }

    private static void benchmarkResultCollector() {
        ResultCollector c = new ResultCollector();
        Engine e = new Engine("E", "C", "http://e/?q=^", new Engine.Block("<r>", "</r>"));
        for (int i = 0; i < 100; i++) {
            c.add(new SearchResult(e, "http://example.com/" + i, "Title " + i, "Preview " + i));
        }
    }

    private static void benchmarkScraper() {
        BlockScraper scraper = new HtmlBlockScraper();
        Engine e = new Engine("E", "C", "http://e/?q=^", new Engine.Block("<r>", "</r>"));
        String html = "<r>ref=\"http://a\"><b>Title</b></a>Preview</r>".repeat(10);
        scraper.scrape(e, html);
    }

    private static void benchmarkUrlFor() {
        Engine e = new Engine("E", "C", "http://e/?q=^&start=`0", new Engine.Block("<r>", "</r>"));
        for (int i = 0; i < 10; i++) {
            e.urlFor("test query", i);
        }
    }
}
