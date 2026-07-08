package io.github.huanhuan5695.easyrule;

import java.util.Arrays;
import java.util.List;

public class TemplateMatcherApiSmokeTest {
    public static void main(String[] args) {
        exactModeIsExplicitAndReturnsSlotSpans();
        slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss();
        explicitSlotSequenceRejectsFixedTextOtherThanUnderscore();
        builtMatcherIsNotChangedByLaterBuilderMutations();
        higherPriorityResultsAreReturnedFirst();
        strictSlotValidationFailsFastForMissingDictionaries();
        strictSlotValidationAllowsCompleteDictionaries();
        matchOptionsCanForceSlotSequenceMode();
        matchOptionsCanLimitResultsPerCall();
        matchOptionsCanLimitStatesPerCall();
        allModeSharesStateBudgetAcrossMatchingPhases();
        matchResultsListIsImmutable();
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
            assertEquals(expectedMessage, actual.getMessage(), message + " message");
            return;
        }
        throw new AssertionError(message + ": expected " + expected.getName());
    }

    private interface ThrowingRunnable {
        void run();
    }
}
