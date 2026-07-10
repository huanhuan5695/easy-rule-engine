import io.github.huanhuan5695.easyrule.MatchMode;
import io.github.huanhuan5695.easyrule.MatchOptions;
import io.github.huanhuan5695.easyrule.PatternMode;
import io.github.huanhuan5695.easyrule.PatternSyntax;
import io.github.huanhuan5695.easyrule.RulePattern;
import io.github.huanhuan5695.easyrule.TemplateMatcher;

import java.util.Arrays;
import java.util.List;

public class QuickStart {
    public static void main(String[] args) {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionary("people", Arrays.asList("中国人", "美国人", "学生"))
                .addSlotDictionary("like", Arrays.asList("喜欢", "爱"))
                .addSlotDictionary("sing", Arrays.asList("唱歌", "唱曲"))
                .addSlotDictionary("song", Arrays.asList("青花瓷", "稻香"))
                .addPattern(RulePattern.exact("music", "person-song", "[people][like][sing][song]", 10))
                .addPattern(RulePattern.slotSequence("music", "sequence-like-sing", "[people]_[like]_[sing]_[song]", 1))
                .build();

        TemplateMatcher.MatchResult exact = first(matcher.match("中国人喜欢唱歌青花瓷"));
        require("music".equals(exact.category()), "expected music category");
        require("person-song".equals(exact.templateId()), "expected exact template id");
        require(PatternMode.EXACT == exact.mode(), "expected exact mode");
        require("中国人".equals(exact.captures().get("people").get(0)), "expected people capture");
        require("青花瓷".equals(exact.captures().get("song").get(0)), "expected song capture");

        TemplateMatcher.MatchResult sequence = first(matcher.match(
                "今天中国人非常喜欢马上唱歌青花瓷",
                MatchOptions.builder().mode(MatchMode.SLOT_SEQUENCE_ONLY).maxResults(1).build()));
        require("sequence-like-sing".equals(sequence.templateId()), "expected sequence template id");
        require(PatternMode.SLOT_SEQUENCE == sequence.mode(), "expected slot sequence mode");

        System.out.println("exact: " + exact.category() + "/" + exact.templateId() + " " + exact.captures());
        System.out.println("sequence: " + sequence.category() + "/" + sequence.templateId() + " " + sequence.captures());

        PatternSyntax customSyntax = PatternSyntax.builder()
                .slot("${", "}")
                .group("{", "}")
                .alternative("/")
                .optional("~")
                .sequenceSeparator("+")
                .build();
        TemplateMatcher customMatcher = TemplateMatcher.builder()
                .patternSyntax(customSyntax)
                .addSlotDictionary("people", Arrays.asList("中国人", "学生"))
                .addExpandedTemplate("profile", "custom-identity", "{我/你}~是${people}")
                .build();

        TemplateMatcher.MatchResult custom = first(customMatcher.match("你是学生"));
        require("custom-identity".equals(custom.templateId()), "expected custom syntax template id");
        require("学生".equals(custom.captures().get("people").get(0)), "expected custom syntax capture");
        System.out.println("custom syntax: " + custom.category() + "/" + custom.templateId()
                + " " + custom.captures());
    }

    private static TemplateMatcher.MatchResult first(List<TemplateMatcher.MatchResult> results) {
        require(!results.isEmpty(), "expected at least one match");
        return results.get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
