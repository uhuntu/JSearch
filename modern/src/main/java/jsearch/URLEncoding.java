package jsearch;

/**
 * URL-encoding, extracted so the scraping and query-building paths can be tested
 * without a network. The original inlined {@code URLEncoder.encode} at the call
 * site; keeping it here means a fixture test never needs to reach {@code java.net}.
 */
final class URLEncoding {

    private URLEncoding() {
    }

    static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            // UTF-8 is guaranteed present on every JVM.
            throw new AssertionError(impossible);
        }
    }
}
