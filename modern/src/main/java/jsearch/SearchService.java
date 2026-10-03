package jsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs a query across several engines.
 *
 * <p>This is where the original's hand-rolled concurrency goes. JSearch created
 * one {@code Thread} per engine, tracked the outstanding count in a
 * {@code static int actualSearchAllowed} that every thread decremented itself,
 * and polled a {@code static boolean _stop} once per character read to decide
 * whether to give up. That design produced both races documented in the archive,
 * and it made "the search is still running" a number you had to guess at.
 *
 * <p>Here the pool owns the concurrency limit, {@code Future} owns the
 * outstanding work, and {@code cancel(true)} interrupts the blocked socket read
 * instead of a flag that happens to be checked between characters.
 */
public final class SearchService implements AutoCloseable {

    private final ExecutorService executor;
    private final PageFetcher fetcher;
    private final BlockScraper scraper;

    public SearchService(int maxConnections, PageFetcher fetcher, BlockScraper scraper) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be at least 1");
        }
        this.executor = Executors.newFixedThreadPool(maxConnections);
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.scraper = Objects.requireNonNull(scraper, "scraper");
    }

    /**
     * Searches {@code engines} for {@code query} at every level, publishing into
     * {@code collector} as results arrive.
     *
     * @return the futures, so the caller can cancel or await
     */
    public List<Future<?>> search(List<Engine> engines, String query, int levels, ResultCollector collector) {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (Engine engine : engines) {
            for (int level = 0; level < levels; level++) {
                tasks.add(engineTask(engine, query, level, collector));
            }
        }
        return tasks.stream().map(executor::submit).collect(Collectors.toList());
    }

    private Callable<Void> engineTask(Engine engine, String query, int level, ResultCollector collector) {
        return () -> {
            String url = engine.urlFor(query, level);
            String html = fetcher.fetch(url);          // interruptible: a blocked read throws
            collector.addAll(scraper.scrape(engine, html));
            return null;
        };
    }

    /**
     * Waits for every task, then fires the collector's completion listener.
     * Tasks that were cancelled are not treated as failures.
     */
    public void awaitAll(List<Future<?>> futures, ResultCollector collector) {
        boolean interrupted = false;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (CancellationException expected) {
                // cancelled on purpose; not an error
            } catch (ExecutionException failure) {
                collector.fireError(failure.getCause());
            } catch (InterruptedException stopWaiting) {
                interrupted = true;
                cancelAll(futures);
            }
        }
        if (!interrupted) {
            collector.fireComplete();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Cancels outstanding work, interrupting any thread blocked on a socket. */
    public void cancelAll(List<Future<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException shutdownNow) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}