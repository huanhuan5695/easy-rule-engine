import java.util.Objects;

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

    public static RulePattern exact(String category, String templateId, String pattern) {
        return exact(category, templateId, pattern, 0);
    }

    public static RulePattern exact(String category, String templateId, String pattern, int priority) {
        return new RulePattern(category, templateId, pattern, PatternMode.EXACT, priority);
    }

    public static RulePattern slotSequence(String category, String templateId, String pattern) {
        return slotSequence(category, templateId, pattern, 0);
    }

    public static RulePattern slotSequence(String category, String templateId, String pattern, int priority) {
        return new RulePattern(category, templateId, pattern, PatternMode.SLOT_SEQUENCE, priority);
    }

    public static RulePattern of(String category, String templateId, String pattern, PatternMode mode) {
        return of(category, templateId, pattern, mode, 0);
    }

    public static RulePattern of(String category, String templateId, String pattern, PatternMode mode, int priority) {
        return new RulePattern(category, templateId, pattern, mode, priority);
    }

    public String category() {
        return category;
    }

    public String templateId() {
        return templateId;
    }

    public String pattern() {
        return pattern;
    }

    public PatternMode mode() {
        return mode;
    }

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
