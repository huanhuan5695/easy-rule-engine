package io.github.huanhuan5695.easyrule;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateMatcherApiTest {
    @Test
    void exactModeIsExplicitAndReturnsSlotSpans() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国", "中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]人"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertEquals(1, results.size());
        TemplateMatcher.MatchResult result = results.get(0);
        assertEquals(PatternMode.EXACT, result.mode());
        assertEquals("profile", result.category());
        assertEquals("nationality", result.templateId());
        assertEquals("中国", result.slotCaptures().get("people").get(0).value());
        assertEquals(2, result.slotCaptures().get("people").get(0).start());
        assertEquals(4, result.slotCaptures().get("people").get(0).end());
    }

    @Test
    void slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "exact-like-sing", "我[like][sing]"))
                .addPattern(RulePattern.slotSequence("sequence", "sequence-like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> exactResults = matcher.match("我喜欢唱歌");
        assertEquals(1, exactResults.size());
        assertEquals("exact-like-sing", exactResults.get(0).templateId());
        assertEquals(PatternMode.EXACT, exactResults.get(0).mode());

        List<TemplateMatcher.MatchResult> fallbackResults = matcher.match("他喜欢唱歌");
        assertEquals(1, fallbackResults.size());
        assertEquals("sequence-like-sing", fallbackResults.get(0).templateId());
        assertEquals(PatternMode.SLOT_SEQUENCE, fallbackResults.get(0).mode());
    }

    @Test
    void explicitSlotSequenceRejectsFixedTextOtherThanUnderscore() {
        assertThrows(IllegalArgumentException.class,
                () -> RulePattern.slotSequence("bad", "bad-pattern", "[like]-[sing]"));
    }

    @Test
    void rulePatternValidatesSlotSyntaxAtCreation() {
        assertThrows(IllegalArgumentException.class,
                () -> RulePattern.exact("bad", "unclosed", "我是[people"));
        assertThrows(IllegalArgumentException.class,
                () -> RulePattern.slotSequence("bad", "empty-slot", "[]"));
        assertThrows(IllegalArgumentException.class,
                () -> RulePattern.slotSequence("bad", "invalid-slot", "[bad slot]"));
    }

    @Test
    void builtMatcherIsNotChangedByLaterBuilderMutations() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "first", "我是[people]"));

        TemplateMatcher first = builder.build();
        builder.addPattern(RulePattern.exact("profile", "later", "他是[people]"));
        TemplateMatcher second = builder.build();

        assertTrue(first.match("他是中国人").isEmpty());
        assertEquals(1, second.match("他是中国人").size());
    }

    @Test
    void higherPriorityResultsAreReturnedFirst() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "low", "我是[people]", 0))
                .addPattern(RulePattern.exact("profile", "high", "我是[people]", 10))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertEquals(2, results.size());
        assertEquals("high", results.get(0).templateId());
        assertEquals("low", results.get(1).templateId());
    }

    @Test
    void strictSlotValidationFailsFastForMissingDictionaries() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> TemplateMatcher.builder()
                        .strictSlotValidation()
                        .addPattern(RulePattern.exact("profile", "missing", "我是[people]"))
                        .build());

        assertEquals("missing slot dictionaries: people", exception.getMessage());
    }

    @Test
    void strictSlotValidationAllowsCompleteDictionaries() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.slotSequence("music", "like-sing", "[like]_[sing]"))
                .build();

        assertEquals(1, matcher.match("我喜欢唱歌").size());
    }

    @Test
    void builderCanRegisterDictionariesAndPatternsInBatches() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionaries(Map.of(
                        "people", Arrays.asList("中国人"),
                        "song", Arrays.asList("青花瓷")))
                .addPatterns(Arrays.asList(
                        RulePattern.exact("music", "person-song", "[people]喜欢唱[song]"),
                        RulePattern.slotSequence("music", "sequence", "[people]_[song]")))
                .build();

        assertEquals("person-song", matcher.match("中国人喜欢唱青花瓷").get(0).templateId());
        assertEquals(1, matcher.match(
                "他说中国人唱青花瓷",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build()).size());
    }

    @Test
    void builderBatchRegistrationRejectsNullInputs() {
        TemplateMatcher.Builder builder = TemplateMatcher.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.addSlotDictionaries(null));
        assertThrows(IllegalArgumentException.class, () -> builder.addPatterns(null));
    }

    @Test
    void duplicateRulePatternsDoNotDuplicateResults() {
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

        assertEquals(1, matcher.match("我是中国人").size());
        assertEquals(1, matcher.match(
                "他喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build()).size());
    }

    @Test
    void matchOptionsCanForceSlotSequenceMode() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "exact-like-sing", "我[like][sing]"))
                .addPattern(RulePattern.slotSequence("sequence", "sequence-like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(1, results.size());
        assertEquals(PatternMode.SLOT_SEQUENCE, results.get(0).mode());
        assertEquals("sequence-like-sing", results.get(0).templateId());
    }

    @Test
    void matchOptionsCanLimitResultsPerCall() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "first", "我是[people]", 10))
                .addPattern(RulePattern.exact("profile", "second", "我是[people]", 5))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我是中国人",
                MatchOptions.builder().maxResults(1).build());

        assertEquals(1, results.size());
        assertEquals("first", results.get(0).templateId());
    }

    @Test
    void matchOptionsCanLimitStatesPerCall() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .maxStates(100)
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> matcher.match("我是中国人", MatchOptions.builder().maxStates(1).build()));

        assertEquals("exact template matching exceeded maxStates=1", exception.getMessage());
    }

    @Test
    void matcherLevelMaxStatesCannotBeRaisedPerCall() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .maxStates(1)
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> matcher.match("我是中国人", MatchOptions.builder().maxStates(100).build()));

        assertEquals("exact template matching exceeded maxStates=1", exception.getMessage());
    }

    @Test
    void allModeSharesStateBudgetAcrossMatchingPhases() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .maxStates(100)
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addPattern(RulePattern.exact("exact", "miss", "不会命中"))
                .addPattern(RulePattern.slotSequence("sequence", "like-sing", "[like]_[sing]"))
                .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> matcher.match(
                        "完全不相关",
                        MatchOptions.builder().mode(MatchMode.ALL).maxStates(1).build()));

        assertEquals("slot sequence matching exceeded maxStates=1", exception.getMessage());
    }

    @Test
    void matchResultsListIsImmutable() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertThrows(UnsupportedOperationException.class, results::clear);
    }

    @Test
    void publicValueObjectsSupportValueSemantics() {
        RulePattern firstPattern = RulePattern.exact("profile", "nationality", "我是[people]", 7);
        RulePattern secondPattern = RulePattern.exact("profile", "nationality", "我是[people]", 7);
        assertEquals(firstPattern, secondPattern);
        assertEquals(firstPattern.hashCode(), secondPattern.hashCode());
        assertTrue(firstPattern.toString().contains("nationality"));

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
        assertEquals(firstOptions, secondOptions);
        assertEquals(firstOptions.hashCode(), secondOptions.hashCode());
        assertTrue(firstOptions.toString().contains("ALL"));

        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(firstPattern)
                .build();
        TemplateMatcher.MatchResult firstResult = matcher.match("我是中国人").get(0);
        TemplateMatcher.MatchResult secondResult = matcher.match("我是中国人").get(0);

        assertEquals(firstResult, secondResult);
        assertEquals(firstResult.hashCode(), secondResult.hashCode());
        assertTrue(firstResult.toString().contains("nationality"));
        assertEquals(firstResult.slotCaptures().get("people").get(0), secondResult.slotCaptures().get("people").get(0));
        assertEquals(
                firstResult.slotCaptures().get("people").get(0).hashCode(),
                secondResult.slotCaptures().get("people").get(0).hashCode());
        assertTrue(firstResult.slotCaptures().get("people").get(0).toString().contains("中国人"));
    }

    @Test
    void slotSequenceFindsOverlappingValuesAcrossSlots() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜", "喜欢"))
                .addSlotDictionary("sing", Arrays.asList("欢唱", "唱歌"))
                .addPattern(RulePattern.slotSequence("music", "overlap", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(3, results.size());
        assertEquals("喜欢", results.get(0).captures().get("like").get(0));
        assertEquals("唱歌", results.get(0).captures().get("sing").get(0));
        assertEquals("喜", results.get(1).captures().get("like").get(0));
        assertEquals("欢唱", results.get(1).captures().get("sing").get(0));
        assertEquals("喜", results.get(2).captures().get("like").get(0));
        assertEquals("唱歌", results.get(2).captures().get("sing").get(0));
    }

    @Test
    void duplicateDictionaryValuesDoNotDuplicateSlotSequenceResults() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢", "喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌", "唱歌"))
                .addPattern(RulePattern.slotSequence("music", "like-sing", "[like]_[sing]"))
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match(
                "我喜欢唱歌",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).build());

        assertEquals(1, results.size());
        assertEquals("喜欢", results.get(0).captures().get("like").get(0));
        assertEquals("唱歌", results.get(0).captures().get("sing").get(0));
    }
}
