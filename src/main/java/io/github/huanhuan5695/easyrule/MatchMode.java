package io.github.huanhuan5695.easyrule;

/**
 * Per-call strategy used by {@link TemplateMatcher#match(String, MatchOptions)}.
 */
public enum MatchMode {
    /**
     * Run exact templates first. If any exact template matches, return those results
     * without running slot-sequence templates.
     */
    EXACT_THEN_SLOT_SEQUENCE,

    /**
     * Run only exact templates.
     */
    EXACT_ONLY,

    /**
     * Run only slot-sequence templates.
     */
    SLOT_SEQUENCE_ONLY,

    /**
     * Run exact and slot-sequence templates, then sort and limit the combined result set.
     */
    ALL
}
