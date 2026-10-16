package sh.okx.civmodern.common.gui;

import java.util.Locale;

/** The manager screens' search: a case-insensitive substring match, nothing fancier. */
public final class SearchFilter {

    private SearchFilter() {
    }

    /** The search box's text as matched against: trimmed and lower-cased. Empty matches everything. */
    public static String normalise(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    /** @param query already {@linkplain #normalise normalised} */
    public static boolean matches(String text, String query) {
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
    }
}
