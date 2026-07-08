# Contributing

Thanks for improving easy-rule-engine. This project aims to stay small, fast, and easy to embed in Java applications.

## Development Setup

Use Maven when available:

```bash
mvn test
```

If Maven is not installed, run the no-dependency local checks:

```bash
make check
```

Run the local benchmark with:

```bash
make benchmark
```

## Contribution Rules

- Keep public APIs backward compatible unless the change is intentionally breaking and documented.
- Add tests for new matching behavior, parser behavior, or edge cases.
- Prefer immutable public value objects and explicit builder options.
- Avoid adding runtime dependencies unless they remove substantial complexity.
- Keep examples copy-pasteable and package-qualified.

## Pull Requests

Each pull request should include:

- A short summary of what changed and why.
- Validation commands and results.
- Notes about behavior changes, compatibility, or follow-up work.
- Benchmarks when changing matching algorithms or trie construction.

Use the repository pull request template and keep the validation checklist current. For releases, follow `docs/release.md`.
