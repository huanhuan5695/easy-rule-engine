SMOKE_BUILD_DIR ?= /tmp/easy-rule-smoke
BENCH_BUILD_DIR ?= /tmp/easy-rule-bench
EXAMPLE_BUILD_DIR ?= /tmp/easy-rule-example

MAIN_SOURCES = \
	src/main/java/io/github/huanhuan5695/easyrule/DoubleArrayTrie.java \
	src/main/java/io/github/huanhuan5695/easyrule/MatchMode.java \
	src/main/java/io/github/huanhuan5695/easyrule/MatchOptions.java \
	src/main/java/io/github/huanhuan5695/easyrule/PatternMode.java \
	src/main/java/io/github/huanhuan5695/easyrule/RulePattern.java \
	src/main/java/io/github/huanhuan5695/easyrule/TemplateMatcher.java

SMOKE_SOURCES = \
	$(MAIN_SOURCES) \
	src/test/java/io/github/huanhuan5695/easyrule/DoubleArrayTrieTest.java \
	src/test/java/io/github/huanhuan5695/easyrule/TemplateMatcherTest.java \
	src/test/java/io/github/huanhuan5695/easyrule/TemplateMatcherApiSmokeTest.java \
	src/test/java/TemplateMatcherPackageSmokeTest.java

BENCH_SOURCES = \
	$(MAIN_SOURCES) \
	src/benchmark/java/io/github/huanhuan5695/easyrule/TemplateMatcherBenchmark.java

EXAMPLE_SOURCES = \
	$(MAIN_SOURCES) \
	examples/QuickStart.java

.PHONY: check smoke example javadocs benchmark clean

check: smoke example javadocs

smoke:
	javac -d $(SMOKE_BUILD_DIR) $(SMOKE_SOURCES)
	java -cp $(SMOKE_BUILD_DIR) io.github.huanhuan5695.easyrule.DoubleArrayTrieTest
	java -cp $(SMOKE_BUILD_DIR) io.github.huanhuan5695.easyrule.TemplateMatcherTest
	java -cp $(SMOKE_BUILD_DIR) io.github.huanhuan5695.easyrule.TemplateMatcherApiSmokeTest
	java -cp $(SMOKE_BUILD_DIR) TemplateMatcherPackageSmokeTest

example:
	javac -d $(EXAMPLE_BUILD_DIR) $(EXAMPLE_SOURCES)
	java -cp $(EXAMPLE_BUILD_DIR) QuickStart

javadocs:
	javadoc -quiet -d /tmp/easy-rule-javadoc src/main/java/io/github/huanhuan5695/easyrule/*.java

benchmark:
	javac -d $(BENCH_BUILD_DIR) $(BENCH_SOURCES)
	java -cp $(BENCH_BUILD_DIR) io.github.huanhuan5695.easyrule.TemplateMatcherBenchmark

clean:
	rm -rf $(SMOKE_BUILD_DIR) $(BENCH_BUILD_DIR) $(EXAMPLE_BUILD_DIR)
