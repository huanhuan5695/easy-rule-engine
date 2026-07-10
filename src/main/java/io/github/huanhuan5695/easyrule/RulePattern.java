package io.github.huanhuan5695.easyrule;

import java.util.Objects;

/**
 * Immutable description of a template rule.
 *
 * <p>A rule belongs to a category and has a caller-defined template id. The
 * pattern may contain fixed text and slot references. Pattern syntax is
 * validated by the {@link TemplateMatcher.Builder} that registers the rule.
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
     * @param pattern slots separated by the configured sequence separator
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
     * @param pattern slots separated by the configured sequence separator
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RulePattern)) {
            return false;
        }
        RulePattern that = (RulePattern) other;
        return priority == that.priority
                && category.equals(that.category)
                && templateId.equals(that.templateId)
                && pattern.equals(that.pattern)
                && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, templateId, pattern, mode, priority);
    }

    @Override
    public String toString() {
        return "RulePattern{"
                + "category='" + category + '\''
                + ", templateId='" + templateId + '\''
                + ", pattern='" + pattern + '\''
                + ", mode=" + mode
                + ", priority=" + priority
                + '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

}
