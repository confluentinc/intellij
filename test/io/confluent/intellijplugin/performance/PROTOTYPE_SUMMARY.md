# Message Viewer Load Testing Prototype

## Summary

This PR introduces a performance testing harness to identify bottlenecks in the current Message Viewer implementation before upgrading to support 1M messages with client-side filtering (porting features from VS Code).

**Key finding**: The current `ListTableModel` FIFO eviction is O(n²), making it unsuitable for large message counts. A new data structure approach is needed.

---

## What Was Built

Three new test files in `test/io/confluent/intellijplugin/performance/`:

| File | Purpose |
|------|---------|
| `MessageViewerLoadTest.kt` | Parameterized load tests measuring insertion, eviction, filtering, and memory |
| `SyntheticKafkaRecordGenerator.kt` | Generates realistic `KafkaRecord` instances with varied payload sizes |
| `PerformanceMetrics.kt` | Utility for timing operations and measuring heap usage |

---

## How to Run

```bash
# Run all performance tests
./gradlew test --tests "io.confluent.intellijplugin.performance.MessageViewerLoadTest"

# Run specific test groups
./gradlew test --tests "*.MessageViewerLoadTest\$InsertionTests"
./gradlew test --tests "*.MessageViewerLoadTest\$FifoEvictionTests"
./gradlew test --tests "*.MessageViewerLoadTest\$FilterTests"
./gradlew test --tests "*.MessageViewerLoadTest\$MemoryTests"
```

Test output is captured in XML format at:
```
build/test-results/test/TEST-io.confluent.intellijplugin.performance.MessageViewerLoadTest$*.xml
```

---

## Key Findings

### 1. FIFO Eviction is O(n²) — CRITICAL

When the model reaches `maxElementsCount`, each insertion triggers `ArrayList.removeFirst()` which shifts all elements.

| Max Elements | Eviction Overhead |
|--------------|-------------------|
| 5,000 | ~1x (JIT warmup) |
| 10,000 | **6x slower** |
| 25,000 | **6x slower** |
| 50,000 | **25x slower** |
| 100,000 | **200x slower** |

**Root cause** (`ListTableModel.kt:47-51`):
```kotlin
if (maxElementsCount != 0 && data.size > maxElementsCount) {
    data.removeFirst()  // O(n) - shifts entire ArrayList
    fireTableRowsDeleted(0, 0)
}
```

**Fix**: Replace with `ArrayDeque` or circular buffer for O(1) eviction.

### 2. Memory Consumption Exceeds Target

| Payload Profile | Bytes/Record | Notes |
|-----------------|--------------|-------|
| SMALL (50-150 bytes) | ~985 | Meets 1KB target |
| MEDIUM (400-600 bytes) | ~1,737 | Exceeds target |
| LARGE (1.8-2.2 KB) | ~4,755 | 4.6x target |
| MIXED (realistic) | ~3,000 | **3x target** |

**Impact**: 1M records with mixed payloads requires ~3GB heap.

**Root cause**: Java String overhead (2 bytes/char + object headers) plus multiple string fields (`key`, `value`, `keyText`, `valueText`, `errorText`, `topic`).

**Potential fixes**:
- Store raw bytes, parse lazily on display
- Deduplicate topic strings
- Use compact string representations

### 3. Insertion Performance (without eviction) — PASSES

| Record Count | Time | Target |
|--------------|------|--------|
| 10,000 | ~5ms | - |
| 50,000 | ~20ms | - |
| 100,000 | ~50ms | <5,000ms ✓ |
| 200,000 | ~100ms | - |

Batch insertion times are sub-millisecond, well under the 100ms EDT blocking threshold.

### 4. Filter Performance — PASSES

| Record Count | Filter Time | Target |
|--------------|-------------|--------|
| 10,000 | ~10ms | - |
| 50,000 | ~50ms | - |
| 100,000 | ~100ms | <500ms ✓ |

Current O(n) filtering is acceptable up to 100K. For 1M records, consider caching filter results with `BitSet`.

---

## Test Design Rationale

### Why observational tests?

The FIFO eviction and memory tests **document behavior** rather than assert thresholds. This is intentional:
- We already know the current implementation won't meet targets at scale
- Hard failures would block the PR without adding value
- The metrics are captured in test output for comparison after fixes

### Why synthetic records?

`SyntheticKafkaRecordGenerator` creates realistic `KafkaRecord` instances with:
- Varied payload sizes (SMALL/MEDIUM/LARGE/MIXED profiles)
- Sequential timestamps for ordering tests
- Distributed partitions for realistic key distribution
- Headers to match production record structure

This gives more accurate memory measurements than minimal test fixtures.

### Why these record counts?

| Count | Rationale |
|-------|-----------|
| 10K | Baseline, fast iteration |
| 50K | Moderate load, ~2 seconds |
| 100K | Target for "good enough" performance |
| 200K | Stress test upper bound |

We avoided 500K-1M in CI due to OOM with default heap. Local testing with `-Xmx4g` is recommended for larger scales.

---

## Recommendations

Based on findings, **Prototype 2 is needed** to implement:

1. **`MessageBuffer<T>`** — `ArrayDeque`-based circular buffer with O(1) eviction
2. **`TimestampIndex`** — `ConcurrentSkipListMap` for O(log n) range queries
3. **`FilterBitSet`** — `java.util.BitSet` wrapper for cached filter results

These use Java standard library collections (per plan revision) rather than custom implementations.

---

## Files Changed

```
test/io/confluent/intellijplugin/performance/
├── MessageViewerLoadTest.kt           # 17 parameterized tests
├── SyntheticKafkaRecordGenerator.kt   # Record factory with payload profiles
├── PerformanceMetrics.kt              # Timing/memory utilities
└── PROTOTYPE_SUMMARY.md               # This document
```

No changes to production code.

---

## Local Testing Notes

For extended testing with larger record counts:

```bash
# Increase heap for 500K+ records
./gradlew test --tests "*.MessageViewerLoadTest" -Dorg.gradle.jvmargs="-Xmx4g"
```

To see detailed output:
```bash
cat build/test-results/test/TEST-io.confluent.intellijplugin.performance.MessageViewerLoadTest\$FifoEvictionTests.xml
```
