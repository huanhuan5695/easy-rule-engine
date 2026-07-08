package io.github.huanhuan5695.easyrule;

/**
 * Compile-time matching mode for a {@link RulePattern}.
 */
public enum PatternMode {
    /**
     * Pattern contains fixed text and optional slots. The whole input must match the pattern.
     */
    EXACT,

    /**
     * Pattern contains only slots separated by underscores. Slot values must appear in order.
     */
    SLOT_SEQUENCE
}
