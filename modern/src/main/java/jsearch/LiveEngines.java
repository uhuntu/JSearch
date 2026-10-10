package jsearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The live engine set: engines that answer a 2026 query, expressed in the
 * archive's own terms &mdash; a name, a category, a URL template with {@code ^}
 * for the query, and block markers (ignored: these engines answer JSON).
 *
 * <p>Every one is an official public API that needs no key, which is what makes
 * a live set possible at all: the 2001 engines died because their HTML moved
 * and their URLs were retired, and a scraped SERP in 2026 needs a parser per
 * engine plus an answer to the bot walls. An API contract is the one thing
 * about a search engine that does not rot.
 *
 * <p>Bing is deliberately absent, for both reasons at once: its HTML no longer
 * matches anything the archive's marker scraper can read, and it answers a
 * server IP with popular pages instead of results once it smells a bot &mdash;
 * the failure {@link RelevanceCheck} exists to catch. If a future change adds
 * a scraper that can read it, the poison check is already here.
 *
 * <p>Marginalia is absent for a reason worth writing down, because it looks
 * like a fixable detail and is not. Its public JSON API takes the query as a
 * <em>path</em> segment, and the {@code ^} slot encodes a query the way the
 * 2002 applet did &mdash; for a query string, where a space becomes {@code +}.
 * In a path a {@code +} is a literal plus, so "java applet" reaches Marginalia
 * as the search for the literal string "java+applet", which it answers with
 * zero results and no error: a silent wrong answer, the worst failure mode
 * there is. Encoding the slot for paths instead would change what every
 * archive-mode URL looks like, so the engine stays out and Marginalia is
 * reached through the local SearXNG instance, which aggregates it and spells
 * the request correctly.
 *
 * <p>The SearXNG entry points at the local instance (override with
 * {@code -Djsearch.searxng=URL}); without one running it fails like any other
 * engine and the UI says so.
 */
public final class LiveEngines {

    private static final Engine.Block NO_MARKERS = new Engine.Block("{", "}");

    private static final String SEARXNG_BASE =
            System.getProperty("jsearch.searxng", "http://127.0.0.1:8888");

    private static final List<Engine> ENGINES = List.of(
            new Engine("Wikipedia", "API",
                    "https://en.wikipedia.org/w/api.php?action=query&list=search"
                            + "&format=json&srlimit=8&srsearch=^", NO_MARKERS),
            new Engine("StackExchange", "API",
                    "https://api.stackexchange.com/2.3/search/advanced"
                            + "?site=stackoverflow&pagesize=8&q=^", NO_MARKERS),
            new Engine("HackerNews", "API",
                    "https://hn.algolia.com/api/v1/search?hitsPerPage=8&query=^", NO_MARKERS),
            new Engine("SearXNG", "API",
                    SEARXNG_BASE + "/search?format=json&q=^", NO_MARKERS));

    private static final Map<String, JsonApiScraper> SCRAPERS = scrapers();

    private static Map<String, JsonApiScraper> scrapers() {
        Map<String, JsonApiScraper> byName = new LinkedHashMap<>();
        byName.put("Wikipedia", JsonApiScraper.wikipedia());
        byName.put("StackExchange", JsonApiScraper.stackexchange());
        byName.put("HackerNews", JsonApiScraper.hn());
        byName.put("SearXNG", JsonApiScraper.searxng());
        return byName;
    }

    public static List<Engine> engines() {
        return ENGINES;
    }

    /**
     * One scraper standing in for five. {@link SearchService} takes a single
     * scraper for every engine it fans out to, so this dispatch routes each
     * engine's response to the JSON shape that engine actually returns; an
     * engine with no entry yields nothing rather than throwing.
     */
    public static BlockScraper dispatch() {
        return (engine, body) -> {
            JsonApiScraper scraper = SCRAPERS.get(engine.name());
            return scraper == null ? List.of() : scraper.scrape(engine, body);
        };
    }

    private LiveEngines() {
    }
}
