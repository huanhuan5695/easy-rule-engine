# Easy Rule Engine

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/huanhuan5695/easy-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/huanhuan5695/easy-rule-engine/actions/workflows/ci.yml)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://openjdk.org/projects/jdk/11/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Easy Rule Engine 是一个轻量、无运行时依赖的 Java 文本规则匹配库。输入文本命中模板后，可以同时获得业务分类、模板唯一 ID、优先级、匹配模式，以及每个槽位的值和原文位置。

它适合确定性的意图路由、命令解析、FAQ 分类和字典实体抽取。当业务规则明确、可解释性要求较高时，不需要引入完整的 NLP 技术栈。

## 项目能力

- 在同一个 pattern 中组合固定文本与用户自定义槽位，例如 `我是[people]`。
- 同一 `category` 下维护多个模板，并返回实际命中的 `templateId`。
- 严格整句匹配失败后，可以自动回退到槽位顺序匹配。
- 支持多个槽位、同名槽位重复捕获，并返回 UTF-16 `start`、`end` 位置。
- 支持 `(我|你)?[like](电影|电视剧)` 这样的有限选项展开。
- 可以把 `[]`、`()`、`|`、`?`、`_` 和转义符替换成业务自定义标记。
- `build()` 后生成不可变快照，可被多个线程安全共享。
- 内置输入长度、搜索状态、槽位命中、结果数量和模板展开限制。
- 基于 Java 11，无第三方运行时依赖。

## 环境要求

- JDK 11 或更高版本
- 推荐 Maven 3.8+；没有 Maven 时可以使用 `make` 完成无依赖检查

当前版本尚未发布到 Maven Central。从其他 Maven 项目引用前，需要先安装到本地仓库。

## 五分钟快速开始

### 第一步：克隆并验证项目

```bash
git clone https://github.com/huanhuan5695/easy-rule-engine.git
cd easy-rule-engine
mvn test
```

没有 Maven 时执行：

```bash
make check
```

运行完整示例：

```bash
make example
```

### 第二步：安装到本地 Maven 仓库

```bash
mvn install
```

在其他 Maven 项目中添加依赖：

```xml
<dependency>
  <groupId>io.github.huanhuan5695</groupId>
  <artifactId>easy-rule-engine</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 第三步：创建匹配器

```java
import io.github.huanhuan5695.easyrule.RulePattern;
import io.github.huanhuan5695.easyrule.TemplateMatcher;

import java.util.Arrays;

TemplateMatcher matcher = TemplateMatcher.builder()
        .strictSlotValidation()
        .strictTemplateIdValidation()
        .addSlotDictionary("people", Arrays.asList("中国人", "美国人", "学生"))
        .addSlotDictionary("song", Arrays.asList("青花瓷", "稻香"))
        .addPattern(RulePattern.exact(
                "music", "person-song", "[people]喜欢唱[song]", 10))
        .build();
```

槽位使用 `[slotName]` 表示。模板引用槽位时，会从同名槽位字典中查找候选值。

### 第四步：匹配文本

```java
TemplateMatcher.MatchResult result = matcher
        .matchFirst("中国人喜欢唱青花瓷")
        .orElseThrow(() -> new IllegalStateException("没有模板命中"));
```

### 第五步：读取分类、模板和槽位

```java
System.out.println(result.category());       // music
System.out.println(result.templateId());     // person-song
System.out.println(result.captures());       // {people=[中国人], song=[青花瓷]}

TemplateMatcher.SlotCapture person = result.slotCaptures().get("people").get(0);
System.out.println(person.value());          // 中国人
System.out.println(person.start());          // 0
System.out.println(person.end());            // 3
```

`start` 包含在命中范围内，`end` 不包含，语义与 `String.substring(start, end)` 一致。位置使用 Java UTF-16 下标。

## 核心概念

| 概念 | 含义 | 示例 |
| --- | --- | --- |
| `category` | 模板所属业务分类 | `music` |
| `templateId` | 分类内稳定的模板唯一 ID | `person-song` |
| `pattern` | 由固定文本和槽位组成的规则 | `[people]喜欢唱[song]` |
| slot | 用户维护的字典引用 | `[people]` |
| `priority` | 多个结果的排序权重，值越大越靠前 | `10` |
| `PatternMode` | 单条模板的解释方式 | `EXACT`、`SLOT_SEQUENCE` |
| `MatchMode` | 一次请求执行哪些模板模式 | `EXACT_THEN_SLOT_SEQUENCE` |
| `PatternSyntax` | Matcher 级的模板语法配置 | `${slot}`、`{a/b}~` |

## 模板类型

### 严格模板

严格模板必须消费完整输入，可以自由组合固定文本和槽位：

```java
.addPattern(RulePattern.exact(
        "profile", "nationality", "我是[people]"))
```

如果字典同时存在 `中国` 和 `中国人`，匹配器会保留仍有可能完成整条模板的前缀候选，不会因为过早选择最长词而漏掉正确结果。

### 槽位序列模板

槽位序列模板只能包含槽位和 `_` 分隔符：

```java
.addPattern(RulePattern.slotSequence(
        "music", "like-sing", "[like]_[sing]"))
```

`_` 只负责在 pattern 中分隔槽位，输入不需要包含下划线。如果字典值按顺序出现，`[like]_[sing]` 可以匹配“我非常喜欢大声唱歌”。输入在槽位前后或槽位之间可以包含其他文本。

### 默认匹配策略

默认策略是 `EXACT_THEN_SLOT_SEQUENCE`：

1. 首先执行严格模板。
2. 严格模板有结果时立即返回。
3. 严格模板没有结果时，再执行槽位序列模板。

单次调用可以覆盖策略：

```java
import io.github.huanhuan5695.easyrule.MatchMode;
import io.github.huanhuan5695.easyrule.MatchOptions;

TemplateMatcher.MatchResult result = matcher.matchFirst(
        input,
        MatchOptions.builder()
                .mode(MatchMode.SLOT_SEQUENCE_ONLY)
                .maxResults(1)
                .build())
        .orElse(null);
```

可选策略包括 `EXACT_THEN_SLOT_SEQUENCE`、`EXACT_ONLY`、`SLOT_SEQUENCE_ONLY` 和 `ALL`。

## 泛化模板

当一条规则只有少量确定的文本变化时，可以在构建期有限展开：

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .addSlotDictionary("like", Arrays.asList("喜欢", "爱看"))
        .addExpandedTemplate(
                "media", "media-like", "(我|你)?[like](电影|电视剧)")
        .build();
```

支持的语法：

- `(a|b|c)`：从多个固定文本中选择一个。
- `(a|b|c)?`：整个选项组可以省略。
- `[slotName]`：仍然在运行时查询字典，不会展开成大量模板。

泛化语法需要显式使用 `addExpandedTemplate(...)`。普通 `addTemplate(...)` 会把 `()|?` 当作普通文本。通过 `maxExpandedPatterns(...)` 可以限制单条规则在构建期产生的模板数量。

## 自定义模板语法

如果默认的 `[]` 和 `()?` 不适合业务配置格式，可以在注册模板前配置一套不可变的 `PatternSyntax`：

```java
import io.github.huanhuan5695.easyrule.PatternSyntax;

PatternSyntax syntax = PatternSyntax.builder()
        .slot("${", "}")          // ${people}
        .group("{", "}")          // {我/你}
        .alternative("/")
        .optional("~")            // {我/你}~
        .sequenceSeparator("+")   // ${like}+${sing}
        .escape("\\")
        .build();

TemplateMatcher matcher = TemplateMatcher.builder()
        .patternSyntax(syntax)
        .addSlotDictionary("people", Arrays.asList("中国人", "学生"))
        .addSlotDictionary("like", Arrays.asList("喜欢"))
        .addSlotDictionary("sing", Arrays.asList("唱歌"))
        .addTemplate("profile", "identity", "我是${people}")
        .addPattern(RulePattern.slotSequence(
                "music", "like-sing", "${like}+${sing}"))
        .addExpandedTemplate(
                "profile", "optional-identity", "{我/你}~是${people}")
        .build();
```

所有标记都支持多个字符：

```java
PatternSyntax syntax = PatternSyntax.builder()
        .slot("{{", "}}")
        .group("<<", ">>")
        .alternative("||")
        .optional("??")
        .sequenceSeparator("::")
        .build();
```

`PatternSyntax.build()` 会拒绝存在前缀歧义的配置。例如槽位开始符 `<` 与分组开始符 `<<` 冲突。槽位和分组可以共享结束符，例如 `${people}` 和 `{我/你}` 都使用 `}`，因为解析器可以根据当前上下文明确区分。

需要把语法标记作为普通文本时，在标记前添加转义符。如果开始符和结束符都是保留标记，需要分别转义：

```text
Pattern: \${不是槽位\}是${value}
Input:   ${不是槽位}是示例
```

`patternSyntax(...)` 必须在 `addTemplate(...)`、`addPattern(...)` 或 `addExpandedTemplate(...)` 之前调用，槽位字典不受顺序限制。`RulePattern` 只保存规则描述，依赖具体语法的校验会在规则注册到 Builder 时执行。

## 加载字典与规则

可以从集合、UTF-8 文件或预构建 Trie 添加槽位字典：

```java
.addSlotDictionary("city", Arrays.asList("北京", "上海"))
.addSlotDictionaryFile("city", Path.of("dict/city.txt"))
.addSlotDictionary("city", DoubleArrayTrie.build(cityNames))
```

字典文件每行保存一个值。空行以及去除首尾空白后以 `#` 开头的行会被忽略。

批量 API 会先校验全部数据，再修改 Builder，避免失败后留下部分配置：

```java
.addSlotDictionaries(dictionaryMap)
.addSlotDictionaryTries(trieMap)
.addPatterns(rulePatterns)
```

调用 `addTemplate(category, pattern)` 时，Builder 会生成 `auto-1`、`auto-2` 这样的内部 ID。需要持久化配置、审计日志或跨系统追踪时，应使用显式 `templateId`。

## 生产环境配置

建议开启严格校验，让错误配置在服务启动时直接失败：

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .strictSlotValidation()
        .strictTemplateIdValidation()
        .maxStates(5_000)
        .maxResults(20)
        .maxInputLength(2_000)
        .maxSlotHits(10_000)
        .maxExpandedPatterns(100)
        // 添加字典和模板
        .build();
```

默认保护值：

| 配置 | 默认值 | 防护目标 |
| --- | ---: | --- |
| `maxStates` | 10,000 | 严格模板或槽位序列搜索分支过多 |
| `maxResults` | 100 | 中间和最终结果集合过大 |
| `maxInputLength` | 100,000 | 输入文本过长 |
| `maxSlotHits` | 100,000 | 字典命中过密或大量重叠 |
| `maxExpandedPatterns` | 512 | 泛化模板组合爆炸 |

单次调用只能收紧 Builder 的全局限制，不能提高上限：

```java
MatchOptions options = MatchOptions.builder()
        .maxStates(1_000)
        .maxResults(5)
        .maxInputLength(500)
        .maxSlotHits(2_000)
        .build();
```

运行时超过限制时，可以单独捕获限流异常：

```java
try {
    matcher.match(input, options);
} catch (TemplateMatcher.MatchLimitExceededException exception) {
    log.warn("匹配被限制: phase={}, limit={}={}",
            exception.phase(), exception.limitName(), exception.limit());
}
```

`TemplateMatcher` 在 `build()` 后不可变，可以被请求线程安全共享。更新字典或规则时，建议构建新的 matcher 快照，再通过应用层原子替换引用。

## 实现原理

```text
注册规则
  -> 使用 PatternSyntax 解析文本、槽位、分组和转义
  -> 生成统一的内部 Pattern AST
  -> 将严格模板编译到共享模板 Trie
  -> 将槽位序列编译为有序槽位列表
  -> 构建 DoubleArrayTrie 字典和共享扫描索引

match(input)
  -> 检查请求资源限制
  -> 使用非递归状态队列遍历严格模板
  -> 按策略扫描并匹配槽位序列
  -> 保留排序后的 Top-K 结果
  -> 返回不可变 MatchResult
```

`DoubleArrayTrie` 使用 `base[]` 和 `check[]` 保存字典状态转移。槽位序列所需的字典还会合并到共享的 Aho-Corasick 风格扫描索引中，使多个序列模板只需扫描一次输入。搜索过程使用队列和共享捕获链，不依赖递归，也不会为每个分支复制完整槽位 Map。

## 项目结构

```text
src/main/java/io/github/huanhuan5695/easyrule/  核心源码
src/test/java/                                    JUnit 与无依赖测试
src/benchmark/java/                               本地基准测试
examples/QuickStart.java                          可运行示例
docs/performance.md                               性能测试说明
docs/release.md                                   发布检查清单
```

## 开发命令

| 命令 | 作用 |
| --- | --- |
| `mvn test` | 执行 JUnit 5 测试 |
| `mvn package` | 构建 jar、sources jar 和 Javadocs jar |
| `mvn install` | 安装当前快照到本地 Maven 仓库 |
| `make check` | 不使用 Maven 执行 smoke、示例和 Javadocs 检查 |
| `make example` | 编译并运行 `examples/QuickStart.java` |
| `make benchmark` | 执行无依赖本地基准测试 |

提交 Pull Request 前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。性能数据的运行和对比方法见 [docs/performance.md](docs/performance.md)。

## 当前约束

- 槽位名只能包含字母、数字、`_` 和 `-`。
- 一个 matcher 中的全部模板使用同一套 `PatternSyntax`，并且必须在添加模板前配置。
- 语法标记不能为空，也不能存在有歧义的前缀关系。
- 槽位序列模板只能包含槽位和配置的序列分隔符（默认是 `_`）。
- 泛化模板仅支持有限选项组，不支持嵌套分组和任意正则表达式。
- 字典适合静态或低频更新场景。
- 重复字典值和完全相同的 `RulePattern` 会被去重。
- 匹配结果以及内部槽位集合均不可变。
- `null` 输入返回空结果；非法规则和非法配置会快速失败。

## 许可证

本项目使用 [MIT License](LICENSE)。
