package io.github.huanhuan5695.easyrule;

import java.util.Objects;

/**
 * Immutable description of a template rule.
 *
 * <p>A rule belongs to a category and has a caller-defined template id. The
 * pattern may contain fixed text and slot references such as {@code [city]}.
 */
public final class RulePattern {
    private final String category;
    private final String templateId;
    private final String pattern;
    private final PatternMode mode;
    private final int priority;

    private RulePattern(String category, String templateId, String pattern, PatternMode mode, int priority) {
        this.category = requireText(category, "category");
        this.templateId = requireText(templateId, "templateId");
        this.pattern = requireText(pattern, "pattern");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.priority = priority;
        if (mode == PatternMode.SLOT_SEQUENCE) {
            validateSlotSequencePattern(pattern);
        }
    }

    /**
     * Creates an exact pattern with priority {@code 0}.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern exact pattern text
     * @return rule pattern
     */
    public static RulePattern exact(String category, String templateId, String pattern) {
        return exact(category, templateId, pattern, 0);
    }

    /**
     * Creates an exact pattern. The whole input must match the compiled pattern.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern exact pattern text
     * @param priority higher values sort before lower values
     * @return rule pattern
     */
    public static RulePattern exact(String category, String templateId, String pattern, int priority) {
        return new RulePattern(category, templateId, pattern, PatternMode.EXACT, priority);
    }

    /**
     * Creates a slot-sequence pattern with priority {@code 0}.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern slots separated by underscores, for example {@code [like]_[song]}
     * @return rule pattern
     */
    public static RulePattern slotSequence(String category, String templateId, String pattern) {
        return slotSequence(category, templateId, pattern, 0);
    }

    /**
     * Creates a slot-sequence pattern. The input may contain extra text, but
     * the configured slots must appear in pattern order.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern slots separated by underscores, for example {@code [like]_[song]}
     * @param priority higher values sort before lower values
     * @return rule pattern
     */
    public static RulePattern slotSequence(String category, String templateId, String pattern, int priority) {
        return new RulePattern(category, templateId, pattern, PatternMode.SLOT_SEQUENCE, priority);
    }

    /**
     * Creates a pattern with an explicit mode and priority {@code 0}.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern pattern text
     * @param mode matching mode
     * @return rule pattern
     */
    public static RulePattern of(String category, String templateId, String pattern, PatternMode mode) {
        return of(category, templateId, pattern, mode, 0);
    }

    /**
     * Creates a pattern with an explicit mode and priority.
     *
     * @param category rule category returned in match results
     * @param templateId unique template id returned in match results
     * @param pattern pattern text
     * @param mode matching mode
     * @param priority higher values sort before lower values
     * @return rule pattern
     */
    public static RulePattern of(String category, String templateId, String pattern, PatternMode mode, int priority) {
        return new RulePattern(category, templateId, pattern, mode, priority);
    }

    /**
     * Returns the rule category.
     *
     * @return category
     */
    public String category() {
        return category;
    }

    /**
     * Returns the caller-defined template id.
     *
     * @return template id
     */
    public String templateId() {
        return templateId;
    }

    /**
     * Returns the original pattern text.
     *
     * @return pattern text
     */
    public String pattern() {
        return pattern;
    }

    /**
     * Returns the pattern mode.
     *
     * @return pattern mode
     */
    public PatternMode mode() {
        return mode;
    }

    /**
     * Returns the pattern priority. Higher values sort before lower values.
     *
     * @return priority
     */
    public int priority() {
        return priority;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void validateSlotSequencePattern(String pattern) {
        boolean inSlot = false;
        boolean hasSlot = false;
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '[') {
                if (inSlot) {
                    throw new IllegalArgumentException("nested slot is not allowed: " + pattern);
                }
                inSlot = true;
            } else if (current == ']') {
                if (!inSlot) {
                    throw new IllegalArgumentException("unopened slot in pattern: " + pattern);
                }
                inSlot = false;
                hasSlot = true;
            } else if (!inSlot && current != '_') {
                throw new IllegalArgumentException(
                        "slot sequence pattern can only contain slots and '_' separators: " + pattern);
            }
        }
        if (inSlot) {
            throw new IllegalArgumentException("unclosed slot in pattern: " + pattern);
        }
        if (!hasSlot) {
            throw new IllegalArgumentException("slot sequence pattern must contain at least one slot");
        }
    }
}
