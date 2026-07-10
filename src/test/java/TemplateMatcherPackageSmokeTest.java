import io.github.huanhuan5695.easyrule.PatternSyntax;
import io.github.huanhuan5695.easyrule.RulePattern;
import io.github.huanhuan5695.easyrule.TemplateMatcher;

import java.util.Arrays;

public class TemplateMatcherPackageSmokeTest {
    public static void main(String[] args) {
        TemplateMatcher matcher = TemplateMatcher.builder()
                .strictSlotValidation()
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addPattern(RulePattern.exact("profile", "nationality", "我是[people]"))
                .build();

        if (matcher.match("我是中国人").size() != 1) {
            throw new AssertionError("packaged public API should be usable by external consumers");
        }

        PatternSyntax syntax = PatternSyntax.builder()
                .slot("${", "}")
                .group("{", "}")
                .alternative("/")
                .optional("~")
                .sequenceSeparator("+")
                .build();
        TemplateMatcher customMatcher = TemplateMatcher.builder()
                .patternSyntax(syntax)
                .addSlotDictionary("people", Arrays.asList("中国人"))
                .addExpandedTemplate("profile", "custom", "{我/你}~是${people}")
                .build();

        if (!"custom".equals(customMatcher.match("你是中国人").get(0).templateId())) {
            throw new AssertionError("custom pattern syntax should be usable by external consumers");
        }

        System.out.println("All TemplateMatcherPackage smoke tests passed.");
    }
}
