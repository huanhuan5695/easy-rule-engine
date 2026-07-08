# Release Guide

This project is not published to Maven Central yet. Use this checklist when preparing a tagged release.

## Versioning

Use semantic versioning:

- Patch: bug fixes and documentation updates.
- Minor: backward-compatible APIs or matcher capabilities.
- Major: breaking public API or behavior changes.

## Pre-release Checklist

1. Update `pom.xml` from `0.1.0-SNAPSHOT` to the release version.
2. Run:

   ```bash
   mvn test
   make example
   make smoke
   make benchmark
   ```

3. Record benchmark results in the release notes when matching internals changed.
4. Confirm README examples compile against the packaged API.
5. Confirm `LICENSE`, `CONTRIBUTING.md`, and `pom.xml` metadata are current.
6. Commit the version change with `Release <version>`.
7. Tag the release:

   ```bash
   git tag v<version>
   git push origin main --tags
   ```

## GitHub Release Notes

Use this structure:

```markdown
## Highlights

- Main user-facing changes.

## Compatibility

- Breaking changes or "No breaking changes".

## Validation

- `mvn test`
- `make example`
- `make smoke`
- benchmark summary when relevant
```

## Publishing Later

Before publishing to Maven Central, add signing, deploy plugin configuration, and a documented release profile. Keep that configuration out of normal local development until credentials and signing are ready.
