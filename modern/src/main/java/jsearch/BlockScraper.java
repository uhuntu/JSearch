package jsearch;

import java.util.List;

/**
 * Extracts results from one engine's HTML.
 *
 * <p>An interface so the scraping algorithm can be exercised against saved
 * fixtures. {@code ENGINES/*.html} in the archive are exactly that &mdash; real
 * captured pages &mdash; which means the original's scraping logic was never
 * testable without a browser, a network and an applet.
 */
public interface BlockScraper {

    /**
     * Scrapes every result block from {@code html} using {@code engine}'s markers.
     *
     * @return results in document order; empty if none, never null
     */
    List<SearchResult> scrape(Engine engine, String html);
}
