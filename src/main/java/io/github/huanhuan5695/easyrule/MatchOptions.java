package io.github.huanhuan5695.easyrule;

import java.util.Objects;

/**
 * Immutable options for one {@link TemplateMatcher#match(String, MatchOptions)} call.
 */
public final class MatchOptions {
    private static final MatchOptions DEFAULT = builder().build();

    private final MatchMode mode;
    private final Integer maxResults;

    private MatchOptions(MatchMode mode, Integer maxResults) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.maxResults = maxResults;
    }

    /**
     * Returns the default options: {@link MatchMode#EXACT_THEN_SLOT_SEQUENCE}
     * with the matcher-level result limit.
     *
     * @return shared immutable default options
     */
    public static MatchOptions defaultOptions() {
        return DEFAULT;
    }

    /**
     * Creates a builder for per-call match options.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the matching strategy for this call.
     *
     * @return match mode
     */
    public MatchMode mode() {
        return mode;
    }

    /**
     * Returns the per-call result limit, or {@code null} to use the matcher-level limit.
     *
     * @return maximum result count or {@code null}
     */
    public Integer maxResults() {
        return maxResults;
    }

    /**
     * Builder for {@link MatchOptions}.
     */
    public static final class Builder {
        private MatchMode mode = MatchMode.EXACT_THEN_SLOT_SEQUENCE;
        private Integer maxResults;

        private Builder() {
        }

        /**
         * Sets the matching strategy.
         *
         * @param mode matching strategy
         * @return this builder
         */
        public Builder mode(MatchMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * Sets a positive per-call maximum result count.
         *
         * @param maxResults maximum number of results to return
         * @return this builder
         */
        public Builder maxResults(int maxResults) {
            if (maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Builds immutable match options.
         *
         * @return match options
         */
        public MatchOptions build() {
            return new MatchOptions(mode, maxResults);
        }
    }
}
