package io.github.huanhuan5695.easyrule;

import java.util.ArrayList;
import java.util.List;

public final class TemplateMatcherBenchmark {
    private static final int WARMUP_ITERATIONS = 5_000;
    private static final int MEASURE_ITERATIONS = 50_000;

    private TemplateMatcherBenchmark() {
    }

    public static void main(String[] args) {
        TemplateMatcher matcher = buildMatcher();
        String exactInput = "我是用户42我喜欢唱歌曲42";
        String sequenceInput = "今天用户42真的很喜欢马上唱歌曲42";
        MatchOptions sequenceOnly = MatchOptions.builder()
                .mode(MatchMode.SLOT_SEQUENCE_ONLY)
                .build();

        runWarmup(matcher, exactInput, sequenceInput);

        BenchmarkResult exact = measure("exact", () -> matcher.match(exactInput));
        BenchmarkResult sequence = measure(
                "slot-sequence",
                () -> matcher.match(sequenceInput, sequenceOnly));

        System.out.println(exact);
        System.out.println(sequence);
    }

    private static TemplateMatcher buildMatcher() {
        List<String> people = numberedValues("用户", 1_000);
        List<String> songs = numberedValues("歌曲", 1_000);
        List<String> likes = listOf("喜欢", "爱", "想听", "想唱");
        List<String> singActions = listOf("唱", "演唱", "播放", "听");

        TemplateMatcher.Builder builder = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionary("people", people)
                .addSlotDictionary("song", songs)
                .addSlotDictionary("like", likes)
                .addSlotDictionary("singAction", singActions)
                .maxResults(20);

        for (int i = 0; i < 100; i++) {
            builder.addPattern(RulePattern.exact("profile", "exact-" + i, "我是[people]我喜欢唱[song]", i));
            builder.addPattern(RulePattern.slotSequence("music", "sequence-" + i, "[people]_[like]_[singAction]_[song]", i));
        }
        return builder.build();
    }

    private static void runWarmup(TemplateMatcher matcher, String exactInput, String sequenceInput) {
        MatchOptions sequenceOnly = MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build();
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            matcher.match(exactInput);
            matcher.match(sequenceInput, sequenceOnly);
        }
    }

    private static BenchmarkResult measure(String name, BenchmarkOperation operation) {
        long start = System.nanoTime();
        int resultCount = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            resultCount += operation.run().size();
        }
        long elapsedNanos = System.nanoTime() - start;
        return new BenchmarkResult(name, MEASURE_ITERATIONS, elapsedNanos, resultCount);
    }

    private static List<String> numberedValues(String prefix, int count) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(prefix + i);
        }
        return values;
    }

    private static List<String> listOf(String first, String second, String third, String fourth) {
        List<String> values = new ArrayList<>();
        values.add(first);
        values.add(second);
        values.add(third);
        values.add(fourth);
        return values;
    }

    private interface BenchmarkOperation {
        List<TemplateMatcher.MatchResult> run();
    }

    private static final class BenchmarkResult {
        private final String name;
        private final int iterations;
        private final long elapsedNanos;
        private final int resultCount;

        private BenchmarkResult(String name, int iterations, long elapsedNanos, int resultCount) {
            this.name = name;
            this.iterations = iterations;
            this.elapsedNanos = elapsedNanos;
            this.resultCount = resultCount;
        }

        @Override
        public String toString() {
            double elapsedMillis = elapsedNanos / 1_000_000.0;
            double averageMicros = elapsedNanos / 1_000.0 / iterations;
            double throughput = iterations / (elapsedNanos / 1_000_000_000.0);
            return name
                    + ": iterations=" + iterations
                    + ", totalMs=" + String.format("%.3f", elapsedMillis)
                    + ", avgMicros=" + String.format("%.3f", averageMicros)
                    + ", opsPerSecond=" + String.format("%.0f", throughput)
                    + ", resultCount=" + resultCount;
        }
    }
}
