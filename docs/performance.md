# Performance Guide

This project includes a lightweight no-dependency benchmark for local comparisons. It is intended to catch large regressions and compare implementation changes on the same machine. It is not a substitute for a full JMH benchmark suite.

## Run The Benchmark

Compile the library and benchmark:

```bash
make benchmark
```

Example output:

```text
exact: iterations=50000, totalMs=..., avgMicros=..., opsPerSecond=..., resultCount=...
match-first: iterations=50000, totalMs=..., avgMicros=..., opsPerSecond=..., resultCount=...
slot-sequence: iterations=50000, totalMs=..., avgMicros=..., opsPerSecond=..., resultCount=...
```

## Reading Results

- `avgMicros` is the average time per match call.
- `opsPerSecond` is useful for comparing changes on the same machine.
- `resultCount` prevents the JVM from optimizing away matching work and confirms the benchmark still returns matches.

Run the benchmark at least three times and compare medians. Use the same JDK, CPU power mode, and input size when comparing commits.

## Current Benchmark Shape

The benchmark builds:

- 1,000 `people` dictionary values.
- 1,000 `song` dictionary values.
- 100 exact templates.
- 100 slot-sequence templates.

It measures:

- Exact template matching for a fully anchored input.
- Best-result matching through `matchFirst` and the Top-K collector.
- Slot-sequence matching with `MatchMode.SLOT_SEQUENCE_ONLY`.

Matching enforces independent limits for input length, visited states, collected slot hits, and returned
results. Keep these limits unchanged when comparing commits so measurements describe the same workload.

## Future Work

For release-quality performance tracking, add a JMH module or Maven profile and publish benchmark results in release notes.
