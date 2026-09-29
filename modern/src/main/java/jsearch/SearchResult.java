package jsearch;

/**
 * A single scraped result.
 *
 * <p>Immutable and self-contained. The original carried the same three facts
 * inside a mutable {@code ResultsDetails} object that was shared between threads
 * and only defensively copied at the last moment; here there is nothing to copy.
 */
public final class SearchResult {

    private final Engine engine;
    private final String url;
    private final String title;
    private final String preview;

    public SearchResult(Engine engine, String url, String title, String preview) {
        this.engine = engine;
        this.url = url;
        this.title = title;
        this.preview = preview;
    }

    public Engine engine() {
        return engine;
    }

    public String url() {
        return url;
    }

    public String title() {
        return title;
    }

    public String preview() {
        return preview;
    }

    @Override
    public String toString() {
        return title + " | " + url + " | from: " + engine;
    }
}
