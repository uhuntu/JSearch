package jsearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * A lightweight, zero-dependency interactive Web UI and REST API server for JSearch.
 *
 * <p>Demonstrates the modern reference design in an interactive web browser:
 * <ul>
 *   <li>Runs concurrent multi-engine search across 2001 archive fixtures or dynamic queries</li>
 *   <li>Visualizes live deduplication (total scraped vs unique results)</li>
 *   <li>Demonstrates the 2002 legacy URL-key collision bug vs the modern reference architecture</li>
 *   <li>Provides REST API endpoints ({@code /api/engines}, {@code /api/search}, {@code /api/compare}, {@code /api/health})</li>
 * </ul>
 */
public final class WebServer implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final Path archiveRoot;
    private final List<Engine> allEngines;

    /**
     * The query each captured page in {@code ENGINES/} was saved for.
     *
     * <p>Each of these four pages is one real answer to one real query, and a
     * fixture only means anything next to the question it was captured against.
     * Verified against the search box in each saved page.
     */
    private static final Map<String, String> CAPTURED_QUERIES = capturedQueries();

    private static Map<String, String> capturedQueries() {
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("google_en.html", "java");      // <title>Google搜索: java</title>
        queries.put("lycos_en.html", "java");      // Search for: "java"
        queries.put("google_cn.html", "西二在线"); // name=q value="西二在线"
        queries.put("baidu_cn.html", "西二在线");  // name=word value="西二在线"
        return Collections.unmodifiableMap(queries);
    }

    public WebServer(int port) throws IOException {
        this.archiveRoot = findArchiveRoot();
        this.allEngines = initEngines(archiveRoot);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.port = this.server.getAddress().getPort();
        this.server.setExecutor(Executors.newFixedThreadPool(8));

        this.server.createContext("/", new StaticUiHandler());
        this.server.createContext("/api/health", new HealthHandler());
        this.server.createContext("/api/engines", new EnginesHandler());
        this.server.createContext("/api/search", new SearchHandler());
        this.server.createContext("/api/compare", new CompareHandler());
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) || "--port".equals(args[i])) {
                if (i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                }
            }
        }
        String portProp = System.getProperty("jsearch.port");
        if (portProp != null && !portProp.isEmpty()) {
            port = Integer.parseInt(portProp);
        }

        WebServer webServer = new WebServer(port);
        webServer.start();

        System.out.println("==================================================================");
        System.out.println(" JSearch Web UI & REST API Server (Modern Reference Implementation)");
        System.out.println("==================================================================");
        System.out.println(" Server running at: http://localhost:" + webServer.getPort() + "/");
        System.out.println(" REST API endpoints:");
        System.out.println("   - GET http://localhost:" + webServer.getPort() + "/api/engines");
        System.out.println("   - GET http://localhost:" + webServer.getPort() + "/api/search?q=java");
        System.out.println("   - GET http://localhost:" + webServer.getPort() + "/api/compare?q=java");
        System.out.println("   - GET http://localhost:" + webServer.getPort() + "/api/health");
        System.out.println(" Archive fixtures available: " + (webServer.archiveRoot != null));
        System.out.println(" Press Ctrl+C to stop.");
        System.out.println("==================================================================");

        Runtime.getRuntime().addShutdownHook(new Thread(webServer::close));
    }

    private static Path findArchiveRoot() {
        String prop = System.getProperty("jsearch.archive");
        if (prop != null) {
            Path p = Paths.get(prop).toAbsolutePath().normalize();
            if (Files.isDirectory(p.resolve("ENGINES")) && Files.isDirectory(p.resolve("Releases"))) {
                return p;
            }
        }
        Path candidate = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 6 && candidate != null; i++) {
            if (Files.isDirectory(candidate.resolve("ENGINES")) && Files.isDirectory(candidate.resolve("Releases"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static List<Engine> initEngines(Path root) {
        if (root != null) {
            Path enginesFile = root.resolve("Releases").resolve("JSEngines.txt");
            if (Files.isRegularFile(enginesFile)) {
                try {
                    String text = new String(Files.readAllBytes(enginesFile), archiveCharset());
                    List<Engine> list = new ArrayList<>(EngineRepository.parse(text));
                    // Also add Lycos from ENGINES/ if not present
                    list.add(new Engine("Lycos / Lycos", "English",
                            "http://search.lycos.com/main/default.asp?query=^",
                            new Engine.Block("ref=", "</a>")));
                    return Collections.unmodifiableList(list);
                } catch (Exception ignored) {
                }
            }
        }
        // Fallback default engines
        List<Engine> defaults = new ArrayList<>();
        defaults.add(new Engine("Google / Google", "English",
                "http://www.google.com/search?q=^&start=`0",
                new Engine.Block("<p><", "k - ")));
        defaults.add(new Engine("GB_Chinese Google", "Chinese",
                "http://www.google.com/search?q=^&lr=lang_zh-CN&start=`0",
                new Engine.Block("<p><", "k - ")));
        defaults.add(new Engine("Baidu / 百度", "Chinese",
                "http://www1.baidu.com/baidu?word=^&cl=3&f=1&pn=`0",
                new Engine.Block("<table", "ble>")));
        defaults.add(new Engine("Lycos / Lycos", "English",
                "http://search.lycos.com/main/default.asp?query=^",
                new Engine.Block("ref=", "</a>")));
        return Collections.unmodifiableList(defaults);
    }

    // -------------------------------------------------------------------------
    // HTTP Handlers
    // -------------------------------------------------------------------------

    private final class StaticUiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/favicon.ico".equals(path)) {
                sendResponse(exchange, 204, "", "image/x-icon");
                return;
            }
            byte[] htmlBytes = buildUiHtml().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "{\"status\":\"ok\",\"port\":" + port
                    + ",\"archiveAvailable\":" + (archiveRoot != null)
                    + ",\"enginesAvailable\":" + allEngines.size() + "}";
            sendResponse(exchange, 200, json, "application/json; charset=UTF-8");
        }
    }

    private final class EnginesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < allEngines.size(); i++) {
                Engine e = allEngines.get(i);
                if (i > 0) sb.append(",");
                sb.append("{")
                        .append("\"id\":").append(quote(e.name())).append(",")
                        .append("\"name\":").append(quote(e.name())).append(",")
                        .append("\"category\":").append(quote(e.category())).append(",")
                        .append("\"urlTemplate\":").append(quote(e.urlTemplate())).append(",")
                        .append("\"blockStart\":").append(quote(e.resultBlock().start())).append(",")
                        .append("\"blockEnd\":").append(quote(e.resultBlock().end()))
                        .append("}");
            }
            sb.append("]");
            sendResponse(exchange, 200, sb.toString(), "application/json; charset=UTF-8");
        }
    }

    private final class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String query = params.getOrDefault("q", "java");
            String mode = params.getOrDefault("mode", "modern"); // "modern" or "legacy"
            int levels = clamp(parseInt(params.get("levels"), 1), 1, 3);
            int speed = clamp(parseInt(params.get("concurrency"), 4), 1, 8);

            String selectedEnginesParam = params.get("engines");
            List<Engine> targetEngines = filterEngines(selectedEnginesParam, mode);

            long start = System.currentTimeMillis();
            SearchResultsData data = executeSearch(targetEngines, query, levels, speed);
            long durationMs = System.currentTimeMillis() - start;

            String json = formatSearchJson(query, mode, durationMs, targetEngines, data);
            sendResponse(exchange, 200, json, "application/json; charset=UTF-8");
        }
    }

    private final class CompareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI());
            String query = params.getOrDefault("q", "java");
            int levels = clamp(parseInt(params.get("levels"), 1), 1, 2);
            int speed = clamp(parseInt(params.get("concurrency"), 4), 1, 8);

            List<Engine> legacyEngines = filterEngines(null, "legacy");
            List<Engine> modernEngines = filterEngines(null, "modern");

            long startLegacy = System.currentTimeMillis();
            SearchResultsData legacyData = executeSearch(legacyEngines, query, levels, speed);
            long legacyDuration = System.currentTimeMillis() - startLegacy;

            long startModern = System.currentTimeMillis();
            SearchResultsData modernData = executeSearch(modernEngines, query, levels, speed);
            long modernDuration = System.currentTimeMillis() - startModern;

            // Missing engines in legacy
            List<String> legacyNames = legacyEngines.stream().map(Engine::toString).collect(Collectors.toList());
            List<String> modernNames = modernEngines.stream().map(Engine::toString).collect(Collectors.toList());
            List<String> lostEngines = new ArrayList<>(modernNames);
            lostEngines.removeAll(legacyNames);

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"query\":").append(quote(query)).append(",");
            sb.append("\"defectExplanation\":").append(quote("In the 1999-2002 JSearch code, getEngData() stored engines in a Hashtable keyed on URL. Because English Google and Chinese Google shared 'http://www.google.com/', the second entry overwrote the first, silently discarding Chinese Google from the search interface.")).append(",");
            sb.append("\"lostEngines\":").append(toJsonStringArray(lostEngines)).append(",");

            // Legacy summary
            sb.append("\"legacy\":{")
                    .append("\"engineCount\":").append(legacyEngines.size()).append(",")
                    .append("\"engines\":").append(toJsonStringArray(legacyNames)).append(",")
                    .append("\"totalScraped\":").append(legacyData.rawCount).append(",")
                    .append("\"totalUnique\":").append(legacyData.collector.count()).append(",")
                    .append("\"durationMs\":").append(legacyDuration).append(",")
                    .append("\"results\":").append(resultsToJson(legacyData.collector.results()))
                    .append("},");

            // Modern summary
            sb.append("\"modern\":{")
                    .append("\"engineCount\":").append(modernEngines.size()).append(",")
                    .append("\"engines\":").append(toJsonStringArray(modernNames)).append(",")
                    .append("\"totalScraped\":").append(modernData.rawCount).append(",")
                    .append("\"totalUnique\":").append(modernData.collector.count()).append(",")
                    .append("\"durationMs\":").append(modernDuration).append(",")
                    .append("\"results\":").append(resultsToJson(modernData.collector.results()))
                    .append("}");

            sb.append("}");
            sendResponse(exchange, 200, sb.toString(), "application/json; charset=UTF-8");
        }
    }

    // -------------------------------------------------------------------------
    // Search Execution & Data Model
    // -------------------------------------------------------------------------

    private static final class SearchResultsData {
        final ResultCollector collector;
        final int rawCount;

        SearchResultsData(ResultCollector collector, int rawCount) {
            this.collector = collector;
            this.rawCount = rawCount;
        }
    }

    private List<Engine> filterEngines(String selectedParam, String mode) {
        List<Engine> base;
        if ("legacy".equalsIgnoreCase(mode)) {
            // Emulate 2002 Hashtable key-by-URL defect:
            // engDataHt.put(engDataHtHead, engDataHtBody);
            Map<String, Engine> urlKeyed = new LinkedHashMap<>();
            for (Engine e : allEngines) {
                String rootUrl = extractRootUrl(e.urlTemplate());
                urlKeyed.put(rootUrl, e); // Overwrites earlier engines sharing root URL!
            }
            base = new ArrayList<>(urlKeyed.values());
        } else {
            base = new ArrayList<>(allEngines);
        }

        if (selectedParam == null || selectedParam.trim().isEmpty() || "all".equalsIgnoreCase(selectedParam)) {
            return base;
        }
        List<String> requested = Arrays.stream(selectedParam.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        return base.stream()
                .filter(e -> requested.contains(e.name()) || requested.contains(e.name().split(" ")[0]))
                .collect(Collectors.toList());
    }

    private static String extractRootUrl(String template) {
        if (template.contains("google.com")) return "http://www.google.com/";
        if (template.contains("baidu.com")) return "http://www1.baidu.com/";
        if (template.contains("lycos.com")) return "http://search.lycos.com/";
        return template;
    }

    private SearchResultsData executeSearch(List<Engine> engines, String query, int levels, int concurrency) {
        ResultCollector collector = new ResultCollector();
        List<Integer> rawCounts = Collections.synchronizedList(new ArrayList<>());

        PageFetcher fetcher = url -> {
            String page = fetchPageContent(url, query);
            return page != null ? page : "";
        };

        HtmlBlockScraper scraper = new HtmlBlockScraper();
        // Custom wrapper to tally raw scraped results before deduplication
        BlockScraper countingScraper = (engine, html) -> {
            List<SearchResult> scraped = scraper.scrape(engine, html);
            rawCounts.add(scraped.size());
            return scraped;
        };

        SearchService service = new SearchService(concurrency, fetcher, countingScraper);
        List<Future<?>> running = service.search(engines, query, levels, collector);
        service.awaitAll(running, collector);
        service.shutdown();

        int totalRaw = rawCounts.stream().mapToInt(Integer::intValue).sum();
        return new SearchResultsData(collector, totalRaw);
    }

    private String fetchPageContent(String url, String query) {
        // The archive captured one real page per engine, and each of those pages is
        // the answer to one specific query: google_en.html and lycos_en.html were
        // captured for "java", google_cn.html and baidu_cn.html for the Chinese
        // query in their own search box. A fixture is only a fixture for the query
        // it was captured against — handing google_en.html back for "applet" would
        // return ten 2001 links about Java under a search for something else, and
        // the numbers the UI reports would all be about the wrong question.
        // Anything else falls through to the synthetic pages below.
        if (archiveRoot != null) {
            String filename = fixtureFor(url);
            String capturedQuery = capturedQueryFor(filename);
            if (filename != null && capturedQuery != null
                    && capturedQuery.equalsIgnoreCase(query.trim())) {
                Path p = archiveRoot.resolve("ENGINES").resolve(filename);
                if (Files.isRegularFile(p)) {
                    try {
                        return decodeArchivePage(p);
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // Dynamic synthetic vintage search engine results for arbitrary queries
        return generateSyntheticPage(url, query);
    }

    /** The captured page this engine URL stands for, or null if it has none. */
    private static String fixtureFor(String url) {
        if (url.contains("lr=lang_zh-CN")) {
            return "google_cn.html";
        } else if (url.contains("google.com")) {
            return "google_en.html";
        } else if (url.contains("baidu.com")) {
            return "baidu_cn.html";
        } else if (url.contains("lycos.com")) {
            return "lycos_en.html";
        }
        return null;
    }

    /**
     * The query a captured page was actually saved for, read out of that page's
     * own search box. Returned only for a page that is present, so a missing
     * fixture cannot claim to answer anything.
     */
    private String capturedQueryFor(String filename) {
        if (filename == null) {
            return null;
        }
        Path p = archiveRoot.resolve("ENGINES").resolve(filename);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        return CAPTURED_QUERIES.get(filename);
    }

    /**
     * Decodes a captured page the way it was saved. Three of the four are GBK;
     * {@code lycos_en.html} is the one file in the archive that was never Chinese
     * and is ISO-8859-1, so decoding it as GBK corrupts its few high bytes.
     */
    private static String decodeArchivePage(Path page) throws IOException {
        Charset cs = "lycos_en.html".equals(page.getFileName().toString())
                ? StandardCharsets.ISO_8859_1
                : archiveCharset();
        return new String(Files.readAllBytes(page), cs);
    }

    private static Charset archiveCharset() {
        try {
            return Charset.forName("GBK");
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private String generateSyntheticPage(String url, String query) {
        String q = (query == null || query.trim().isEmpty()) ? "java" : query.trim();
        String safeQ = q.replace("<", "&lt;").replace(">", "&gt;");

        if (url.contains("baidu.com")) {
            StringBuilder sb = new StringBuilder("<html><head><title>Baidu Search</title></head><body>");
            for (int i = 1; i <= 10; i++) {
                sb.append("<table width=\"100%\"><tr><td>")
                        .append("<a href=\"http://www.baidu.com/link?url=site").append(i).append("\">")
                        .append("<b>").append(safeQ).append(" 相关的中文搜索结果 #").append(i).append("</b></a>")
                        .append("<br>这是百度抓取的关于 ").append(safeQ).append(" 的中文网页快照与摘要信息，展示在 JSearch 聚合列表中。")
                        .append("</td></tr></table>ble>");
            }
            sb.append("</body></html>");
            return sb.toString();
        } else if (url.contains("lycos.com")) {
            StringBuilder sb = new StringBuilder("<html><body>");
            for (int i = 1; i <= 8; i++) {
                sb.append("<p>ref=\"http://www.lycos-site.org/item").append(i).append("\">")
                        .append("<b>Lycos Directory: ").append(safeQ).append(" Topic ").append(i).append("</b></a>")
                        .append(" Lycos vintage web catalog entry for ").append(safeQ).append(".</p>");
            }
            sb.append("</body></html>");
            return sb.toString();
        } else {
            // Google style
            boolean isChinese = url.contains("lang_zh-CN");
            StringBuilder sb = new StringBuilder("<html><body>");
            for (int i = 1; i <= 10; i++) {
                // Include shared overlapping URL for item 1 and item 4 to demonstrate de-duplication!
                String targetUrl = (i == 1) ? "http://www.sun.com/java"
                        : (i == 4) ? "http://www.w3.org/standards"
                        : "http://www.google-result-" + (isChinese ? "cn" : "en") + ".org/doc" + i;
                String title = isChinese
                        ? safeQ + " 中文官方指南与技术资源 (" + i + ")"
                        : safeQ + " Standard Reference & Architecture (" + i + ")";
                String preview = isChinese
                        ? "提供权威的 " + safeQ + " 开发者文档、规范下载以及中文社群讨论信息。"
                        : "Comprehensive overview of " + safeQ + " technology, enterprise solutions, and software downloads.";

                sb.append("<p><a href=\"").append(targetUrl).append("\"><b>").append(title).append("</b></a><br>")
                        .append(preview).append(" - 32k - </p>");
            }
            sb.append("</body></html>");
            return sb.toString();
        }
    }

    private static String formatSearchJson(String query, String mode, long durationMs,
                                            List<Engine> engines, SearchResultsData data) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"query\":").append(quote(query)).append(",");
        sb.append("\"mode\":").append(quote(mode)).append(",");
        sb.append("\"durationMs\":").append(durationMs).append(",");
        sb.append("\"totalEngines\":").append(engines.size()).append(",");
        sb.append("\"totalRaw\":").append(data.rawCount).append(",");
        sb.append("\"totalUnique\":").append(data.collector.count()).append(",");
        sb.append("\"duplicatesDropped\":").append(Math.max(0, data.rawCount - data.collector.count())).append(",");

        // Engine summary
        Map<String, Long> engineCounts = data.collector.results().stream()
                .collect(Collectors.groupingBy(r -> r.engine().name(), Collectors.counting()));
        sb.append("\"engines\":[");
        int eIdx = 0;
        for (Engine e : engines) {
            if (eIdx++ > 0) sb.append(",");
            sb.append("{")
                    .append("\"name\":").append(quote(e.name())).append(",")
                    .append("\"category\":").append(quote(e.category())).append(",")
                    .append("\"count\":").append(engineCounts.getOrDefault(e.name(), 0L))
                    .append("}");
        }
        sb.append("],");

        // Results
        sb.append("\"results\":").append(resultsToJson(data.collector.results()));
        sb.append("}");
        return sb.toString();
    }

    private static String resultsToJson(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (i > 0) sb.append(",");
            sb.append("{")
                    .append("\"rank\":").append(i + 1).append(",")
                    .append("\"title\":").append(quote(r.title())).append(",")
                    .append("\"url\":").append(quote(r.url())).append(",")
                    .append("\"preview\":").append(quote(r.preview())).append(",")
                    .append("\"engine\":").append(quote(r.engine().name())).append(",")
                    .append("\"category\":").append(quote(r.engine().category()))
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonStringArray(List<String> list) {
        return "[" + list.stream().map(WebServer::quote).collect(Collectors.joining(",")) + "]";
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static void sendResponse(HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> map = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            } else if (idx == -1 && !pair.isEmpty()) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    static String quote(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c >= 127) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Frontend HTML/CSS/JS Single-Page Application
    // -------------------------------------------------------------------------

    private static String buildUiHtml() {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>JSearch Meta-Search Engine & Archaeology Demo</title>\n"
                + "  <style>\n"
                + "    :root {\n"
                + "      --bg-base: #0a0f1d;\n"
                + "      --bg-surface: #11192d;\n"
                + "      --bg-card: rgba(22, 33, 58, 0.7);\n"
                + "      --border-color: rgba(255, 255, 255, 0.08);\n"
                + "      --text-main: #f1f5f9;\n"
                + "      --text-muted: #94a3b8;\n"
                + "      --primary: #3b82f6;\n"
                + "      --primary-hover: #2563eb;\n"
                + "      --accent: #8b5cf6;\n"
                + "      --success: #10b981;\n"
                + "      --warning: #f59e0b;\n"
                + "      --danger: #ef4444;\n"
                + "      --glow: rgba(59, 130, 246, 0.25);\n"
                + "    }\n"
                + "    * { box-sizing: border-box; margin: 0; padding: 0; }\n"
                + "    body {\n"
                + "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Inter, sans-serif;\n"
                + "      background-color: var(--bg-base);\n"
                + "      color: var(--text-main);\n"
                + "      min-height: 100vh;\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      background-image: radial-gradient(circle at 50% 0%, rgba(59, 130, 246, 0.12) 0%, transparent 60%);\n"
                + "    }\n"
                + "    header {\n"
                + "      padding: 1.5rem 2rem;\n"
                + "      border-bottom: 1px solid var(--border-color);\n"
                + "      background: rgba(10, 15, 29, 0.85);\n"
                + "      backdrop-filter: blur(12px);\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      justify-content: space-between;\n"
                + "      position: sticky;\n"
                + "      top: 0;\n"
                + "      z-index: 100;\n"
                + "    }\n"
                + "    .brand {\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      gap: 0.75rem;\n"
                + "    }\n"
                + "    .logo-badge {\n"
                + "      background: linear-gradient(135deg, var(--primary), var(--accent));\n"
                + "      color: white;\n"
                + "      font-weight: 800;\n"
                + "      font-size: 1.25rem;\n"
                + "      padding: 0.35rem 0.75rem;\n"
                + "      border-radius: 8px;\n"
                + "      box-shadow: 0 0 15px var(--glow);\n"
                + "    }\n"
                + "    .brand h1 { font-size: 1.25rem; font-weight: 700; }\n"
                + "    .brand p { font-size: 0.75rem; color: var(--text-muted); }\n"
                + "    .nav-tags {\n"
                + "      display: flex;\n"
                + "      gap: 0.5rem;\n"
                + "    }\n"
                + "    .badge-pill {\n"
                + "      font-size: 0.7rem;\n"
                + "      padding: 0.25rem 0.6rem;\n"
                + "      border-radius: 9999px;\n"
                + "      background: rgba(255, 255, 255, 0.05);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      color: var(--text-muted);\n"
                + "    }\n"
                + "    main {\n"
                + "      flex: 1;\n"
                + "      max-width: 1180px;\n"
                + "      width: 100%;\n"
                + "      margin: 0 auto;\n"
                + "      padding: 2rem 1.5rem;\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 1.5rem;\n"
                + "    }\n"
                + "    .hero {\n"
                + "      text-align: center;\n"
                + "      margin-bottom: 0.5rem;\n"
                + "    }\n"
                + "    .hero h2 {\n"
                + "      font-size: 2.25rem;\n"
                + "      font-weight: 800;\n"
                + "      margin-bottom: 0.5rem;\n"
                + "      background: linear-gradient(135deg, #ffffff 40%, var(--primary) 100%);\n"
                + "      -webkit-background-clip: text;\n"
                + "      -webkit-text-fill-color: transparent;\n"
                + "    }\n"
                + "    .hero p {\n"
                + "      color: var(--text-muted);\n"
                + "      font-size: 1rem;\n"
                + "      max-width: 650px;\n"
                + "      margin: 0 auto;\n"
                + "    }\n"
                + "    .control-card {\n"
                + "      background: var(--bg-card);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      border-radius: 16px;\n"
                + "      padding: 1.5rem;\n"
                + "      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);\n"
                + "      backdrop-filter: blur(16px);\n"
                + "    }\n"
                + "    .search-box {\n"
                + "      display: flex;\n"
                + "      gap: 0.75rem;\n"
                + "      margin-bottom: 1rem;\n"
                + "    }\n"
                + "    .search-input-wrapper {\n"
                + "      flex: 1;\n"
                + "      position: relative;\n"
                + "    }\n"
                + "    .search-input {\n"
                + "      width: 100%;\n"
                + "      background: rgba(10, 15, 29, 0.9);\n"
                + "      border: 1px solid rgba(255, 255, 255, 0.15);\n"
                + "      color: white;\n"
                + "      font-size: 1.1rem;\n"
                + "      padding: 0.85rem 1.25rem;\n"
                + "      border-radius: 10px;\n"
                + "      outline: none;\n"
                + "      transition: all 0.2s;\n"
                + "    }\n"
                + "    .search-input:focus {\n"
                + "      border-color: var(--primary);\n"
                + "      box-shadow: 0 0 0 3px var(--glow);\n"
                + "    }\n"
                + "    .btn-search {\n"
                + "      background: linear-gradient(135deg, var(--primary), var(--primary-hover));\n"
                + "      color: white;\n"
                + "      border: none;\n"
                + "      padding: 0 1.75rem;\n"
                + "      border-radius: 10px;\n"
                + "      font-weight: 600;\n"
                + "      font-size: 1rem;\n"
                + "      cursor: pointer;\n"
                + "      transition: transform 0.1s, box-shadow 0.2s;\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      gap: 0.5rem;\n"
                + "    }\n"
                + "    .btn-search:hover {\n"
                + "      box-shadow: 0 0 15px var(--glow);\n"
                + "      transform: translateY(-1px);\n"
                + "    }\n"
                + "    .quick-queries {\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      gap: 0.5rem;\n"
                + "      flex-wrap: wrap;\n"
                + "      margin-bottom: 1.25rem;\n"
                + "      font-size: 0.85rem;\n"
                + "      color: var(--text-muted);\n"
                + "    }\n"
                + "    .query-tag {\n"
                + "      background: rgba(255, 255, 255, 0.06);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      color: #cbd5e1;\n"
                + "      padding: 0.2rem 0.6rem;\n"
                + "      border-radius: 6px;\n"
                + "      cursor: pointer;\n"
                + "      transition: all 0.2s;\n"
                + "    }\n"
                + "    .query-tag:hover {\n"
                + "      background: var(--primary);\n"
                + "      color: white;\n"
                + "      border-color: var(--primary);\n"
                + "    }\n"
                + "    .options-grid {\n"
                + "      display: grid;\n"
                + "      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));\n"
                + "      gap: 1.25rem;\n"
                + "      padding-top: 1rem;\n"
                + "      border-top: 1px solid var(--border-color);\n"
                + "    }\n"
                + "    .option-group h4 {\n"
                + "      font-size: 0.8rem;\n"
                + "      text-transform: uppercase;\n"
                + "      color: var(--text-muted);\n"
                + "      margin-bottom: 0.5rem;\n"
                + "      letter-spacing: 0.05em;\n"
                + "    }\n"
                + "    .engine-chips {\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 0.4rem;\n"
                + "    }\n"
                + "    .engine-checkbox {\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      gap: 0.5rem;\n"
                + "      font-size: 0.85rem;\n"
                + "      cursor: pointer;\n"
                + "    }\n"
                + "    .engine-checkbox input { accent-color: var(--primary); }\n"
                + "    .mode-pills {\n"
                + "      display: flex;\n"
                + "      background: rgba(10, 15, 29, 0.8);\n"
                + "      border-radius: 8px;\n"
                + "      padding: 0.25rem;\n"
                + "      border: 1px solid var(--border-color);\n"
                + "    }\n"
                + "    .mode-pill {\n"
                + "      flex: 1;\n"
                + "      padding: 0.4rem 0.75rem;\n"
                + "      font-size: 0.8rem;\n"
                + "      font-weight: 600;\n"
                + "      text-align: center;\n"
                + "      border-radius: 6px;\n"
                + "      cursor: pointer;\n"
                + "      transition: all 0.2s;\n"
                + "      color: var(--text-muted);\n"
                + "    }\n"
                + "    .mode-pill.active {\n"
                + "      background: var(--primary);\n"
                + "      color: white;\n"
                + "      box-shadow: 0 0 10px var(--glow);\n"
                + "    }\n"
                + "    .mode-pill.active.legacy {\n"
                + "      background: var(--warning);\n"
                + "      color: black;\n"
                + "      box-shadow: 0 0 10px rgba(245, 158, 11, 0.4);\n"
                + "    }\n"
                + "    .metrics-bar {\n"
                + "      display: grid;\n"
                + "      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));\n"
                + "      gap: 1rem;\n"
                + "    }\n"
                + "    .metric-card {\n"
                + "      background: var(--bg-surface);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      border-radius: 12px;\n"
                + "      padding: 1rem;\n"
                + "      text-align: center;\n"
                + "    }\n"
                + "    .metric-value {\n"
                + "      font-size: 1.6rem;\n"
                + "      font-weight: 700;\n"
                + "      color: #fff;\n"
                + "    }\n"
                + "    .metric-value.highlight { color: var(--primary); }\n"
                + "    .metric-value.warn { color: var(--warning); }\n"
                + "    .metric-label {\n"
                + "      font-size: 0.75rem;\n"
                + "      color: var(--text-muted);\n"
                + "      margin-top: 0.25rem;\n"
                + "    }\n"
                + "    .alert-panel {\n"
                + "      background: rgba(245, 158, 11, 0.1);\n"
                + "      border: 1px solid rgba(245, 158, 11, 0.3);\n"
                + "      border-radius: 12px;\n"
                + "      padding: 1rem 1.25rem;\n"
                + "      font-size: 0.85rem;\n"
                + "      line-height: 1.5;\n"
                + "      color: #fde68a;\n"
                + "      display: none;\n"
                + "    }\n"
                + "    .alert-panel.active { display: block; }\n"
                + "    .results-section {\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 1rem;\n"
                + "    }\n"
                + "    .results-header {\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      justify-content: space-between;\n"
                + "    }\n"
                + "    .filter-pills {\n"
                + "      display: flex;\n"
                + "      gap: 0.5rem;\n"
                + "    }\n"
                + "    .filter-pill {\n"
                + "      font-size: 0.75rem;\n"
                + "      padding: 0.25rem 0.6rem;\n"
                + "      border-radius: 6px;\n"
                + "      background: rgba(255, 255, 255, 0.05);\n"
                + "      color: var(--text-muted);\n"
                + "      cursor: pointer;\n"
                + "      border: 1px solid transparent;\n"
                + "    }\n"
                + "    .filter-pill.active {\n"
                + "      background: rgba(59, 130, 246, 0.15);\n"
                + "      color: var(--primary);\n"
                + "      border-color: var(--primary);\n"
                + "    }\n"
                + "    .results-list {\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 0.75rem;\n"
                + "    }\n"
                + "    .result-item {\n"
                + "      background: var(--bg-surface);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      border-radius: 12px;\n"
                + "      padding: 1.25rem;\n"
                + "      transition: border-color 0.2s, transform 0.2s;\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 0.4rem;\n"
                + "    }\n"
                + "    .result-item:hover {\n"
                + "      border-color: rgba(59, 130, 246, 0.4);\n"
                + "      transform: translateY(-1px);\n"
                + "    }\n"
                + "    .result-meta {\n"
                + "      display: flex;\n"
                + "      align-items: center;\n"
                + "      gap: 0.6rem;\n"
                + "      font-size: 0.75rem;\n"
                + "    }\n"
                + "    .engine-badge {\n"
                + "      padding: 0.15rem 0.5rem;\n"
                + "      border-radius: 4px;\n"
                + "      font-weight: 600;\n"
                + "      font-size: 0.7rem;\n"
                + "    }\n"
                + "    .engine-badge.google { background: rgba(59, 130, 246, 0.2); color: #60a5fa; }\n"
                + "    .engine-badge.baidu { background: rgba(239, 68, 68, 0.2); color: #f87171; }\n"
                + "    .engine-badge.lycos { background: rgba(245, 158, 11, 0.2); color: #fbbf24; }\n"
                + "    .result-url {\n"
                + "      color: var(--text-muted);\n"
                + "      font-family: monospace;\n"
                + "      font-size: 0.75rem;\n"
                + "      word-break: break-all;\n"
                + "    }\n"
                + "    .result-title {\n"
                + "      font-size: 1.05rem;\n"
                + "      font-weight: 600;\n"
                + "      color: #93c5fd;\n"
                + "      text-decoration: none;\n"
                + "    }\n"
                + "    .result-title:hover { text-decoration: underline; }\n"
                + "    .result-preview {\n"
                + "      font-size: 0.85rem;\n"
                + "      color: #cbd5e1;\n"
                + "      line-height: 1.45;\n"
                + "    }\n"
                + "    .compare-grid {\n"
                + "      display: grid;\n"
                + "      grid-template-columns: 1fr 1fr;\n"
                + "      gap: 1rem;\n"
                + "    }\n"
                + "    @media (max-width: 768px) {\n"
                + "      .compare-grid { grid-template-columns: 1fr; }\n"
                + "    }\n"
                + "    .compare-col {\n"
                + "      background: var(--bg-surface);\n"
                + "      border: 1px solid var(--border-color);\n"
                + "      border-radius: 12px;\n"
                + "      padding: 1.25rem;\n"
                + "      display: flex;\n"
                + "      flex-direction: column;\n"
                + "      gap: 0.75rem;\n"
                + "    }\n"
                + "    .col-header {\n"
                + "      padding-bottom: 0.75rem;\n"
                + "      border-bottom: 1px solid var(--border-color);\n"
                + "    }\n"
                + "    .col-title {\n"
                + "      font-weight: 700;\n"
                + "      font-size: 1rem;\n"
                + "    }\n"
                + "    footer {\n"
                + "      margin-top: 2rem;\n"
                + "      padding: 2rem;\n"
                + "      border-top: 1px solid var(--border-color);\n"
                + "      text-align: center;\n"
                + "      font-size: 0.8rem;\n"
                + "      color: var(--text-muted);\n"
                + "    }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header>\n"
                + "    <div class=\"brand\">\n"
                + "      <div class=\"logo-badge\">JS</div>\n"
                + "      <div>\n"
                + "        <h1>JSearch</h1>\n"
                + "        <p>1999-2002 Archive & Reference Reimplementation</p>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "    <div class=\"nav-tags\">\n"
                + "      <span class=\"badge-pill\">JDK 11+ Cleanroom</span>\n"
                + "      <span class=\"badge-pill\">Zero Dependencies</span>\n"
                + "      <span class=\"badge-pill\">GB8567-88 Standard Docs</span>\n"
                + "    </div>\n"
                + "  </header>\n"
                + "\n"
                + "  <main>\n"
                + "    <div class=\"hero\">\n"
                + "      <h2>\"Turns Search Engines into FIND Engines\"</h2>\n"
                + "      <p>Concurrent search fan-out, sliding-window block scraping, and lock-free thread-safe deduplication.</p>\n"
                + "    </div>\n"
                + "\n"
                + "    <div class=\"control-card\">\n"
                + "      <div class=\"search-box\">\n"
                + "        <div class=\"search-input-wrapper\">\n"
                + "          <input type=\"text\" id=\"queryInput\" class=\"search-input\" value=\"java\" placeholder=\"Enter search query (e.g. java, applet, search)...\">\n"
                + "        </div>\n"
                + "        <button id=\"searchBtn\" class=\"btn-search\" onclick=\"performSearch()\">\n"
                + "          <span>Search Engines</span>\n"
                + "        </button>\n"
                + "      </div>\n"
                + "\n"
                + "      <div class=\"quick-queries\">\n"
                + "        <span>Archive Fixtures:</span>\n"
                + "        <span class=\"query-tag\" onclick=\"setQuery('java')\">java (2001 Snapshot)</span>\n"
                + "        <span class=\"query-tag\" onclick=\"setQuery('applet')\">applet</span>\n"
                + "        <span class=\"query-tag\" onclick=\"setQuery('search engine')\">search engine</span>\n"
                + "        <span class=\"query-tag\" onclick=\"setQuery('open source')\">open source</span>\n"
                + "      </div>\n"
                + "\n"
                + "      <div class=\"options-grid\">\n"
                + "        <div class=\"option-group\">\n"
                + "          <h4>Architecture Mode</h4>\n"
                + "          <div class=\"mode-pills\">\n"
                + "            <div class=\"mode-pill active\" id=\"pillModern\" onclick=\"setMode('modern')\">Modern Ref</div>\n"
                + "            <div class=\"mode-pill\" id=\"pillLegacy\" onclick=\"setMode('legacy')\">1999 Buggy</div>\n"
                + "            <div class=\"mode-pill\" id=\"pillCompare\" onclick=\"setMode('compare')\">Side-by-Side</div>\n"
                + "          </div>\n"
                + "        </div>\n"
                + "        <div class=\"option-group\">\n"
                + "          <h4>Search Engines</h4>\n"
                + "          <div class=\"engine-chips\" id=\"engineCheckboxes\">\n"
                + "            <label class=\"engine-checkbox\"><input type=\"checkbox\" value=\"Google\" checked> Google (English)</label>\n"
                + "            <label class=\"engine-checkbox\"><input type=\"checkbox\" value=\"GB_Chinese Google\" checked> GB_Chinese Google</label>\n"
                + "            <label class=\"engine-checkbox\"><input type=\"checkbox\" value=\"Baidu\" checked> Baidu (Chinese)</label>\n"
                + "            <label class=\"engine-checkbox\"><input type=\"checkbox\" value=\"Lycos\" checked> Lycos (English)</label>\n"
                + "          </div>\n"
                + "        </div>\n"
                + "        <div class=\"option-group\">\n"
                + "          <h4>Search Max Speed (Concurrency)</h4>\n"
                + "          <input type=\"range\" id=\"speedSlider\" min=\"1\" max=\"8\" value=\"4\" style=\"width:100%; accent-color: var(--primary);\" oninput=\"updateSpeedLabel(this.value)\">\n"
                + "          <div style=\"font-size:0.75rem; color:var(--text-muted); margin-top:0.25rem;\">Workers: <span id=\"speedVal\">4</span> threads (historical smsCh)</div>\n"
                + "        </div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "\n"
                + "    <div class=\"alert-panel\" id=\"legacyAlert\">\n"
                + "      <strong>⚠️ 2002 Architecture Defect Emulation Active:</strong><br>\n"
                + "      In <code>Sources/JSApplet.java:getEngData()</code>, engines were loaded into a <code>Hashtable</code> keyed on their URL (<code>engDataHt.put(url, data)</code>). Because English Google and Chinese Google both used <code>http://www.google.com/</code>, the second engine silently overwrote the first in the hashtable. <em>Chinese Google vanished from the search list!</em>\n"
                + "    </div>\n"
                + "\n"
                + "    <div class=\"metrics-bar\">\n"
                + "      <div class=\"metric-card\">\n"
                + "        <div class=\"metric-value highlight\" id=\"metricUnique\">0</div>\n"
                + "        <div class=\"metric-label\">Unique Deduplicated</div>\n"
                + "      </div>\n"
                + "      <div class=\"metric-card\">\n"
                + "        <div class=\"metric-value\" id=\"metricRaw\">0</div>\n"
                + "        <div class=\"metric-label\">Total Scraped Blocks</div>\n"
                + "      </div>\n"
                + "      <div class=\"metric-card\">\n"
                + "        <div class=\"metric-value warn\" id=\"metricDupes\">0</div>\n"
                + "        <div class=\"metric-label\">Duplicates Dropped</div>\n"
                + "      </div>\n"
                + "      <div class=\"metric-card\">\n"
                + "        <div class=\"metric-value\" id=\"metricEngines\">0</div>\n"
                + "        <div class=\"metric-label\">Engines Queried</div>\n"
                + "      </div>\n"
                + "      <div class=\"metric-card\">\n"
                + "        <div class=\"metric-value\" id=\"metricDuration\">0ms</div>\n"
                + "        <div class=\"metric-label\">Search Latency</div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "\n"
                + "    <div class=\"results-section\" id=\"standardView\">\n"
                + "      <div class=\"results-header\">\n"
                + "        <h3>Aggregated Results (<span id=\"resultCount\">0</span>)</h3>\n"
                + "        <div class=\"filter-pills\" id=\"engineFilters\">\n"
                + "          <span class=\"filter-pill active\" onclick=\"filterByEngine('all')\">All Engines</span>\n"
                + "          <span class=\"filter-pill\" onclick=\"filterByEngine('Google')\">Google</span>\n"
                + "          <span class=\"filter-pill\" onclick=\"filterByEngine('Baidu')\">Baidu</span>\n"
                + "          <span class=\"filter-pill\" onclick=\"filterByEngine('Lycos')\">Lycos</span>\n"
                + "        </div>\n"
                + "      </div>\n"
                + "      <div class=\"results-list\" id=\"resultsContainer\"></div>\n"
                + "    </div>\n"
                + "\n"
                + "    <div class=\"results-section\" id=\"compareView\" style=\"display:none;\">\n"
                + "      <h3>Side-by-Side Archaeology Analysis</h3>\n"
                + "      <div class=\"compare-grid\">\n"
                + "        <div class=\"compare-col\">\n"
                + "          <div class=\"col-header\">\n"
                + "            <div class=\"col-title\" style=\"color:var(--warning);\">1999-2002 Original (URL-Key Bug)</div>\n"
                + "            <p style=\"font-size:0.75rem; color:var(--text-muted);\">Hashtable keyed on URL; drops Chinese Google</p>\n"
                + "          </div>\n"
                + "          <div id=\"legacyColResults\" class=\"results-list\"></div>\n"
                + "        </div>\n"
                + "        <div class=\"compare-col\">\n"
                + "          <div class=\"col-header\">\n"
                + "            <div class=\"col-title\" style=\"color:var(--success);\">2026 Modern Reference (Cleanroom)</div>\n"
                + "            <p style=\"font-size:0.75rem; color:var(--text-muted);\">Identity is (name, category); all engines survive</p>\n"
                + "          </div>\n"
                + "          <div id=\"modernColResults\" class=\"results-list\"></div>\n"
                + "        </div>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </main>\n"
                + "\n"
                + "  <footer>\n"
                + "    JSearch Historical Archive (1999-2002) &amp; Modern Reference Suite. Licensed under GPL-2.0. Author: Hunt Lin.\n"
                + "  </footer>\n"
                + "\n"
                + "  <script>\n"
                + "    let currentMode = 'modern';\n"
                + "    let lastResults = [];\n"
                + "    let activeFilter = 'all';\n"
                + "\n"
                + "    function setMode(mode) {\n"
                + "      currentMode = mode;\n"
                + "      document.getElementById('pillModern').className = 'mode-pill' + (mode === 'modern' ? ' active' : '');\n"
                + "      document.getElementById('pillLegacy').className = 'mode-pill' + (mode === 'legacy' ? ' active legacy' : '');\n"
                + "      document.getElementById('pillCompare').className = 'mode-pill' + (mode === 'compare' ? ' active' : '');\n"
                + "      document.getElementById('legacyAlert').className = 'alert-panel' + (mode === 'legacy' ? ' active' : '');\n"
                + "      performSearch();\n"
                + "    }\n"
                + "\n"
                + "    function setQuery(q) {\n"
                + "      document.getElementById('queryInput').value = q;\n"
                + "      performSearch();\n"
                + "    }\n"
                + "\n"
                + "    function updateSpeedLabel(val) {\n"
                + "      document.getElementById('speedVal').innerText = val;\n"
                + "    }\n"
                + "\n"
                + "    document.getElementById('queryInput').addEventListener('keydown', (e) => {\n"
                + "      if (e.key === 'Enter') performSearch();\n"
                + "    });\n"
                + "\n"
                + "    function getSelectedEngines() {\n"
                + "      const boxes = document.querySelectorAll('#engineCheckboxes input:checked');\n"
                + "      return Array.from(boxes).map(b => b.value).join(',');\n"
                + "    }\n"
                + "\n"
                + "    async function performSearch() {\n"
                + "      const query = document.getElementById('queryInput').value.trim() || 'java';\n"
                + "      const speed = document.getElementById('speedSlider').value;\n"
                + "      const engines = getSelectedEngines();\n"
                + "\n"
                + "      if (currentMode === 'compare') {\n"
                + "        document.getElementById('standardView').style.display = 'none';\n"
                + "        document.getElementById('compareView').style.display = 'block';\n"
                + "        const resp = await fetch(`/api/compare?q=${encodeURIComponent(query)}&concurrency=${speed}`);\n"
                + "        const data = await resp.json();\n"
                + "        renderCompare(data);\n"
                + "      } else {\n"
                + "        document.getElementById('standardView').style.display = 'flex';\n"
                + "        document.getElementById('compareView').style.display = 'none';\n"
                + "        const resp = await fetch(`/api/search?q=${encodeURIComponent(query)}&mode=${currentMode}&engines=${encodeURIComponent(engines)}&concurrency=${speed}`);\n"
                + "        const data = await resp.json();\n"
                + "        renderStandard(data);\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    function renderStandard(data) {\n"
                + "      lastResults = data.results || [];\n"
                + "      document.getElementById('metricUnique').innerText = data.totalUnique;\n"
                + "      document.getElementById('metricRaw').innerText = data.totalRaw;\n"
                + "      document.getElementById('metricDupes').innerText = data.duplicatesDropped;\n"
                + "      document.getElementById('metricEngines').innerText = data.totalEngines;\n"
                + "      document.getElementById('metricDuration').innerText = data.durationMs + 'ms';\n"
                + "      document.getElementById('resultCount').innerText = lastResults.length;\n"
                + "      renderFilteredResults();\n"
                + "    }\n"
                + "\n"
                + "    function filterByEngine(engineName) {\n"
                + "      activeFilter = engineName;\n"
                + "      document.querySelectorAll('.filter-pill').forEach(p => {\n"
                + "        p.className = 'filter-pill' + (p.innerText.toLowerCase().includes(engineName.toLowerCase()) ? ' active' : '');\n"
                + "      });\n"
                + "      renderFilteredResults();\n"
                + "    }\n"
                + "\n"
                + "    function renderFilteredResults() {\n"
                + "      const container = document.getElementById('resultsContainer');\n"
                + "      container.innerHTML = '';\n"
                + "      const filtered = activeFilter === 'all'\n"
                + "        ? lastResults\n"
                + "        : lastResults.filter(r => r.engine.toLowerCase().includes(activeFilter.toLowerCase()));\n"
                + "\n"
                + "      if (filtered.length === 0) {\n"
                + "        container.innerHTML = '<div style=\"text-align:center; padding:2rem; color:var(--text-muted);\">No results found.</div>';\n"
                + "        return;\n"
                + "      }\n"
                + "\n"
                + "      filtered.forEach(r => {\n"
                + "        const card = document.createElement('div');\n"
                + "        card.className = 'result-item';\n"
                + "        const badgeClass = r.engine.toLowerCase().includes('google') ? 'google' : (r.engine.toLowerCase().includes('baidu') ? 'baidu' : 'lycos');\n"
                + "        card.innerHTML = `\n"
                + "          <div class=\"result-meta\">\n"
                + "            <span class=\"engine-badge ${badgeClass}\">${escapeHtml(r.engine)}</span>\n"
                + "            <span class=\"result-url\">${escapeHtml(r.url)}</span>\n"
                + "          </div>\n"
                + "          <a class=\"result-title\" href=\"${escapeHtml(r.url)}\" target=\"_blank\" rel=\"noopener noreferrer\">${escapeHtml(r.title || r.url)}</a>\n"
                + "          <p class=\"result-preview\">${escapeHtml(r.preview || '')}</p>\n"
                + "        `;\n"
                + "        container.appendChild(card);\n"
                + "      });\n"
                + "    }\n"
                + "\n"
                + "    function renderCompare(data) {\n"
                + "      document.getElementById('metricUnique').innerText = data.modern.totalUnique;\n"
                + "      document.getElementById('metricRaw').innerText = data.modern.totalScraped;\n"
                + "      document.getElementById('metricDupes').innerText = data.modern.totalScraped - data.modern.totalUnique;\n"
                + "      document.getElementById('metricEngines').innerText = data.modern.engineCount;\n"
                + "      document.getElementById('metricDuration').innerText = data.modern.durationMs + 'ms';\n"
                + "\n"
                + "      const legCol = document.getElementById('legacyColResults');\n"
                + "      const modCol = document.getElementById('modernColResults');\n"
                + "      legCol.innerHTML = '';\n"
                + "      modCol.innerHTML = '';\n"
                + "\n"
                + "      const lostDiv = document.createElement('div');\n"
                + "      lostDiv.style.cssText = 'background:rgba(239,68,68,0.15); border:1px solid var(--danger); border-radius:8px; padding:0.75rem; font-size:0.8rem; color:#fca5a5;';\n"
                + "      lostDiv.innerHTML = `<strong>Overwritten &amp; Vanished Engine:</strong><br><code>${escapeHtml(data.lostEngines.join(', '))}</code><br>Discarded by URL-key Hashtable collision.`;\n"
                + "      legCol.appendChild(lostDiv);\n"
                + "\n"
                + "      (data.legacy.results || []).forEach(r => renderSmallCard(legCol, r));\n"
                + "      (data.modern.results || []).forEach(r => renderSmallCard(modCol, r));\n"
                + "    }\n"
                + "\n"
                + "    function renderSmallCard(parent, r) {\n"
                + "      const item = document.createElement('div');\n"
                + "      item.className = 'result-item';\n"
                + "      item.innerHTML = `\n"
                + "        <div class=\"result-meta\"><span class=\"engine-badge google\">${escapeHtml(r.engine)}</span></div>\n"
                + "        <div style=\"font-size:0.9rem; font-weight:600; color:#93c5fd;\">${escapeHtml(r.title || r.url)}</div>\n"
                + "        <div class=\"result-url\">${escapeHtml(r.url)}</div>\n"
                + "      `;\n"
                + "      parent.appendChild(item);\n"
                + "    }\n"
                + "\n"
                + "    function escapeHtml(str) {\n"
                + "      if (!str) return '';\n"
                + "      return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');\n"
                + "    }\n"
                + "\n"
                + "    window.onload = performSearch;\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }
}
