package jsearch;

import java.util.Objects;

/**
 * One search engine: its identity, the URL template to query, and the markers
 * that delimit a result block in the returned HTML.
 *
 * <p>The identity of an engine is {@code (name, category)} &mdash; <em>not</em> its
 * URL. That distinction is the whole reason this type exists. The original
 * JSearch stored engines in a {@code Hashtable} keyed on the URL, so two engines
 * sharing a URL (Chinese Google and English Google, both pointing at
 * {@code http://www.google.com/}) silently collapsed into one and one of them
 * vanished from the UI. Keying a map on a value that is not unique destroys
 * identity; there is nothing downstream that can recover it.
 */
public final class Engine {

    /** What the original code called {@code srchBlkB} / {@code srchBlkE}. */
    public static final class Block {

        private final String start;
        private final String end;

        public Block(String start, String end) {
            this.start = Objects.requireNonNull(start, "block start");
            this.end = Objects.requireNonNull(end, "block end");
        }

        public String start() {
            return start;
        }

        public String end() {
            return end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Block)) {
                return false;
            }
            Block other = (Block) o;
            return start.equals(other.start) && end.equals(other.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return start + ".." + end;
        }
    }

    private final String name;
    private final String category;
    private final String urlTemplate;
    private final Block resultBlock;

    public Engine(String name, String category, String urlTemplate, Block resultBlock) {
        this.name = requireText(name, "name");
        this.category = requireText(category, "category");
        this.urlTemplate = requireText(urlTemplate, "urlTemplate");
        this.resultBlock = Objects.requireNonNull(resultBlock, "resultBlock");
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public String urlTemplate() {
        return urlTemplate;
    }

    public Block resultBlock() {
        return resultBlock;
    }

    /**
     * Builds the concrete URL for a query. {@code ^} is replaced by the
     * URL-encoded query; {@code `} is replaced by the paging level digit, which
     * is what the original called "level" and substituted with a backtick.
     */
    public String urlFor(String query, int level) {
        String encoded = URLEncoding.encode(query);
        return urlTemplate
                .replace('`', (char) ('0' + level))
                .replace("^", encoded);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Engine)) {
            return false;
        }
        Engine other = (Engine) o;
        // URL deliberately excluded: it is not part of identity.
        return name.equals(other.name) && category.equals(other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, category);
    }

    @Override
    public String toString() {
        return name + " [" + category + "]";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
