import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
