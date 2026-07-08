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

        System.out.println("All TemplateMatcherPackage smoke tests passed.");
    }
}
