import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TemplateMatcherTest {
    public static void main(String[] args) {
        extractsCustomSlotValue();
        backtracksWhenLongestSlotCandidateBreaksSuffix();
        supportsUserDefinedSlotNames();
        failsWhenSlotDictionaryIsMissing();
        keepsRepeatedSlotCapturesInOrder();
        extractsMultipleSlotNames();
        fallsBackToSlotSequenceWhenExactTemplateMisses();
        prefersExactTemplateBeforeSlotSequence();

        System.out.println("All TemplateMatcher tests passed.");
    }

    private static void extractsCustomSlotValue() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人", "美国人", "学生"))
                .addTemplate("music", "sing", "我是[people]我喜欢唱歌")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人我喜欢唱歌");

        assertEquals(1, results.size(), "one result");
        assertEquals("music", results.get(0).category(), "category");
        assertEquals("sing", results.get(0).templateId(), "template id");
        assertEquals(Arrays.asList("中国人"), results.get(0).captures().get("people"), "people capture");
    }

    private static void backtracksWhenLongestSlotCandidateBreaksSuffix() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国", "中国人"))
                .addTemplate("profile", "nationality", "我是[people]人")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertEquals(1, results.size(), "one backtracked result");
        assertEquals(Arrays.asList("中国"), results.get(0).captures().get("people"), "shorter candidate wins");
    }

    private static void supportsUserDefinedSlotNames() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("food", Arrays.asList("火锅", "拉面"))
                .addTemplate("meal", "eat", "我想吃[food]")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我想吃火锅");

        assertEquals(1, results.size(), "one custom slot result");
        assertEquals(Arrays.asList("火锅"), results.get(0).captures().get("food"), "food capture");
    }

    private static void failsWhenSlotDictionaryIsMissing() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addTemplate("profile", "missing", "我是[people]")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我是中国人");

        assertTrue(results.isEmpty(), "missing dictionary prevents match");
    }

    private static void keepsRepeatedSlotCapturesInOrder() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人", "美国人"))
                .addTemplate("chat", "compare", "[people]和[people]聊天")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("中国人和美国人聊天");

        assertEquals(1, results.size(), "one repeated slot result");
        Map<String, List<String>> captures = results.get(0).captures();
        assertEquals(Arrays.asList("中国人", "美国人"), captures.get("people"), "repeated captures");
    }

    private static void extractsMultipleSlotNames() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("people", Arrays.asList("中国人", "美国人"))
                .addSlotDictionary("song", Arrays.asList("青花瓷", "稻香"))
                .addTemplate("music", "person-song", "[people]喜欢唱[song]")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("中国人喜欢唱青花瓷");

        assertEquals(1, results.size(), "one multi-slot result");
        TemplateMatcher.MatchResult result = results.get(0);
        assertEquals("music", result.category(), "multi-slot category");
        assertEquals("person-song", result.templateId(), "multi-slot template id");
        assertEquals(Arrays.asList("中国人"), result.captures().get("people"), "people capture");
        assertEquals(Arrays.asList("青花瓷"), result.captures().get("song"), "song capture");
    }

    private static void fallsBackToSlotSequenceWhenExactTemplateMisses() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢", "爱"))
                .addSlotDictionary("sing", Arrays.asList("唱歌", "唱曲"))
                .addTemplate("music", "like-sing", "[like]_[sing]")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我喜欢唱歌");

        assertEquals(1, results.size(), "one slot sequence result");
        TemplateMatcher.MatchResult result = results.get(0);
        assertEquals("music", result.category(), "slot sequence category");
        assertEquals("like-sing", result.templateId(), "slot sequence template id");
        assertEquals(Arrays.asList("喜欢"), result.captures().get("like"), "like capture value");
        assertEquals(Arrays.asList("唱歌"), result.captures().get("sing"), "sing capture value");

        TemplateMatcher.SlotCapture like = result.slotCaptures().get("like").get(0);
        TemplateMatcher.SlotCapture sing = result.slotCaptures().get("sing").get(0);
        assertEquals("喜欢", like.value(), "like span value");
        assertEquals(1, like.start(), "like start");
        assertEquals(3, like.end(), "like end");
        assertEquals("唱歌", sing.value(), "sing span value");
        assertEquals(3, sing.start(), "sing start");
        assertEquals(5, sing.end(), "sing end");
    }

    private static void prefersExactTemplateBeforeSlotSequence() {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .addSlotDictionary("like", Arrays.asList("喜欢"))
                .addSlotDictionary("sing", Arrays.asList("唱歌"))
                .addTemplate("exact", "exact-like-sing", "我[like][sing]")
                .addTemplate("sequence", "sequence-like-sing", "[like]_[sing]")
                .build();

        List<TemplateMatcher.MatchResult> results = matcher.match("我喜欢唱歌");

        assertEquals(1, results.size(), "only exact result returned");
        assertEquals("exact", results.get(0).category(), "exact category wins");
        assertEquals("exact-like-sing", results.get(0).templateId(), "exact template wins");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
