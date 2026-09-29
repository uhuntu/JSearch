package jsearch;

import java.io.IOException;

/**
 * Fetches one page. Separated from {@link SearchService} so the whole pipeline
 * can be exercised against canned HTML with no network, no socket, and no applet.
 */
public interface PageFetcher {

    /**
     * @return the page body for {@code url}
     * @throws IOException if the page cannot be retrieved
     */
    String fetch(String url) throws IOException;
}
