# Easy Rule Engine

[English](README.md) | [简体中文](README.zh-CN.md)

[![CI](https://github.com/huanhuan5695/easy-rule-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/huanhuan5695/easy-rule-engine/actions/workflows/ci.yml)
[![Java 11+](https://img.shields.io/badge/Java-11%2B-blue.svg)](https://openjdk.org/projects/jdk/11/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Easy Rule Engine is a lightweight, dependency-free Java library for matching text against business templates. A successful match returns the rule category, template ID, priority, matching mode, and every captured slot with its source position.

It is useful for deterministic intent routing, command parsing, FAQ classification, and dictionary-backed entity extraction when a full NLP stack would be unnecessary.

## Why Use It?

- Match fixed text and user-defined slots in one pattern, such as `I am [people]`.
- Keep multiple templates under one category while receiving the exact `templateId` that matched.
- Fall back from strict whole-input matching to ordered slot-sequence matching.
- Capture repeated or multiple slots with UTF-16 `start` and `end` offsets.
- Express bounded alternatives with syntax such as `(I|we)?[like](movies|series)`.
- Replace `[]`, `()`, `|`, `?`, `_`, and the escape marker with application-specific tokens.
- Share immutable matcher snapshots safely across threads.
- Protect services with configurable limits for input length, search states, slot hits, results, and pattern expansion.
- Run with Java 11 and no runtime dependencies.

## Requirements

- JDK 11 or newer
- Maven 3.8+ for the standard build, or `make` for dependency-free checks

The artifact is not yet published to Maven Central. Install it locally before using it from another Maven project.

## Quick Start

### 1. Clone and verify

```bash
git clone https://github.com/huanhuan5695/easy-rule-engine.git
cd easy-rule-engine
mvn test
```

Without Maven:

```bash
make check
```

Run the complete example:

```bash
make example
```

### 2. Install the library locally

```bash
mvn install
```

Add it to another Maven project:

```xml
<dependency>
  <groupId>io.github.huanhuan5695</groupId>
  <artifactId>easy-rule-engine</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 3. Build a matcher

```java
import io.github.huanhuan5695.easyrule.RulePattern;
import io.github.huanhuan5695.easyrule.TemplateMatcher;

import java.util.Arrays;

TemplateMatcher matcher = TemplateMatcher.builder()
        .strictSlotValidation()
        .strictTemplateIdValidation()
        .addSlotDictionary("people", Arrays.asList("Chinese", "American", "student"))
        .addSlotDictionary("song", Arrays.asList("Imagine", "Yesterday"))
        .addPattern(RulePattern.exact(
                "music", "person-song", "[people] likes [song]", 10))
        .build();
```

Slots are written as `[slotName]`. Every referenced slot reads candidates from the dictionary registered under the same name.

### 4. Match input and inspect the result

```java
TemplateMatcher.MatchResult result = matcher
        .matchFirst("student likes Imagine")
        .orElseThrow(() -> new IllegalStateException("no template matched"));

System.out.println(result.category());       // music
System.out.println(result.templateId());     // person-song
System.out.println(result.captures());       // {people=[student], song=[Imagine]}

TemplateMatcher.SlotCapture person = result.slotCaptures().get("people").get(0);
System.out.println(person.value());          // student
System.out.println(person.start());          // 0
System.out.println(person.end());            // 7
```

`start` is inclusive and `end` is exclusive, matching `String.substring(start, end)`. Positions use Java UTF-16 indexes.

## Core Concepts

| Concept | Meaning | Example |
| --- | --- | --- |
| `category` | Business group returned after a match | `music` |
| `templateId` | Stable rule identifier inside a category | `person-song` |
| `pattern` | Fixed text combined with slots | `[people] likes [song]` |
| slot | User-defined dictionary reference | `[people]` |
| `priority` | Higher values are returned first | `10` |
| `PatternMode` | How one rule is interpreted | `EXACT`, `SLOT_SEQUENCE` |
| `MatchMode` | Which rule groups one request executes | `EXACT_THEN_SLOT_SEQUENCE` |
| `PatternSyntax` | Matcher-wide tokens used to parse pattern strings | `${slot}`, `{a/b}~` |

## Pattern Types

### Exact templates

An exact template must consume the complete input. Fixed text and slots can be mixed freely:

```java
.addPattern(RulePattern.exact(
        "profile", "nationality", "I am [people]"))
```

If a dictionary contains both `China` and `Chinese`, the matcher keeps viable prefix candidates while traversing the remaining pattern. This prevents an early longest-prefix choice from hiding a valid full match.

### Slot-sequence templates

A slot-sequence template contains only slots and `_` separators:

```java
.addPattern(RulePattern.slotSequence(
        "music", "like-sing", "[like]_[sing]"))
```

The underscore separates slots in the pattern; it is not expected in the input. `[like]_[sing]` matches `I really like to sing` when dictionary values appear in that order. Extra text before, between, or after slots is allowed.

### Matching strategy

The default request strategy is `EXACT_THEN_SLOT_SEQUENCE`:

1. Run exact templates.
2. Return immediately when at least one exact template matches.
3. Run slot-sequence templates only after an exact miss.

Override it per request:

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

Available strategies are `EXACT_THEN_SLOT_SEQUENCE`, `EXACT_ONLY`, `SLOT_SEQUENCE_ONLY`, and `ALL`.

## Generalized Templates

Use finite expansion when a rule has a small, known set of text alternatives:

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .addSlotDictionary("like", Arrays.asList("likes", "loves"))
        .addExpandedTemplate(
                "media", "media-like", "(I|we)?[like](movies|series)")
        .build();
```

Supported syntax:

- `(a|b|c)` selects one alternative.
- `(a|b|c)?` makes the complete group optional.
- `[slotName]` remains a runtime dictionary lookup and is never expanded into templates.

Expansion is explicit: `addTemplate(...)` treats `()|?` as literal text, while `addExpandedTemplate(...)` interprets the syntax above. Control the compile-time expansion budget with `maxExpandedPatterns(...)`.

## Custom Pattern Syntax

Configure one immutable `PatternSyntax` before registering templates when the default `[]` and `()?` notation does not fit your configuration format:

```java
import io.github.huanhuan5695.easyrule.PatternSyntax;

PatternSyntax syntax = PatternSyntax.builder()
        .slot("${", "}")          // ${people}
        .group("{", "}")          // {I/you}
        .alternative("/")
        .optional("~")            // {I/you}~
        .sequenceSeparator("+")   // ${like}+${sing}
        .escape("\\")
        .build();

TemplateMatcher matcher = TemplateMatcher.builder()
        .patternSyntax(syntax)
        .addSlotDictionary("people", Arrays.asList("student", "developer"))
        .addSlotDictionary("like", Arrays.asList("likes"))
        .addSlotDictionary("sing", Arrays.asList("singing"))
        .addTemplate("profile", "identity", "I am ${people}")
        .addPattern(RulePattern.slotSequence(
                "music", "like-sing", "${like}+${sing}"))
        .addExpandedTemplate(
                "profile", "optional-identity", "{I/you}~am ${people}")
        .build();
```

Tokens may contain multiple characters:

```java
PatternSyntax syntax = PatternSyntax.builder()
        .slot("{{", "}}")
        .group("<<", ">>")
        .alternative("||")
        .optional("??")
        .sequenceSeparator("::")
        .build();
```

Prefix conflicts are rejected during `PatternSyntax.build()`. For example, slot start `<` conflicts with group start `<<`. Slot and group closing tokens may be identical, as in `${people}` and `{I/you}`, because parser context makes the meaning unambiguous.

Use the configured escape token before syntax that must remain literal. Escape both delimiters when both are reserved:

```text
Pattern: \${not-a-slot\} is ${value}
Input:   ${not-a-slot} is example
```

The syntax must be set before `addTemplate(...)`, `addPattern(...)`, or `addExpandedTemplate(...)`. Dictionaries may be registered in any order. `RulePattern` stores rule metadata; syntax-dependent validation happens when it is registered with a matcher builder.

## Loading Rules and Dictionaries

Register a collection, a UTF-8 file, or a prebuilt trie:

```java
.addSlotDictionary("city", Arrays.asList("Beijing", "Shanghai"))
.addSlotDictionaryFile("city", Path.of("dict/city.txt"))
.addSlotDictionary("city", DoubleArrayTrie.build(cityNames))
```

Dictionary files contain one value per line. Blank lines and trimmed lines starting with `#` are ignored.

Batch APIs validate all values before mutating the builder:

```java
.addSlotDictionaries(dictionaryMap)
.addSlotDictionaryTries(trieMap)
.addPatterns(rulePatterns)
```

For low-stakes or local rules, `addTemplate(category, pattern)` generates IDs such as `auto-1`. Use explicit IDs for persisted configurations, audit logs, and distributed systems.

## Production Configuration

Enable strict validation so configuration errors fail during startup:

```java
TemplateMatcher matcher = TemplateMatcher.builder()
        .strictSlotValidation()
        .strictTemplateIdValidation()
        .maxStates(5_000)
        .maxResults(20)
        .maxInputLength(2_000)
        .maxSlotHits(10_000)
        .maxExpandedPatterns(100)
        // dictionaries and patterns
        .build();
```

Default limits:

| Limit | Default | Protects against |
| --- | ---: | --- |
| `maxStates` | 10,000 | Excessive exact or sequence search branching |
| `maxResults` | 100 | Large result collections |
| `maxInputLength` | 100,000 | Oversized input strings |
| `maxSlotHits` | 100,000 | Dense or highly overlapping dictionary hits |
| `maxExpandedPatterns` | 512 | Combinatorial generalized-pattern expansion |

Per-call options can lower, but never raise, matcher-level limits:

```java
MatchOptions options = MatchOptions.builder()
        .maxStates(1_000)
        .maxResults(5)
        .maxInputLength(500)
        .maxSlotHits(2_000)
        .build();
```

Handle runtime limit failures separately from invalid rule configuration:

```java
try {
    matcher.match(input, options);
} catch (TemplateMatcher.MatchLimitExceededException exception) {
    log.warn("Match limited: phase={}, limit={}={}",
            exception.phase(), exception.limitName(), exception.limit());
}
```

`TemplateMatcher` is immutable after `build()` and can be shared by request threads. To update rules or dictionaries, build a new snapshot and atomically replace the application reference.

## How It Works

```text
Rule registration
  -> parse text, slots, groups, and escapes with PatternSyntax
  -> create a shared internal pattern AST
  -> compile exact patterns into a shared template trie
  -> compile slot sequences into ordered slot lists
  -> build DoubleArrayTrie dictionaries and a shared scan index

match(input)
  -> enforce request limits
  -> traverse exact template states without recursion
  -> optionally scan and match slot sequences
  -> retain ordered Top-K results
  -> return immutable MatchResult objects
```

`DoubleArrayTrie` stores dictionary transitions in `base[]` and `check[]`. Slot-sequence dictionaries are also combined into a shared Aho-Corasick-style scan index, allowing one input scan to collect values required by multiple sequence templates. Search states use queues and shared capture traces rather than recursive calls or copied capture maps.

## Project Layout

```text
src/main/java/io/github/huanhuan5695/easyrule/  library source
src/test/java/                                    JUnit and no-dependency tests
src/benchmark/java/                               local benchmark
examples/QuickStart.java                          runnable example
docs/performance.md                               benchmark guide
docs/release.md                                   release checklist
```

## Development Commands

| Command | Purpose |
| --- | --- |
| `mvn test` | Run the JUnit 5 suite |
| `mvn package` | Build the jar, sources jar, and Javadocs jar |
| `mvn install` | Install the snapshot into the local Maven repository |
| `make check` | Run smoke tests, the example, and Javadocs without Maven |
| `make example` | Compile and run `examples/QuickStart.java` |
| `make benchmark` | Run the local dependency-free benchmark |

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request and [docs/performance.md](docs/performance.md) before reporting benchmark changes.

## Current Constraints

- Slot names may contain letters, digits, `_`, and `-`.
- One `PatternSyntax` applies to every template in a matcher and must be configured before templates.
- Syntax tokens must be non-empty and cannot have ambiguous prefix relationships.
- Slot-sequence patterns may contain only slots and the configured sequence separator (`_` by default).
- Generalized patterns support finite groups only; nested groups and arbitrary regular expressions are intentionally unsupported.
- Dictionaries are optimized for static or infrequently updated data.
- Duplicate dictionary values and identical `RulePattern` instances are deduplicated.
- Match results and nested capture collections are immutable.
- `null` input returns no results; invalid rules and invalid configuration fail fast.

## License

Released under the [MIT License](LICENSE).
