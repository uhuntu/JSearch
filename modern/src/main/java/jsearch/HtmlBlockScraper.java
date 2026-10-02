package jsearch;

import java.util.ArrayList;
import java.util.List;

/**
 * The original sliding-window algorithm, extracted and made testable.
 *
 * <p>JSearch pushed a 4-character window forward one character at a time,
 * comparing it against the block markers, then parsed a block by looking for
 * {@code ref=} (URL), {@code </a>} (title) and the end marker (preview), dropping
 * anything between {@code <} and {@code >}. That algorithm is preserved here on
 * purpose: the point of the exercise is the surrounding design, not rewriting
 * the parser.
 *
 * <p>One change is deliberate and is called out because it fixes a real trap.
 * The original's window was hard-wired to four characters, which means any marker
 * shorter than four could never match &mdash; the shipped engine file uses
 * {@code <p><} and {@code .</d} precisely because they are four wide. Since the
 * whole page is available in memory here, matching is expressed as
 * "does the marker start at this position" instead of "does a 4-character window
 * equal this", which removes the constraint rather than working around it.
 *
 * <p>Markers are matched at every position, including inside a tag, which is what
 * the original's window did and what the shipped Baidu data depends on: its end
 * marker {@code ble>} only ever appears within {@code </table>}.
 */
public final class HtmlBlockScraper implements BlockScraper {

    private static final String URL_MARKER = "ref=";
    private static final String TITLE_END_MARKER = "</a>";

    @Override
    public List<SearchResult> scrape(Engine engine, String html) {
        List<SearchResult> results = new ArrayList<>();
        Cursor cursor = new Cursor(html);
        String blockStart = engine.resultBlock().start();
        String blockEnd = engine.resultBlock().end();

        while (cursor.hasNext()) {
            if (cursor.matches(blockStart)) {
                SearchResult result = readBlock(engine, cursor, blockEnd);
                if (result != null) {
                    results.add(result);
                }
            } else {
                cursor.advance();
            }
        }
        return results;
    }

    private static SearchResult readBlock(Engine engine, Cursor cursor, String blockEnd) {
        String url = readUrl(cursor);
        String title = readTitle(cursor);
        String preview = readPreview(cursor, blockEnd);
        return new SearchResult(engine, url, title, preview);
    }

    /** Consumes through {@code ref=}, then the URL up to whitespace or {@code >}. */
    private static String readUrl(Cursor cursor) {
        StringBuilder url = new StringBuilder();
        while (!cursor.matches(URL_MARKER)) {
            if (!cursor.advance()) {
                return url.toString();
            }
        }
        // Consume the whole marker, not one character: the original's window had
        // already absorbed all four characters of "ref=" by the time it compared.
        for (int i = 0; i < URL_MARKER.length(); i++) {
            if (!cursor.advance()) {
                return url.toString();
            }
        }
        while (cursor.current() != '>' && cursor.current() != ' ') {
            if (cursor.current() != '"') {
                url.append(cursor.current());
            }
            if (!cursor.advance()) {
                break;
            }
        }
        while (cursor.current() != '>') {   // tolerate early exit on whitespace
            if (!cursor.advance()) {
                break;
            }
        }
        return url.toString();
    }

    /** Title: text up to {@code </a>}, with {@code <...>} tags stripped. */
    private static String readTitle(Cursor cursor) {
        StringBuilder title = new StringBuilder();
        while (!cursor.matches(TITLE_END_MARKER)) {
            if (cursor.current() == '<') {
                while (cursor.current() != '>') {
                    if (!cursor.advance()) {
                        break;
                    }
                }
            }
            if (cursor.current() != '>' && cursor.current() != ' ') {
                title.append(cursor.current());
            }
            if (!cursor.advance()) {
                break;
            }
        }
        return title.toString();
    }

    /** Preview: text up to the end marker, with tags and newlines stripped. */
    private static String readPreview(Cursor cursor, String blockEnd) {
        StringBuilder preview = new StringBuilder();
        while (!cursor.matches(blockEnd)) {
            if (cursor.current() == '<') {
                // Skip to the end of the tag, but never past an end marker that
                // starts inside it. The shipped Baidu end marker is "ble>", and it
                // only ever occurs as part of "</table>"; jumping straight to '>'
                // stepped over it, so the first Baidu block ran to the end of the
                // page and swallowed the other nine. The original's character
                // window compared at every position, tags included, so it did see
                // it — this is a regression in the port, fixed here.
                while (cursor.current() != '>' && !cursor.matches(blockEnd)) {
                    if (!cursor.advance()) {
                        break;
                    }
                }
                if (cursor.matches(blockEnd)) {
                    break;              // the marker starts inside this tag
                }
            }
            if (cursor.current() != '>' && cursor.current() != '\r' && cursor.current() != '\n') {
                preview.append(cursor.current());
            }
            if (!cursor.advance()) {
                break;
            }
        }
        return preview.toString();
    }

    /** A position in a string, advanced one character at a time. */
    private static final class Cursor {

        private final String text;
        private int at;

        Cursor(String text) {
            this.text = text;
            this.at = 0;
        }

        boolean hasNext() {
            return at < text.length();
        }

        boolean advance() {
            if (at >= text.length()) {
                return false;
            }
            at++;
            return true;
        }

        char current() {
            return at < text.length() ? text.charAt(at) : '\0';
        }

        /** True if {@code marker} begins at the current position. */
        boolean matches(String marker) {
            return marker != null && !marker.isEmpty() && text.startsWith(marker, at);
        }
    }
}
