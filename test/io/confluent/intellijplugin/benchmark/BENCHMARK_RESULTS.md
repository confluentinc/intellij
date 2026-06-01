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
| 10,000   |   4.5 | 20.5 |   159 |
| 50,000   |  21.6 |  107 |   796 |

Linear in capacity (~5× records → ~5× heap), as expected.

## Steady-state flush EDT time

Wrapped buffer (every flush evicts+inserts), batch = 200 records. Measures `flushPendingAdds` +
synchronous `TableRowSorter` maintenance; excludes async `repaint()`. The model carries production
`columnClasses`, so the sorter uses the real comparators `setupSorters` would attach.

**Unsorted (the default view — no active sort column)** — p50 / p99 ms, 500 flushes:

| capacity | 256 B      | 2 KB       | 16 KB      |
|---------:|:-----------|:-----------|:-----------|
| 10,000   | 0.04 / 0.10 | 0.02 / 0.11 | 0.02 / 0.13 |
| 50,000   | 0.02 / 0.06 | 0.02 / 0.04 | 0.02 / 0.10 |

**Sorted by Timestamp descending (Date column, numeric compare)** — p50 / p99 ms, 500 flushes:

| capacity | 256 B      | 2 KB       | 16 KB      |
|---------:|:-----------|:-----------|:-----------|
| 10,000   | 0.09 / 0.15 | 0.08 / 0.13 | 0.07 / 0.15 |
| 50,000   | 0.26 / 0.32 | 0.20 / 0.28 | 0.18 / 0.30 |

Timestamp is monotonic with arrival, so a descending sort inserts every new row at the head of the
view — a near-worst-case for the sorter's view-index maintenance.

**Sorted by Value descending (String column)** — p50 / max **ms per flush**, few samples:

| capacity | 256 B      | 2 KB        | 16 KB           |
|---------:|:-----------|:------------|:----------------|
| 10,000   | 41 / 43    | 324 / 349   | 2583 / 2773     |
| 50,000   | 49 / 49    | 392 / 395   | 3135 / 3141     |

`setupSorters` registers comparators only for `Int`/`Long`, so a String column falls through to
`DefaultRowSorter`'s default: a locale-aware `java.text.Collator`. At full 500-flush counts the 16 KB
cells did not finish in 40+ minutes, so this variant runs with a tiny sample count.

## Interpretation

- **Unsorted: flat and free.** ~0.02–0.13 ms p99 with no capacity trend — 50k is indistinguishable
  from 10k. The sliding-window fix makes the delete+insert events O(batch), not O(buffer).
- **Numeric/Date sort: scales mildly with capacity, still negligible.** p50 rises ~0.07 ms → ~0.20 ms
  (10k → 50k) as the sorter maintains its view-index arrays across the buffer, and is flat across
  value size (it's array-shuffle work, not comparison). The 50k worst case (~0.32 ms p99) is far under
  the ~16 ms frame budget — not user-perceptible.
- **String sort is the cliff, and it's driven by value size, not capacity.** Cost is dominated by
  `Collator.compare` scanning the full payload on every comparison; `insertInOrder` is a log-n binary
  search, so 50k is only ~1.2× slower than 10k (the log factor) while **8× the value size is ~8× the
  cost**. Even the cheapest cell (256 B) is **~41 ms/flush — already over a frame**, i.e. visibly
  janky; 2 KB is **~350 ms**; 16 KB is a **~3-second EDT freeze per flush**. That's 3–4 orders of
  magnitude worse than the Date sort.
- **Heap is the only capacity-linked cost.** At ≤ 2 KB, 50k is ~22–107 MB — fine for an IDE. At 16 KB,
  50k reaches ~800 MB live — heavy for a shared JVM, the scenario to watch.

## Recommendation

**The 50k bump is safe on responsiveness grounds.** In every view whose cost depends on capacity
(unsorted, numeric/Date sort) 50k stays sub-millisecond per flush. The only capacity-linked cost is
heap; the residual risk is large-payload topics (≈ 1 GB at 50k × big messages), for which the cleaner
lever than a smaller row cap is a memory-aware/byte-budget cap on the existing `maxElementsCount` soft
cap — a separate feature, not a blocker.

**Separately — and not a function of the 10k-vs-50k decision — sorting by a String column (Key/Value)
is effectively unusable during live tailing**, freezing the EDT for tens of ms (small messages) to
seconds (large messages) on every flush. This is a pre-existing problem (present at 10k too); 50k only
makes it ~20% worse. The fix is orthogonal to capacity: register a cheap comparator for String columns
(`String.compareTo` / `naturalOrder` instead of the `Collator` default), and/or follow the VS Code
extensions' approach of not maintaining a live sorted view while tailing (page over insertion order;
reserve true sort for a paused/snapshot view or an incrementally-maintained index). Worth a follow-up
ticket independent of this PR.

## Cross-reference: how other message viewers handle this

Before treating live column sorting as a must-have we cracked open how the mainstream VS Code Kafka
extensions handle a large, continuously-updating message list. The short version: **neither offers
user column sorting at all** — they sidestep the problem this benchmark exposes rather than solving
it.

- **Confluent for VS Code** (`confluentinc/vscode`) — has a real message-viewer webview with
  timestamp/partition/offset/key/value columns, but **column sorting is deliberately disabled**. The
  host holds messages in a `CircularBuffer` with incrementally-maintained `SkipList` indexes
  (timestamp-desc, partition-asc) plus `BitSet` filters; rows are served in timestamp-descending order
  straight off the skip-list lane, and the webview only ever holds **one page (≤100 rows)** via
  host-side pagination. An arbitrary-column sort path exists but is dead code:
  `// TEMP (July 12th) disabling this since we don't have sorting feature yet` (`src/stream/stream.ts`).
  When the buffer fills it **pauses the stream** rather than churning.
- **jlandersen/vscode-kafka** ("Tools for Apache Kafka") — also a webview viewer, but the `<th>`
  headers are static labels with no click/sort handlers. Order is always insertion-order-reversed
  (`getFilteredMessagesDescending()` = `…slice().reverse()`); cap enforced by `splice(0, overflow)`;
  paginated host-side (≤1000/page). No comparator on any message field.

**The pattern both converge on:** never maintain a live *sorted view* during tailing. They (a) keep
the client thin via host-side pagination so it never holds tens of thousands of rows to sort, (b)
serve a fixed natural order (insertion ≈ timestamp-desc) that is free for an append/evict buffer, and
(c) where ordered-by-key access is genuinely needed, use an incrementally-maintained index
(O(log n)/event) rather than a row sorter that re-validates its whole view on every insert/delete.

This is directly relevant to us: our `JTable` + `TableRowSorter` *does* re-maintain a sorted view on
every flush (what the sorted runs above measure). For the cheap (numeric/Date) case that's fine; for
the String/`Collator` case it's the cliff documented above. The proven industry answer to "live sort
is expensive" is not a smaller row cap — it's to not maintain a live sorted view while tailing, and to
reserve true sort for a paused/snapshot view or an incremental index. Confluent's own VS Code team
reached the same conclusion (sort path shipped disabled).

Sources: `confluentinc/vscode` (`src/webview/message-viewer.ts`, `src/consume.ts`,
`src/stream/stream.ts`); `jlandersen/vscode-kafka` (`src/providers/consumerTableViewProvider.ts`,
`src/providers/consumerSession.ts`).

### Caveats

- Heap is gc + `Runtime` delta — approximate, not exact.
- Flush latency excludes async paint. p50 is the reliable signal.
- The String-sort variant uses values with a long shared prefix (so `Collator` scans the whole
  payload) and only a handful of samples — read it as "this is catastrophic," not as a precise figure.
  A naive `String.compareTo` would be far cheaper than `Collator` but still scans the shared prefix, so
  large-value string sorts would remain costly.
