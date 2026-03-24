# Table Performance Benchmark Tool

A tool for comparing JTable vs JCEF table rendering performance directly from the IDE. Used to validate that the JCEF-based consumer table performs acceptably at various data sizes.

## Usage

1. Run `./gradlew runIde`
2. In the launched IDE, go to top menu **Tools > Table Performance Benchmark**
3. Select row counts, operations, and iteration count
4. Click **OK** to run the benchmark tests - it opens a UI where you can watch them run
5. Benchmarks produce an exportable comparison table showing JTable vs JCEF timings

## What it measures

- **Batch Insert**: Time to add N rows to an empty table
- **Clear & Replace**: Time to replace all rows with a new dataset

Each operation runs against both JTable and JCEF implementations. Results show duration, throughput (rows/sec), which implementation is faster, and by what ratio.

## Files

```
src/io/confluent/intellijplugin/
  actions/dev/
    JcefPerformanceBenchmarkAction.kt   # Entry point action for Tools menu
    ui/
      JcefBenchmarkDialog.kt            # Config dialog (test sizes, operations, iterations)
      JcefBenchmarkPanel.kt             # Results panel with comparison table and CSV export
      PerformanceBenchmarkRunner.kt     # Orchestration and result aggregation
      TableBenchmark.kt                 # Table interface + JTable/JCEF implementations
  consumer/editor/performance/
    PerformanceMetrics.kt               # Data classes for results and CSV export
    SyntheticRecordGenerator.kt         # Generates realistic KafkaRecord test data

resources/jcef/
  benchmark-table.html                  # HTML table with performance instrumentation
```

## Implementation Details

- The benchmark action is registered in `plugin.xml` under `ToolsMenu` with id `Kafka.Dev.JcefPerformanceBenchmark`
- JCEF benchmarks use `window.__perfCallback` to report render timing from JavaScript back to Kotlin
- JTable benchmarks measure `paintImmediately` time on EDT for a direct comparison
- The HTML template (`benchmark-table.html`) implements virtual scrolling (lazy loading) to me closer to the existing consumer table. In benchmark-table.html, only the rows visible in the viewport (plus a buffer of 30 rows) are rendered as DOM nodes. Top/bottom spacer <tr> elements fake the scroll height. On scroll, it recalculates which  
  rows to render. Another option for rendering would be pagination.
- Synthetic data uses a fixed random seed for reproducible results across runs
