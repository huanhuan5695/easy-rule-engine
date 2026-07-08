import java.util.Arrays;
import java.util.List;

public class TemplateMatcherApiSmokeTest {
    public static void main(String[] args) {
        exactModeIsExplicitAndReturnsSlotSpans();
        slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss();
        explicitSlotSequenceRejectsFixedTextOtherThanUnderscore();
        builtMatcherIsNotChangedByLaterBuilderMutations();
        higherPriorityResultsAreReturnedFirst();

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

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
