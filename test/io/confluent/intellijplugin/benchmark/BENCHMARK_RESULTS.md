# ListTableModel capacity benchmark — results

> **Throwaway / branch-only.** This and `ListTableModelBenchmark.kt` live only on
> `ncothren/perf-list-table-model-benchmark` to inform the 10k-vs-50k default buffer-capacity
> decision for the consumer message viewer. **Not for merge to main.**

Stacked on the sliding-window wrap-event fix (PR #619). Measured on Apple Silicon, JDK 21,
test JVM `-Xmx3g`. Numbers are approximate — read as orders of magnitude.

## Reproduce

```bash
./gradlew test --tests "*ListTableModelBenchmark" -Dbenchmark.listTableModel=true
# output is captured in build/test-results/test/TEST-*ListTableModelBenchmark*.xml (<system-out>)
```

## Retained heap once the buffer is full (MB)

| capacity | 256 B | 2 KB | 16 KB |
|---------:|------:|-----:|------:|
| 10,000   |   4.5 | 21.5 |   161 |
| 50,000   |  21.6 |  107 |   796 |

Linear in capacity (~5× records → ~5× heap), as expected.

## Steady-state flush EDT time — p50 / p99 ms

Wrapped buffer (every flush evicts+inserts), batch = 200 records, 500 timed flushes after 100 warmup.
No active sort column. Measures `flushPendingAdds` + synchronous `TableRowSorter` maintenance;
excludes async `repaint()`.

| capacity | 256 B      | 2 KB       | 16 KB      |
|---------:|:-----------|:-----------|:-----------|
| 10,000   | 0.04 / 0.13 | 0.02 / 0.10 | 0.03 / 0.13 |
| 50,000   | 0.02 / 0.11 | 0.02 / 0.08 | 0.02 / 0.15 |

## Interpretation

- **Responsiveness is a non-issue.** Per-flush EDT cost is ~0.1 ms p99 and **flat across capacity** —
  50k is indistinguishable from 10k. The sliding-window fix means flush cost no longer scales with
  buffer size, so bumping the default to 50k costs nothing in "jumpiness." This directly answers the
  PR reviewer's concern.
- **Heap is the only real cost.** At typical text-message sizes (≤ 2 KB), 50k is ~22–107 MB —
  acceptable for an IDE plugin. At large messages (16 KB), 50k reaches ~800 MB live, which is heavy
  for a shared IDE JVM and is the scenario to watch.

## Recommendation

Adopt 50k. The responsiveness objection is settled by the flush-latency data. The residual risk is
large-payload topics (≈ 1 GB at 50k × big messages); if that proves real, the cleaner lever than a
smaller row cap is a memory-aware/byte-budget cap layered on the existing `maxElementsCount` soft
cap — a separate feature, not a blocker for the 50k bump.

### Caveats

- Heap is gc + `Runtime` delta — approximate, not exact.
- Flush latency excludes async paint and assumes no active sort column (the reported default view).
  Sorting by a column adds incremental cost at both capacities.
