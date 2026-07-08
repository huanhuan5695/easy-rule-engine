# 模板槽位匹配器

这是一个基于 Java 的模板匹配示例项目，用于把用户输入文本快速匹配到业务模板，并返回模板所属分类、模板唯一 ID，以及命中的槽位值和槽位在原文中的位置。

项目核心适合这些场景：

- 意图识别：把一句话路由到指定 `category` 和 `templateId`。
- 模板分类：同一分类下维护多个模板，每个模板有唯一 ID。
- 槽位抽取：在匹配模板时，同时拿到 `[people]`、`[city]`、`[song]` 等用户自定义槽位。
- 字典匹配：每个槽位可以维护自己的词典，例如 `people -> 中国人、美国人、学生`。

## 项目结构

```text
pom.xml                # Maven 构建配置，包含 JUnit 5 测试依赖

src/main/java/
  DoubleArrayTrie.java     # 双数组前缀树，用于高效字典前缀匹配
  PatternMode.java         # 模板匹配模式枚举
  RulePattern.java         # 稳定的规则模板描述对象
  TemplateMatcher.java     # 模板匹配器，支持严格模板和槽位序列两种模式

src/test/java/
  DoubleArrayTrieTest.java # 双数组前缀树测试入口
  TemplateMatcherTest.java # 模板匹配器测试入口
  TemplateMatcherApiTest.java      # JUnit 5 API 测试
  TemplateMatcherApiSmokeTest.java # 无依赖 API smoke test
```

项目提供 Maven 工程配置；如果本机暂时没有 Maven，也可以使用 `javac` 和 `java` 运行无依赖测试入口。

## 快速运行

推荐使用 Maven：

```bash
mvn test
```

如果本机没有 Maven，可以在项目根目录执行：

```bash
javac -d /tmp/dat-test src/main/java/DoubleArrayTrie.java src/main/java/PatternMode.java src/main/java/RulePattern.java src/main/java/TemplateMatcher.java src/test/java/DoubleArrayTrieTest.java src/test/java/TemplateMatcherTest.java src/test/java/TemplateMatcherApiSmokeTest.java
java -cp /tmp/dat-test DoubleArrayTrieTest
java -cp /tmp/dat-test TemplateMatcherTest
java -cp /tmp/dat-test TemplateMatcherApiSmokeTest
```

期望输出：

```text
All DoubleArrayTrie tests passed.
All TemplateMatcher tests passed.
All TemplateMatcherApi smoke tests passed.
```

## 基本用法

先构建槽位字典，再注册模板：

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .addSlotDictionary("people", Arrays.asList("中国人", "美国人", "学生"))
        .addSlotDictionary("song", Arrays.asList("青花瓷", "稻香"))
        .addPattern(RulePattern.exact("music", "person-song", "[people]喜欢唱[song]"))
        .build();

List<TemplateMatcher.MatchResult> results = matcher.match("中国人喜欢唱青花瓷");
```

读取匹配结果：

```java
TemplateMatcher.MatchResult result = results.get(0);

result.category();   // "music"
result.templateId(); // "person-song"
result.mode();       // PatternMode.EXACT
result.captures();   // { people=["中国人"], song=["青花瓷"] }
```

如果需要槽位在原文中的位置，使用 `slotCaptures()`：

```java
TemplateMatcher.SlotCapture people = result.slotCaptures().get("people").get(0);

people.slotName(); // "people"
people.value();    // "中国人"
people.start();    // 0
people.end();      // 3
```

`start` 是命中内容的起始下标，`end` 是结束下标的后一位，遵循 Java `substring(start, end)` 的习惯。

## 匹配模式

### 1. 严格模板匹配

严格模板中可以同时包含固定文本和槽位：

```text
我是[people]我喜欢唱歌
```

输入：

```text
我是中国人我喜欢唱歌
```

匹配过程：

```text
固定文本：我 -> 是
槽位：[people] -> 中国人
固定文本：我 -> 喜 -> 欢 -> 唱 -> 歌
```

命中后返回：

```text
category = music
templateId = sing
people = 中国人
```

### 2. 槽位序列匹配

如果 pattern 中只有槽位和 `_` 分隔符，例如：

```text
[like]_[sing]
```

它会被识别为槽位序列模式。这里的 `_` 只是分隔符，不要求输入中真的出现 `_`。

输入：

```text
我喜欢唱歌
```

如果字典中存在：

```text
like -> 喜欢、爱
sing -> 唱歌、唱曲
```

则可以命中：

```text
like = 喜欢, start = 1, end = 3
sing = 唱歌, start = 3, end = 5
```

槽位序列模式适合描述“这些槽位按顺序出现在句子里”的意图，不要求整句话完全等于模板。

## 匹配优先级

`TemplateMatcher.match(input)` 使用两阶段策略：

1. 先执行严格模板匹配。
2. 如果严格模板有结果，直接返回，不再执行槽位序列匹配。
3. 如果严格模板没有结果，再执行槽位序列匹配。

这样可以保证明确模板优先，弱匹配兜底。

例如同时存在：

```text
exact:    我[like][sing]
sequence: [like]_[sing]
```

输入：

```text
我喜欢唱歌
```

会优先返回 `exact` 模板，而不是 `sequence` 模板。

## 实现原理

### 双数组前缀树

`DoubleArrayTrie` 使用两个核心数组：

```text
base[]
check[]
```

从节点 `s` 走字符 `c` 时，目标位置为：

```text
t = base[s] + code(c)
```

然后用：

```text
check[t] == s
```

确认这个位置确实是从 `s` 转移过来的。

这种结构避免了普通 Trie 中大量对象和指针的开销，适合静态字典的快速前缀查询。项目中槽位字典就是用它构建的。

### 严格模板匹配

模板会先被解析成 token：

```text
我是[people]我喜欢唱歌
```

解析为：

```text
TEXT("我")
TEXT("是")
SLOT("people")
TEXT("我")
TEXT("喜")
TEXT("欢")
TEXT("唱")
TEXT("歌")
```

固定字符会进入模板 Trie 的字符边，槽位会进入特殊的 `SlotEdge`。

匹配时维护状态：

```text
当前模板节点
当前输入位置
已经捕获的槽位
```

当遇到槽位边时，从当前输入位置调用对应槽位字典的前缀匹配。如果一个槽位有多个候选，例如 `中国` 和 `中国人`，匹配器会保留多个候选状态，后续固定文本匹配失败时可以自动回退到较短候选。

### 槽位序列匹配

槽位序列 pattern：

```text
[like]_[sing]
```

会被编译为：

```text
like -> sing
```

匹配时从输入中按顺序寻找各槽位字典的命中项。每找到一个槽位，就记录它的值、起始位置和结束位置，然后继续寻找下一个槽位。

该模式允许输入前后有额外文本，例如：

```text
我喜欢唱歌
他非常喜欢大声唱歌
```

只要槽位按顺序出现，就可以匹配。

### 非递归状态队列

模板匹配没有使用递归，而是用 `ArrayDeque` 保存待处理状态。这样可以避免长模板或大量候选导致 Java 调用栈溢出，也方便通过 `maxStates` 和 `maxResults` 控制匹配成本。

默认限制：

```text
maxStates = 10000
maxResults = 100
```

可以在构建时调整：

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .maxStates(5000)
        .maxResults(20)
        .build();
```

## API 说明

### 添加槽位字典

```java
addSlotDictionary(String slotName, Collection<String> values)
```

示例：

```java
.addSlotDictionary("city", Arrays.asList("北京", "上海", "杭州"))
```

也可以直接传入已经构建好的 `DoubleArrayTrie`：

```java
.addSlotDictionary("city", cityTrie)
```

### 添加模板

```java
addPattern(RulePattern pattern)
```

示例：

```java
.addPattern(RulePattern.exact("travel", "from-city", "我来自[city]"))
.addPattern(RulePattern.slotSequence("music", "like-sing", "[like]_[sing]"))
```

如果多个模板都命中同一段输入，可以给规则设置优先级。数字越大，结果越靠前：

```java
.addPattern(RulePattern.exact("profile", "low", "我是[people]", 0))
.addPattern(RulePattern.exact("profile", "high", "我是[people]", 10))
```

参数含义：

- `category`：模板分类，例如 `music`、`travel`、`profile`。
- `templateId`：模板唯一 ID。
- `pattern`：模板内容，支持固定文本和 `[slotName]`。
- `mode`：模板模式，推荐显式使用 `PatternMode.EXACT` 或 `PatternMode.SLOT_SEQUENCE`。
- `priority`：可选优先级，默认为 `0`，值越大排序越靠前。

为了兼容旧代码，仍然保留：

```java
addTemplate(String category, String templateId, String pattern)
```

该方法会根据 pattern 形态自动推断模式。

### 匹配输入

```java
List<TemplateMatcher.MatchResult> results = matcher.match(input);
```

`MatchResult` 提供：

```java
category()
templateId()
mode()
priority()
captures()
slotCaptures()
```

其中：

- `captures()` 返回简化后的槽位值。
- `slotCaptures()` 返回带位置的槽位命中详情。

## 设计约束

- 当前实现适合静态或低频更新的字典。字典更新后建议重新构建 matcher。
- 槽位名只允许字母、数字、下划线和中划线。
- 槽位序列模式只把 `_` 作为分隔符；如果 pattern 里出现其他固定字符，就会按严格模板处理。
- 当严格模板匹配成功时，不会继续执行槽位序列匹配。
- `build()` 会生成当前 Builder 状态的快照；后续继续给 Builder 添加模板不会影响已经构建好的 matcher。
- 槽位序列匹配会先为输入预扫描每个槽位的命中项，避免多个 sequence 模板重复扫描同一段文本。槽位和字典规模进一步增大时，可以升级为 Aho-Corasick 自动机。

## 示例测试

更多示例可以查看：

- `src/test/java/DoubleArrayTrieTest.java`
- `src/test/java/TemplateMatcherTest.java`

这些测试展示了：

- 普通词典前缀匹配
- 显式 `RulePattern` / `PatternMode` API
- 严格模板匹配
- 多槽位抽取
- 同名槽位多次捕获
- 候选回退
- 严格模板优先
- 槽位序列兜底匹配
