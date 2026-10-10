package jsearch;

import java.util.ArrayList;
import java.util.List;

/**
 * The check for a failure mode the archive never had to face: an engine that
 * wants you gone does not always return an error. Bing, after a few requests
 * from a server IP, stops failing and starts serving popular pages that have
 * nothing to do with the query &mdash; a response that parses perfectly and is
 * about nothing.
 *
 * <p>The rule: if no result mentions any significant term of the query, the
 * response is treated as a block page and dropped, rather than shown as
 * results. It is the same rule the JSearXNG descendant applies, so the two
 * implementations can be compared against each other.
 */
public final class RelevanceCheck {

    private RelevanceCheck() {
    }

    /**
     * The query's significant tokens: lowercased, split on non-word characters,
     * short ones dropped &mdash; "go" and "ai" match half the web and would
     * pass anything.
     */
    public static List<String> tokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) {
            return tokens;
        }
        for (String token : query.toLowerCase().split("\\W+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Whether the hits are about the query.
     *
     * <p>An empty token list (a query of only short words) or no hits at all
     * returns true: there is nothing to reject, and rejecting everything would
     * silence engines that legitimately found nothing.
     */
    public static boolean relevant(String query, List<SearchResult> hits) {
        List<String> tokens = tokens(query);
        if (tokens.isEmpty() || hits == null || hits.isEmpty()) {
            return true;
        }
        for (SearchResult hit : hits) {
            String blob = (hit.title() + " " + hit.preview() + " " + hit.url()).toLowerCase();
            for (String token : tokens) {
                if (blob.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }
}
