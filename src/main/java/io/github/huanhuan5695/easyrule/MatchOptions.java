package io.github.huanhuan5695.easyrule;

import java.util.Objects;

/**
 * Immutable options for one {@link TemplateMatcher#match(String, MatchOptions)} call.
 */
public final class MatchOptions {
    private static final MatchOptions DEFAULT = builder().build();

    private final MatchMode mode;
    private final Integer maxResults;
    private final Integer maxStates;
    private final Integer maxInputLength;
    private final Integer maxSlotHits;

    private MatchOptions(
            MatchMode mode,
            Integer maxResults,
            Integer maxStates,
            Integer maxInputLength,
            Integer maxSlotHits) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.maxResults = maxResults;
        this.maxStates = maxStates;
        this.maxInputLength = maxInputLength;
        this.maxSlotHits = maxSlotHits;
    }

    /**
     * Returns the default options: {@link MatchMode#EXACT_THEN_SLOT_SEQUENCE}
     * with matcher-level result and state limits.
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
     * Returns the per-call state limit, or {@code null} to use the matcher-level limit.
     *
     * @return maximum visited internal states or {@code null}
     */
    public Integer maxStates() {
        return maxStates;
    }

    /**
     * Returns the per-call UTF-16 input length limit, or {@code null} to use the matcher-level limit.
     *
     * @return maximum input length or {@code null}
     */
    public Integer maxInputLength() {
        return maxInputLength;
    }

    /**
     * Returns the per-call slot-hit limit, or {@code null} to use the matcher-level limit.
     *
     * @return maximum slot hits or {@code null}
     */
    public Integer maxSlotHits() {
        return maxSlotHits;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MatchOptions)) {
            return false;
        }
        MatchOptions that = (MatchOptions) other;
        return mode == that.mode
                && Objects.equals(maxResults, that.maxResults)
                && Objects.equals(maxStates, that.maxStates)
                && Objects.equals(maxInputLength, that.maxInputLength)
                && Objects.equals(maxSlotHits, that.maxSlotHits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, maxResults, maxStates, maxInputLength, maxSlotHits);
    }

    @Override
    public String toString() {
        return "MatchOptions{"
                + "mode=" + mode
                + ", maxResults=" + maxResults
                + ", maxStates=" + maxStates
                + ", maxInputLength=" + maxInputLength
                + ", maxSlotHits=" + maxSlotHits
                + '}';
    }

    /**
     * Builder for {@link MatchOptions}.
     */
    public static final class Builder {
        private MatchMode mode = MatchMode.EXACT_THEN_SLOT_SEQUENCE;
        private Integer maxResults;
        private Integer maxStates;
        private Integer maxInputLength;
        private Integer maxSlotHits;

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
         * Sets a positive per-call maximum number of visited internal states.
         *
         * <p>This can lower, but not raise, the matcher-level state limit.
         *
         * @param maxStates maximum number of internal states to visit
         * @return this builder
         */
        public Builder maxStates(int maxStates) {
            if (maxStates <= 0) {
                throw new IllegalArgumentException("maxStates must be positive");
            }
            this.maxStates = maxStates;
            return this;
        }

        /**
         * Sets a positive per-call UTF-16 input length limit.
         *
         * <p>This can lower, but not raise, the matcher-level input limit.
         *
         * @param maxInputLength maximum {@link String#length()} value
         * @return this builder
         */
        public Builder maxInputLength(int maxInputLength) {
            if (maxInputLength <= 0) {
                throw new IllegalArgumentException("maxInputLength must be positive");
            }
            this.maxInputLength = maxInputLength;
            return this;
        }

        /**
         * Sets a positive per-call limit for dictionary hits collected during slot-sequence scanning.
         *
         * <p>This can lower, but not raise, the matcher-level slot-hit limit.
         *
         * @param maxSlotHits maximum number of collected slot hits
         * @return this builder
         */
        public Builder maxSlotHits(int maxSlotHits) {
            if (maxSlotHits <= 0) {
                throw new IllegalArgumentException("maxSlotHits must be positive");
            }
            this.maxSlotHits = maxSlotHits;
            return this;
        }

        /**
         * Builds immutable match options.
         *
         * @return match options
         */
        public MatchOptions build() {
            return new MatchOptions(mode, maxResults, maxStates, maxInputLength, maxSlotHits);
        }
    }
}
