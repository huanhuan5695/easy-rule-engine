# Repository Guidelines

## Project Structure & Module Organization

This repository is a lightweight Java rule matcher. Keep code organized by responsibility and avoid implementation files at the root. Current layout:

- `src/main/java/` for matcher and trie source code.
- `src/test/java/` for JUnit and smoke tests.
- `README.md` for user-facing usage and design notes.
- `pom.xml` for Maven build configuration.

Keep future docs in `docs/` and repeatable automation in `scripts/` if those areas are added.

## Build, Test, and Development Commands

Use Maven when available:

- `mvn test` runs the JUnit test suite.
- `mvn package` builds the jar.

Without Maven, compile and run the smoke tests directly:

```bash
javac -d /tmp/dat-test src/main/java/DoubleArrayTrie.java src/main/java/PatternMode.java src/main/java/RulePattern.java src/main/java/TemplateMatcher.java src/test/java/DoubleArrayTrieTest.java src/test/java/TemplateMatcherTest.java src/test/java/TemplateMatcherApiSmokeTest.java
java -cp /tmp/dat-test DoubleArrayTrieTest
java -cp /tmp/dat-test TemplateMatcherTest
java -cp /tmp/dat-test TemplateMatcherApiSmokeTest
```

## Coding Style & Naming Conventions

Use standard Java style: four-space indentation, `PascalCase` classes, `camelCase` methods and fields, and constants in `UPPER_SNAKE_CASE`. Keep public API objects small and immutable where practical.

## Testing Guidelines

Add tests with the feature or fix they cover. Prefer JUnit 5 tests under `src/test/java/`; keep no-dependency smoke tests when they help verify behavior without Maven. Test method names should describe behavior, such as `slotSequenceModeIsExplicitAndOnlyUsedAfterExactMiss`.

## Commit & Pull Request Guidelines

Use concise imperative commit messages, such as `Add explicit pattern mode API` or `Fix slot sequence fallback`. Keep commits focused.

Pull requests should include a short summary, validation steps, linked issues when relevant, and screenshots or recordings for UI changes. Note follow-up work explicitly so reviewers can separate intentional scope from omissions.

## Security & Configuration Tips

Do not commit secrets, local credentials, generated private keys, or machine-specific configuration. Provide example environment files such as `.env.example` when configuration is required, and document required variables in `docs/` or the project README.
