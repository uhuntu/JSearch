package jsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The single place results live.
 *
 * <p>This replaces four structures in the original: a {@code Hashtable} for
 * detail, a {@code Vector} for URLs, a {@code List} for the visible rows, and a
 * {@code Label} for the count. Four parallel structures with no single owner is
 * what made the races possible in the first place.
 *
 * <p>Deduplication is by URL, and the check-then-act is inside the lock. In the
 * original that pair was split across two threads and could both conclude "not
 * seen" and insert the same URL twice. Here it cannot.
 */
public final class ResultCollector {

    private final Object lock = new Object();

    /** Insertion-ordered and deduplicated; the one truth. */
    private final Set<String> seenUrls = new LinkedHashSet<>();

    private final List<SearchResult> results = new ArrayList<>();

    private volatile Consumer<SearchResult> onResult = result -> { };

    private volatile Runnable onComplete = () -> { };

    /** Called when an engine task fails. One bad engine must not sink the rest. */
    private volatile Consumer<Throwable> onError = cause -> { };

    /**
     * Records a result, or discards it if the URL is already known.
     *
     * @return true if the result was new
     */
    public boolean add(SearchResult result) {
        boolean added;
        synchronized (lock) {
            added = seenUrls.add(result.url());
            if (added) {
                results.add(result);
            }
        }
        if (added) {
            onResult.accept(result);
        }
        return added;
    }

    public void addAll(Iterable<SearchResult> incoming) {
        for (SearchResult result : incoming) {
            add(result);
        }
    }

    public List<SearchResult> results() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(results));
        }
    }

    public int count() {
        synchronized (lock) {
            return results.size();
        }
    }

    public boolean contains(String url) {
        synchronized (lock) {
            return seenUrls.contains(url);
        }
    }

    /**
     * Registers a listener called after each new result. The default collector
     * is empty, so no null check is needed anywhere.
     *
     * <p>Deliberately outside the lock: a listener that blocks must not block
     * other engines from recording results.
     */
    public void onResult(Consumer<SearchResult> listener) {
        this.onResult = listener;
    }

    public void onComplete(Runnable listener) {
        this.onComplete = listener;
    }

    public void onError(Consumer<Throwable> listener) {
        this.onError = listener;
    }

    /** Called by the search service when an engine task fails. */
    public void fireError(Throwable cause) {
        onError.accept(cause);
    }

    /** Called by the search service once every engine has finished. */
    public void fireComplete() {
        onComplete.run();
    }
}