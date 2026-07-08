package io.github.huanhuan5695.easyrule;

import java.util.Objects;

public final class MatchOptions {
    private static final MatchOptions DEFAULT = builder().build();

    private final MatchMode mode;
    private final Integer maxResults;

    private MatchOptions(MatchMode mode, Integer maxResults) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.maxResults = maxResults;
    }

    public static MatchOptions defaultOptions() {
        return DEFAULT;
    }

    public static Builder builder() {
        return new Builder();
    }

    public MatchMode mode() {
        return mode;
    }

    public Integer maxResults() {
        return maxResults;
    }

    public static final class Builder {
        private MatchMode mode = MatchMode.EXACT_THEN_SLOT_SEQUENCE;
        private Integer maxResults;

        public Builder mode(MatchMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder maxResults(int maxResults) {
            if (maxResults <= 0) {
                throw new IllegalArgumentException("maxResults must be positive");
            }
            this.maxResults = maxResults;
            return this;
        }

        public MatchOptions build() {
            return new MatchOptions(mode, maxResults);
        }
    }
}
