package jsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link BlockScraper} for search APIs that return JSON. It ignores the
 * engine's block markers: a JSON response has no markup to delimit, so the
 * "where are the results" knowledge lives here instead.
 *
 * <p>A response with no results (path absent, or not a list) yields an empty
 * list. A response that is not JSON at all throws
 * {@link IllegalArgumentException}, which {@link SearchService#awaitAll} reports
 * as that engine's failure rather than quietly showing nothing.
 *
 * <p>Each API spells its fields differently, and some spell them differently
 * <em>within</em> one response &mdash; Hacker News puts a discussion's title in
 * {@code title} for link posts and {@code story_title} for text posts &mdash;
 * so every field takes a list of candidate keys and the first non-empty one
 * wins. Two cases carry no URL at all (Wikipedia, Hacker News text posts); for
 * those a template builds the URL from other fields of the same hit.
 */
public final class JsonApiScraper implements BlockScraper {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

    /** Brave Search API: {@code {"web":{"results":[{url,title,description}]}}}. */
    public static JsonApiScraper brave() {
        return new JsonApiScraper(List.of("web", "results"),
                List.of("url"), List.of("title"), List.of("description"), null);
    }

    /** SearXNG with {@code format=json}: {@code {"results":[{url,title,content}]}}. */
    public static JsonApiScraper searxng() {
        return new JsonApiScraper(List.of("results"),
                List.of("url"), List.of("title"), List.of("content"), null);
    }

    /**
     * Wikipedia's search API: {@code {"query":{"search":[{title,snippet}]}}}.
     * No url field &mdash; the article URL is the title's.
     */
    public static JsonApiScraper wikipedia() {
        return new JsonApiScraper(List.of("query", "search"),
                List.of(), List.of("title"), List.of("snippet"),
                "https://en.wikipedia.org/wiki/{title}");
    }

    /** Stack Exchange's advanced search: {@code {"items":[{link,title,body}]}}. */
    public static JsonApiScraper stackexchange() {
        return new JsonApiScraper(List.of("items"),
                List.of("link"), List.of("title"), List.of("body"), null);
    }

    /**
     * Hacker News's Algolia API: {@code {"hits":[{url,title,story_title,
     * story_text,objectID}]}}. Text posts have no url, so the item URL is
     * built from the objectID.
     */
    public static JsonApiScraper hn() {
        return new JsonApiScraper(List.of("hits"),
                List.of("url"), List.of("title", "story_title"),
                List.of("story_text", "comment_text"),
                "https://news.ycombinator.com/item?id={objectID}");
    }

    /**
     * Marginalia's public search: {@code {"results":[{url,title,description}]}}.
     * The shape is kept and tested although no live engine uses it: Marginalia
     * is reached through the local SearXNG instance instead, for the reason
     * {@link LiveEngines} documents &mdash; its query is a path segment, which
     * the {@code ^} slot cannot encode correctly.
     */
    public static JsonApiScraper marginalia() {
        return new JsonApiScraper(List.of("results"),
                List.of("url"), List.of("title"), List.of("description"), null);
    }

    private final List<String> resultsPath;
    private final List<String> urlKeys;
    private final List<String> titleKeys;
    private final List<String> previewKeys;
    private final String urlTemplate;

    public JsonApiScraper(List<String> resultsPath, List<String> urlKeys, List<String> titleKeys,
                          List<String> previewKeys, String urlTemplate) {
        this.resultsPath = new ArrayList<>(resultsPath);
        this.urlKeys = new ArrayList<>(urlKeys);
        this.titleKeys = new ArrayList<>(titleKeys);
        this.previewKeys = new ArrayList<>(previewKeys);
        this.urlTemplate = urlTemplate;
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
            String url = firstText(fields, urlKeys);
            if (url.isEmpty() && urlTemplate != null) {
                url = fillTemplate(urlTemplate, fields);
            }
            if (url.isEmpty()) {
                continue;
            }
            results.add(new SearchResult(engine, url,
                    firstText(fields, titleKeys), firstText(fields, previewKeys)));
        }
        return results;
    }

    /** The first key whose value is a non-empty string, tags stripped. */
    private static String firstText(Map<?, ?> fields, List<String> keys) {
        for (String key : keys) {
            String text = text(fields.get(key));
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    /**
     * Fills the template's {@code {field}} placeholders from the hit. A value
     * going into a URL path is percent-encoded, not form-encoded: {@code +} in
     * a path is a literal plus, and Wikipedia answers 404 for the form-encoded
     * spelling of a title with a space in it. A placeholder with no matching
     * field yields no URL at all, so the hit is skipped rather than linked to a
     * broken address.
     */
    private static String fillTemplate(String template, Map<?, ?> fields) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = text(fields.get(m.group(1)));
            if (value.isEmpty()) {
                return "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(pathEncode(value)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Percent-encoding for a path segment. {@link URLEncoding} is form encoding
     * (space becomes {@code +}), which is right for a query string and wrong
     * for a path; the {@code +} it produces is the only difference, and it
     * never encodes a real {@code +} as one, so the swap is unambiguous.
     */
    private static String pathEncode(String value) {
        return URLEncoding.encode(value).replace("+", "%20");
    }

    /** APIs mark up matched terms (strong tags); strip tags as the HTML scraper does. */
    private static String text(Object value) {
        if (!(value instanceof String)) {
            return "";
        }
        return ((String) value).replaceAll("<[^>]*>", "").trim();
    }
}
