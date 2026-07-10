package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable syntax definition used to parse rule pattern strings.
 *
 * <p>One syntax is configured per {@link TemplateMatcher.Builder}. Tokens may
 * contain multiple characters. The default syntax preserves the original
 * {@code [slot]}, {@code (a|b)?}, and {@code _} notation.
 */
public final class PatternSyntax {
    private static final PatternSyntax DEFAULT = builder().build();

    private final String slotStart;
    private final String slotEnd;
    private final String groupStart;
    private final String groupEnd;
    private final String alternative;
    private final String optional;
    private final String sequenceSeparator;
    private final String escape;
    private final List<String> tokensByLength;

    private PatternSyntax(Builder builder) {
        this.slotStart = requireToken(builder.slotStart, "slotStart");
        this.slotEnd = requireToken(builder.slotEnd, "slotEnd");
        this.groupStart = requireToken(builder.groupStart, "groupStart");
        this.groupEnd = requireToken(builder.groupEnd, "groupEnd");
        this.alternative = requireToken(builder.alternative, "alternative");
        this.optional = requireToken(builder.optional, "optional");
        this.sequenceSeparator = requireToken(builder.sequenceSeparator, "sequenceSeparator");
        this.escape = requireToken(builder.escape, "escape");
        validatePairs();
        validateConflicts();
        this.tokensByLength = buildTokensByLength();
    }

    /**
     * Returns the shared default syntax.
     *
     * @return default pattern syntax
     */
    public static PatternSyntax defaultSyntax() {
        return DEFAULT;
    }

    /**
     * Creates a syntax builder initialized with the default tokens.
     *
     * @return syntax builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the slot opening token.
     *
     * @return slot opening token
     */
    public String slotStart() {
        return slotStart;
    }

    /**
     * Returns the slot closing token.
     *
     * @return slot closing token
     */
    public String slotEnd() {
        return slotEnd;
    }

    /**
     * Returns the alternative-group opening token.
     *
     * @return group opening token
     */
    public String groupStart() {
        return groupStart;
    }

    /**
     * Returns the alternative-group closing token.
     *
     * @return group closing token
     */
    public String groupEnd() {
        return groupEnd;
    }

    /**
     * Returns the token separating alternatives inside a group.
     *
     * @return alternative token
     */
    public String alternative() {
        return alternative;
    }

    /**
     * Returns the token that marks the preceding group as optional.
     *
     * @return optional token
     */
    public String optional() {
        return optional;
    }

    /**
     * Returns the token separating slots in a slot-sequence pattern.
     *
     * @return sequence separator token
     */
    public String sequenceSeparator() {
        return sequenceSeparator;
    }

    /**
     * Returns the token used to escape syntax tokens as literal text.
     *
     * @return escape token
     */
    public String escape() {
        return escape;
    }

    List<String> tokensByLength() {
        return tokensByLength;
    }

    private void validatePairs() {
        if (slotStart.equals(slotEnd)) {
            throw new IllegalArgumentException("slot start and end tokens must differ");
        }
        if (groupStart.equals(groupEnd)) {
            throw new IllegalArgumentException("group start and end tokens must differ");
        }
    }

    private void validateConflicts() {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("slotStart", slotStart);
        tokens.put("slotEnd", slotEnd);
        tokens.put("groupStart", groupStart);
        tokens.put("groupEnd", groupEnd);
        tokens.put("alternative", alternative);
        tokens.put("optional", optional);
        tokens.put("sequenceSeparator", sequenceSeparator);
        tokens.put("escape", escape);

        List<Map.Entry<String, String>> entries = new ArrayList<>(tokens.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                Map.Entry<String, String> first = entries.get(i);
                Map.Entry<String, String> second = entries.get(j);
                if (sharedClosingToken(first.getKey(), second.getKey(), first.getValue(), second.getValue())) {
                    continue;
                }
                if (first.getValue().startsWith(second.getValue())
                        || second.getValue().startsWith(first.getValue())) {
                    throw new IllegalArgumentException(
                            "pattern syntax tokens conflict: "
                                    + first.getKey() + "='" + first.getValue() + "', "
                                    + second.getKey() + "='" + second.getValue() + "'");
                }
            }
        }
    }

    private static boolean sharedClosingToken(
            String firstName,
            String secondName,
            String firstValue,
            String secondValue) {
        return firstValue.equals(secondValue)
                && ((firstName.equals("slotEnd") && secondName.equals("groupEnd"))
                || (firstName.equals("groupEnd") && secondName.equals("slotEnd")));
    }

    private List<String> buildTokensByLength() {
        List<String> tokens = new ArrayList<>();
        addUnique(tokens, slotStart);
        addUnique(tokens, slotEnd);
        addUnique(tokens, groupStart);
        addUnique(tokens, groupEnd);
        addUnique(tokens, alternative);
        addUnique(tokens, optional);
        addUnique(tokens, sequenceSeparator);
        addUnique(tokens, escape);
        tokens.sort((first, second) -> Integer.compare(second.length(), first.length()));
        return Collections.unmodifiableList(tokens);
    }

    private static void addUnique(List<String> tokens, String token) {
        if (!tokens.contains(token)) {
            tokens.add(token);
        }
    }

    private static String requireToken(String token, String name) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return token;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PatternSyntax)) {
            return false;
        }
        PatternSyntax that = (PatternSyntax) other;
        return slotStart.equals(that.slotStart)
                && slotEnd.equals(that.slotEnd)
                && groupStart.equals(that.groupStart)
                && groupEnd.equals(that.groupEnd)
                && alternative.equals(that.alternative)
                && optional.equals(that.optional)
                && sequenceSeparator.equals(that.sequenceSeparator)
                && escape.equals(that.escape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                slotStart,
                slotEnd,
                groupStart,
                groupEnd,
                alternative,
                optional,
                sequenceSeparator,
                escape);
    }

    @Override
    public String toString() {
        return "PatternSyntax{"
                + "slot='" + slotStart + "..." + slotEnd + '\''
                + ", group='" + groupStart + "..." + groupEnd + '\''
                + ", alternative='" + alternative + '\''
                + ", optional='" + optional + '\''
                + ", sequenceSeparator='" + sequenceSeparator + '\''
                + ", escape='" + escape + '\''
                + '}';
    }

    /** Builder for immutable {@link PatternSyntax} instances. */
    public static final class Builder {
        private String slotStart = "[";
        private String slotEnd = "]";
        private String groupStart = "(";
        private String groupEnd = ")";
        private String alternative = "|";
        private String optional = "?";
        private String sequenceSeparator = "_";
        private String escape = "\\";

        private Builder() {
        }

        /**
         * Sets slot opening and closing tokens.
         *
         * @param start slot opening token
         * @param end slot closing token
         * @return this builder
         */
        public Builder slot(String start, String end) {
            this.slotStart = start;
            this.slotEnd = end;
            return this;
        }

        /**
         * Sets alternative-group opening and closing tokens.
         *
         * @param start group opening token
         * @param end group closing token
         * @return this builder
         */
        public Builder group(String start, String end) {
            this.groupStart = start;
            this.groupEnd = end;
            return this;
        }

        /**
         * Sets the token separating alternatives inside a group.
         *
         * @param token alternative token
         * @return this builder
         */
        public Builder alternative(String token) {
            this.alternative = token;
            return this;
        }

        /**
         * Sets the token that marks the preceding group as optional.
         *
         * @param token optional token
         * @return this builder
         */
        public Builder optional(String token) {
            this.optional = token;
            return this;
        }

        /**
         * Sets the slot-sequence separator token.
         *
         * @param token sequence separator token
         * @return this builder
         */
        public Builder sequenceSeparator(String token) {
            this.sequenceSeparator = token;
            return this;
        }

        /**
         * Sets the escape token.
         *
         * @param token escape token
         * @return this builder
         */
        public Builder escape(String token) {
            this.escape = token;
            return this;
        }

        /**
         * Validates tokens and builds immutable syntax.
         *
         * @return pattern syntax
         */
        public PatternSyntax build() {
            return new PatternSyntax(this);
        }
    }
}
