package jsearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A {@link BlockScraper} for search APIs that return JSON. It ignores the
 * engine's block markers: a JSON response has no markup to delimit, so the
 * "where are the results" knowledge lives here instead.
 *
 * <p>A response with no results (path absent, or not a list) yields an empty
 * list. A response that is not JSON at all throws
 * {@link IllegalArgumentException}, which {@link SearchService#awaitAll} reports
 * as that engine's failure rather than quietly showing nothing.
 */
public final class JsonApiScraper implements BlockScraper {

    /** Brave Search API: {@code {"web":{"results":[{url,title,description}]}}}. */
    public static JsonApiScraper brave() {
        return new JsonApiScraper(Arrays.asList("web", "results"), "url", "title", "description");
    }

    /** SearXNG with {@code format=json}: {@code {"results":[{url,title,content}]}}. */
    public static JsonApiScraper searxng() {
        return new JsonApiScraper(Arrays.asList("results"), "url", "title", "content");
    }

    private final List<String> resultsPath;
    private final String urlKey;
    private final String titleKey;
    private final String previewKey;

    public JsonApiScraper(List<String> resultsPath, String urlKey, String titleKey, String previewKey) {
        this.resultsPath = new ArrayList<>(resultsPath);
        this.urlKey = urlKey;
        this.titleKey = titleKey;
        this.previewKey = previewKey;
    }

    @Override
    public List<SearchResult> scrape(Engine engine, String json) {
        Object node = Json.parse(json);
        for (String key : resultsPath) {
            node = node instanceof Map ? ((Map<?, ?>) node).get(key) : null;
        }
        List<SearchResult> results = new ArrayList<>();
        if (!(node instanceof List)) {
            return results;
        }
        for (Object item : (List<?>) node) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> fields = (Map<?, ?>) item;
            String url = text(fields.get(urlKey));
            if (url.isEmpty()) {
                continue;
            }
            results.add(new SearchResult(engine, url, text(fields.get(titleKey)), text(fields.get(previewKey))));
        }
        return results;
    }

    /** APIs mark up matched terms (strong tags); strip tags as the HTML scraper does. */
    private static String text(Object value) {
        if (!(value instanceof String)) {
            return "";
        }
        return ((String) value).replaceAll("<[^>]*>", "").trim();
    }
}
