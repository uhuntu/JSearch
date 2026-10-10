package jsearch;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The network {@link PageFetcher}: a plain HTTP GET with timeouts.
 *
 * <p>Headers are registered per URL prefix, so an API key meant for one provider
 * is never sent to another when several engines share one fetcher.
 *
 * <p>Every request carries a User-Agent that says who is asking. The JDK's
 * default ("Java/17") is refused outright by some APIs &mdash; Wikipedia answers
 * 403 to it and 200 to an identified client &mdash; and an unidentified client
 * is the one thing a public API has grounds to refuse.
 *
 * <p>Interrupting the calling thread (what {@link SearchService#cancelAll} does)
 * aborts the request in flight and surfaces as {@link InterruptedIOException}
 * with the interrupt flag restored.
 */
public final class HttpPageFetcher implements PageFetcher {

    /** Identifies this client; override per URL prefix with {@link #withHeader}. */
    static final String USER_AGENT = "JSearch/1.0 (metasearch reference implementation)";

    private final HttpClient client;
    private final Duration requestTimeout;
    private final Map<String, Map<String, String>> headersByPrefix = new LinkedHashMap<>();

    public HttpPageFetcher(Duration connectTimeout, Duration requestTimeout) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    public HttpPageFetcher() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(15));
    }

    /**
     * Sends {@code name: value} on every request whose URL starts with
     * {@code urlPrefix}. A registered header <em>replaces</em> the default for
     * that prefix rather than joining it: {@code header()} appends, and two
     * User-Agent lines on one request is a request an API may refuse.
     */
    public HttpPageFetcher withHeader(String urlPrefix, String name, String value) {
        headersByPrefix.computeIfAbsent(urlPrefix, k -> new LinkedHashMap<>()).put(name, value);
        return this;
    }

    @Override
    public String fetch(String url) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .header("Accept", "application/json, text/html;q=0.8")
                .header("User-Agent", USER_AGENT);
        headersByPrefix.forEach((prefix, headers) -> {
            if (url.startsWith(prefix)) {
                headers.forEach(request::setHeader);
            }
        });
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            InterruptedIOException io = new InterruptedIOException("interrupted fetching " + url);
            io.initCause(interrupted);
            throw io;
        }
    }
}
