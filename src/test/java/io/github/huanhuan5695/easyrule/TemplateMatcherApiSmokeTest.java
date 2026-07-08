package io.github.huanhuan5695.easyrule;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TemplateMatcherApiSmokeTest {
    public static void main(String[] args) {
        exactModeIsExplicitAndReturnsSlotSpans();
        slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss();
        explicitSlotSequenceRejectsFixedTextOtherThanUnderscore();
        rulePatternValidatesSlotSyntaxAtCreation();
        builtMatcherIsNotChangedByLaterBuilderMutations();
        higherPriorityResultsAreReturnedFirst();
        strictSlotValidationFailsFastForMissingDictionaries();
        strictSlotValidationAllowsCompleteDictionaries();
        strictTemplateIdValidationFailsForDuplicateIdsInSameCategory();
        strictTemplateIdValidationAllowsSameIdInDifferentCategories();
        matcherStatsDescribeLoadedConfiguration();
        builderCanRegisterDictionariesAndPatternsInBatches();
        builderCanRegisterTrieDictionariesInBatches();
        builderBatchRegistrationRejectsNullInputs();
        failedBatchPatternRegistrationDoesNotPartiallyMutateBuilder();
        failedBatchDictionaryRegistrationDoesNotPartiallyMutateBuilder();
        failedBatchTrieDictionaryRegistrationDoesNotPartiallyMutateBuilder();
        legacyAddTemplateRejectsNullPatternConsistently();
        duplicateRulePatternsDoNotDuplicateResults();
        matchOptionsCanForceSlotSequenceMode();
        matchOptionsCanLimitResultsPerCall();
        matchOptionsCanLimitStatesPerCall();
        allModeSharesStateBudgetAcrossMatchingPhases();
        matchFirstReturnsHighestOrderedResult();
        matchFirstHonorsOptionsAndReturnsEmptyWhenMissing();
        matchResultsListIsImmutable();
        publicValueObjectsSupportValueSemantics();
        slotSequenceFindsOverlappingValuesAcrossSlots();
        duplicateDictionaryValuesDoNotDuplicateSlotSequenceResults();

        System.out.println("All TemplateMatcherApi smoke tests passed.");
    }

    private static void exactModeIsExplicitAndReturnsSlotSpans() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国", "中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]人"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertEquals(1, results.size(), "one exact API result");
        TemplateMatcher.MatchResult result = results.get(0);
        assertEquals(PatternMode.EXACT, result.mode(), "exact result mode");
        assertEquals("profile", result.category(), "exact category");
        assertEquals("nationality", result.templateId(), "exact template id");
        assertEquals("中国", result.slotCaptures().get("people").get(0).value(), "people value");
        assertEquals(2, result.slotCaptures().get("people").get(0).start(), "people start");
        assertEquals(4, result.slotCaptures().get("people").get(0).end(), "people end");
    }

    private static void slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "exact-like-sing", "我[like][sing]"))
                .addPattern(RulePattern.slotSequence("sequence", "sequence-like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> exactResults = matcher.match("我喜欢唱歌");
        assertEquals(1, exactResults.size(), "one exact priority result");
        assertEquals("exact-like-sing", exactResults.get(0).templateId(), "exact template wins");
        assertEquals(PatternMode.EXACT, exactResults.get(0).mode(), "exact mode wins");

        List<TemplateMatcher.MatchResult> fallbackResults = matcher.match("他喜欢唱歌");
        assertEquals(1, fallbackResults.size(), "one slot sequence fallback result");
        assertEquals("sequence-like-sing", fallbackResults.get(0).templateId(), "sequence fallback id");
        assertEquals(PatternMode.SLOT_SEQUENCE, fallbackResults.get(0).mode(), "sequence fallback mode");
    }

    private static void explicitSlotSequenceRejectsFixedTextOtherThanUnderscore() {
        try {
            RulePattern.slotSequence("bad", "bad-pattern", "[like]-[sing]");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("slot sequence should reject fixed text other than underscore");
    }

    private static void rulePatternValidatesSlotSyntaxAtCreation() {
        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> RulePattern.exact("bad", "unclosed", "我是[people"),
                "exact pattern should reject unclosed slots");
        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> RulePattern.slotSequence("bad", "empty-slot", "[]"),
                "slot sequence should reject empty slot names");
        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> RulePattern.slotSequence("bad", "invalid-slot", "[bad slot]"),
                "slot sequence should reject invalid slot names");
    }

    private static void builtMatcherIsNotChangedByLaterBuilderMutations() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "first", "我是[people]"));

        TemplateMatcher first = builder.build();
        builder.addPattern(RulePattern.exact("profile", "later", "他是[people]"));
        TemplateMatcher second = builder.build();

        assertEquals(0, first.match("他是中国人").size(), "first matcher keeps build-time snapshot");
        assertEquals(1, second.match("他是中国人").size(), "second matcher sees later pattern");
    }

    private static void higherPriorityResultsAreReturnedFirst() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "low", "我是[people]", 0))
                .addPattern(RulePattern.exact("profile", "high", "我是[people]", 10))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertEquals(2, results.size(), "two priority results");
        assertEquals("high", results.get(0).templateId(), "higher priority first");
        assertEquals("low", results.get(1).templateId(), "lower priority second");
    }

    private static void strictSlotValidationFailsFastForMissingDictionaries() {
        try {
            TemplateMatcher.builder()
                    .strictSlotValidation()
                    .addPattern(RulePattern.exact("profile", "missing", "我是[people]"))
                    .build();
        } catch (IllegalStateException expected) {
            assertEquals("missing slot dictionaries: people", expected.getMessage(), "missing slot message");
            return;
        }
        throw new AssertionError("strict validation should fail when a slot dictionary is missing");
    }

    private static void strictSlotValidationAllowsCompleteDictionaries() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.slotSequence("music", "like-sing", "[like]_[sing]"))
                .build();

        assertEquals(1, matcher.match("我喜欢唱歌").size(), "complete dictionaries build and match");
    }

    private static void strictTemplateIdValidationFailsForDuplicateIdsInSameCategory() {
        try {
            TemplateMatcher.builder()
                    .strictTemplateIdValidation()
                    .addPattern(RulePattern.exact("profile", "same-id", "我是[people]"))
                    .addPattern(RulePattern.exact("profile", "same-id", "他是[people]"))
                    .build();
        } catch (IllegalStateException expected) {
            assertEquals("duplicate template ids: profile/same-id", expected.getMessage(), "duplicate id message");
            return;
        }
        throw new AssertionError("strict template id validation should reject duplicate ids");
    }

    private static void strictTemplateIdValidationAllowsSameIdInDifferentCategories() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictTemplateIdValidation()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "same-id", "我是[people]"))
                .addPattern(RulePattern.exact("account", "same-id", "我是[people]"))
                .build();

        assertEquals(2, matcher.match("我是中国人").size(), "same id can be reused across categories");
    }

    private static void matcherStatsDescribeLoadedConfiguration() {
        RulePattern exact = RulePattern.exact("profile", "nationality", "我是[people]");
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人", "中国人", "学生"))
                .addSlotDictionary("song", DoubleArrayTrie.build(Arrays.asList("青花瓷", "稻香")))
                .addPattern(exact)
                .addPattern(exact)
                .addPattern(RulePattern.slotSequence("music", "person-song", "[people]_[song]"))
                .build();
        TemplateMatcher equivalentMatcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人", "中国人", "学生"))
                .addSlotDictionary("song", DoubleArrayTrie.build(Arrays.asList("青花瓷", "稻香")))
                .addPattern(exact)
                .addPattern(exact)
                .addPattern(RulePattern.slotSequence("music", "person-song", "[people]_[song]"))
                .build();

        TemplateMatcher.Stats stats = matcher.stats();
        TemplateMatcher.Stats sameStats = equivalentMatcher.stats();

        assertEquals(2, stats.templateCount(), "stats count unique templates");
        assertEquals(1, stats.exactTemplateCount(), "stats count exact templates");
        assertEquals(1, stats.slotSequenceTemplateCount(), "stats count slot sequence templates");
        assertEquals(2, stats.slotDictionaryCount(), "stats count slot dictionaries");
        assertEquals(4, stats.slotValueCount(), "stats count unique slot values");
        assertTrue(stats != sameStats, "stats value semantics compare distinct instances");
        assertEquals(stats, sameStats, "stats compare by value");
        assertEquals(stats.hashCode(), sameStats.hashCode(), "stats hash code");
        assertTrue(stats.toString().contains("templateCount=2"), "stats string contains counts");
    }

    private static void builderCanRegisterDictionariesAndPatternsInBatches() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionaries(Map.of(
                        "people", Arrays.asList("中国人"),
                        "song", Arrays.asList("青花瓷")))
                .addPatterns(Arrays.asList(
                        RulePattern.exact("music", "person-song", "[people]喜欢唱[song]"),
                        RulePattern.slotSequence("music", "sequence", "[people]_[song]")))
                .build();

        assertEquals(
                "person-song",
                matcher.match("中国人喜欢唱青花瓷").get(0).templateId(),
                "batch exact pattern matches");
        assertEquals(
                1,
                matcher.match(
                        "他说中国人唱青花瓷",
                        MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build()).size(),
                "batch slot sequence pattern matches");
    }

    private static void builderCanRegisterTrieDictionariesInBatches() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionaryTries(Map.of(
                        "people", DoubleArrayTrie.build(Arrays.asList("中国人")),
                        "song", DoubleArrayTrie.build(Arrays.asList("青花瓷"))))
                .addPatterns(Arrays.asList(
                        RulePattern.exact("music", "person-song", "[people]喜欢唱[song]"),
                        RulePattern.slotSequence("music", "sequence", "[people]_[song]")))
                .build();

        assertEquals(
                "person-song",
                matcher.match("中国人喜欢唱青花瓷").get(0).templateId(),
                "batch trie exact pattern matches");
        assertEquals(
                1,
                matcher.match(
                        "他说中国人唱青花瓷",
                        MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build()).size(),
                "batch trie slot sequence pattern matches");
    }

    private static void builderBatchRegistrationRejectsNullInputs() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder();

        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> builder.addSlotDictionaries(null),
                "batch dictionaries should reject null input");
        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> builder.addPatterns(null),
                "batch patterns should reject null input");
        assertThrows(
                IllegalArgumentException.class,
                null,
                () -> builder.addSlotDictionaryTries(null),
                "batch trie dictionaries should reject null input");
    }

    private static void failedBatchPatternRegistrationDoesNotPartiallyMutateBuilder() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"));

        assertThrows(
                IllegalArgumentException.class,
                "pattern is required",
                () -> builder.addPatterns(Arrays.asList(
                        RulePattern.exact("profile", "nationality", "我是[people]"),
                        null)),
                "batch pattern failure should reject null pattern");

        assertEquals(0, builder.build().match("我是中国人").size(), "failed pattern batch should be atomic");
    }

    private static void failedBatchDictionaryRegistrationDoesNotPartiallyMutateBuilder() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder();
        Map<String, List<String>> dictionaries = new LinkedHashMap<>();
        dictionaries.put("people", Arrays.asList("中国人"));
        dictionaries.put("bad", null);

        assertThrows(
                IllegalArgumentException.class,
                "values is required",
                () -> builder.addSlotDictionaries(dictionaries),
                "batch dictionary failure should reject null values");

        try {
            builder.strictSlotValidation()
                    .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                    .build();
        } catch (IllegalStateException expected) {
            assertEquals("missing slot dictionaries: people", expected.getMessage(), "failed dictionary batch atomic");
            return;
        }
        throw new AssertionError("failed dictionary batch should not retain previous entries");
    }

    private static void failedBatchTrieDictionaryRegistrationDoesNotPartiallyMutateBuilder() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder();
        Map<String, DoubleArrayTrie> dictionaries = new LinkedHashMap<>();
        dictionaries.put("people", DoubleArrayTrie.build(Arrays.asList("中国人")));
        dictionaries.put("bad", null);

        assertThrows(
                IllegalArgumentException.class,
                "trie is required",
                () -> builder.addSlotDictionaryTries(dictionaries),
                "batch trie dictionary failure should reject null trie");

        try {
            builder.strictSlotValidation()
                    .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                    .build();
        } catch (IllegalStateException expected) {
            assertEquals("missing slot dictionaries: people", expected.getMessage(), "failed trie batch atomic");
            return;
        }
        throw new AssertionError("failed trie dictionary batch should not retain previous entries");
    }

    private static void legacyAddTemplateRejectsNullPatternConsistently() {
        assertThrows(
                IllegalArgumentException.class,
                "pattern is required",
                () -> TemplateMatcher.builder().addTemplate("profile", "bad", null),
                "legacy addTemplate should reject null pattern consistently");
    }

    private static void duplicateRulePatternsDoNotDuplicateResults() {
        RulePattern exact = RulePattern.exact("profile", "nationality", "我是[people]");
        RulePattern sequence = RulePattern.slotSequence("music", "like-sing", "[like]_[sing]");
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(exact)
                .addPattern(exact)
                .addPattern(sequence)
                .addPattern(sequence)
                .build();

        assertEquals(1, matcher.match("我是中国人").size(), "duplicate exact rules should collapse");
        assertEquals(
                1,
                matcher.match(
                        "他喜欢唱歌",
                        MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build()).size(),
                "duplicate slot sequence rules should collapse");
    }

    private static void matchOptionsCanForceSlotSequenceMode() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "exact-like-sing", "我[like][sing]"))
                .addPattern(RulePattern.slotSequence("sequence", "sequence-like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(1, results.size(), "one forced slot sequence result");
        assertEquals(PatternMode.SLOT_SEQUENCE, results.get(0).mode(), "slot sequence mode forced");
        assertEquals("sequence-like-sing", results.get(0).templateId(), "slot sequence template returned");
    }

    private static void matchOptionsCanLimitResultsPerCall() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "first", "我是[people]", 10))
                .addPattern(RulePattern.exact("profile", "second", "我是[people]", 5))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我是中国人",
                MatchOptions.builder().maxResults(1).build());

        assertEquals(1, results.size(), "one limited result");
        assertEquals("first", results.get(0).templateId(), "highest priority survives per-call limit");
    }

    private static void matchOptionsCanLimitStatesPerCall() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .maxStates(100)
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        assertThrows(
                IllegalStateException.class,
                "exact template matching exceeded maxStates=1",
                () -> matcher.match("我是中国人", MatchOptions.builder().maxStates(1).build()),
                "per-call max states should fail fast");
    }

    private static void allModeSharesStateBudgetAcrossMatchingPhases() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .maxStates(100)
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "miss", "不会命中"))
                .addPattern(RulePattern.slotSequence("sequence", "like-sing", "[like]_[sing]"))
                .build();

        assertThrows(
                IllegalStateException.class,
                "slot sequence matching exceeded maxStates=1",
                () -> matcher.match(
                        "完全不相关",
                        MatchOptions.builder().mode(MatchMode.ALL).maxStates(1).build()),
                "all mode should share one state budget across phases");
    }

    private static void matchFirstReturnsHighestOrderedResult() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "low", "我是[people]", 0))
                .addPattern(RulePattern.exact("profile", "high", "我是[people]", 10))
                .build();

        Optional<TemplateMatcher.MatchResult> result = matcher.matchFirst("我是中国人");

        assertTrue(result.isPresent(), "best result is present");
        assertEquals("high", result.get().templateId(), "matchFirst returns highest ordered result");
    }

    private static void matchFirstHonorsOptionsAndReturnsEmptyWhenMissing() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "exact-like-sing", "我[like][sing]"))
                .addPattern(RulePattern.slotSequence("sequence", "sequence-like-sing", "[like]_[sing]"))
                .build();

        Optional<TemplateMatcher.MatchResult> result = matcher.matchFirst(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertTrue(result.isPresent(), "mode-specific best result is present");
        assertEquals(PatternMode.SLOT_SEQUENCE, result.get().mode(), "matchFirst honors mode option");
        assertTrue(matcher.matchFirst("完全不相关").isEmpty(), "missing input returns empty optional");
    }

    private static void matchResultsListIsImmutable() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        try {
            results.clear();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("match results list should be immutable");
    }

    private static void publicValueObjectsSupportValueSemantics() {
        RulePattern firstPattern = RulePattern.exact("profile", "nationality", "我是[people]", 7);
        RulePattern secondPattern = RulePattern.exact("profile", "nationality", "我是[people]", 7);
        assertEquals(firstPattern, secondPattern, "rule patterns compare by value");
        assertEquals(firstPattern.hashCode(), secondPattern.hashCode(), "rule pattern hash code");
        assertTrue(firstPattern.toString().contains("nationality"), "rule pattern string contains id");

        MatchOptions firstOptions = MatchOptions.builder()
                .mode(MatchMode.ALL)
                .maxResults(3)
                .maxStates(50)
                .build();
        MatchOptions secondOptions = MatchOptions.builder()
                .mode(MatchMode.ALL)
                .maxResults(3)
                .maxStates(50)
                .build();
        assertEquals(firstOptions, secondOptions, "match options compare by value");
        assertEquals(firstOptions.hashCode(), secondOptions.hashCode(), "match options hash code");
        assertTrue(firstOptions.toString().contains("ALL"), "match options string contains mode");

        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(firstPattern)
                .build();
        TemplateMatcher.MatchResult firstResult = matcher.match("我是中国人").get(0);
        TemplateMatcher.MatchResult secondResult = matcher.match("我是中国人").get(0);

        assertEquals(firstResult, secondResult, "match results compare by value");
        assertEquals(firstResult.hashCode(), secondResult.hashCode(), "match result hash code");
        assertTrue(firstResult.toString().contains("nationality"), "match result string contains id");
        assertEquals(
                firstResult.slotCaptures().get("people").get(0),
                secondResult.slotCaptures().get("people").get(0),
                "slot captures compare by value");
        assertEquals(
                firstResult.slotCaptures().get("people").get(0).hashCode(),
                secondResult.slotCaptures().get("people").get(0).hashCode(),
                "slot capture hash code");
        assertTrue(
                firstResult.slotCaptures().get("people").get(0).toString().contains("中国人"),
                "slot capture string contains value");
    }

    private static void slotSequenceFindsOverlappingValuesAcrossSlots() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜", "喜欢"))
                .addSlotDictionary("sing", Arrays.asList("欢唱", "唱歌"))
                .addPattern(RulePattern.slotSequence("music", "overlap", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(3, results.size(), "overlapping slot sequence results");
        assertEquals("喜欢", results.get(0).captures().get("like").get(0), "longer combined capture first");
        assertEquals("唱歌", results.get(0).captures().get("sing").get(0), "later slot capture");
        assertEquals("喜", results.get(1).captures().get("like").get(0), "short overlapping capture retained");
        assertEquals("欢唱", results.get(1).captures().get("sing").get(0), "overlapping next slot retained");
        assertEquals("喜", results.get(2).captures().get("like").get(0), "short capture can skip to later slot");
        assertEquals("唱歌", results.get(2).captures().get("sing").get(0), "later slot retained after short capture");
    }

    private static void duplicateDictionaryValuesDoNotDuplicateSlotSequenceResults() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢", "喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌", "唱歌"))
                .addPattern(RulePattern.slotSequence("music", "like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(1, results.size(), "duplicate dictionary values should not duplicate results");
        assertEquals("喜欢", results.get(0).captures().get("like").get(0), "single like capture");
        assertEquals("唱歌", results.get(0).captures().get("sing").get(0), "single sing capture");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(
            Class<? extends Throwable> expected,
            String expectedMessage,
            ThrowingRunnable runnable,
            String message) {
        try {
            runnable.run();
        } catch (Throwable actual) {
            if (!expected.isInstance(actual)) {
                throw new AssertionError(message + ": expected " + expected.getName() + ", got " + actual, actual);
            }
            if (expectedMessage != null) {
                assertEquals(expectedMessage, actual.getMessage(), message + " message");
            }
            return;
        }
        throw new AssertionError(message + ": expected " + expected.getName());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
