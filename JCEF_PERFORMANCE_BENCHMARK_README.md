# Table Performance Benchmark Tool

A developer tool for comparing JTable vs JCEF table rendering performance directly from the IDE. Use it to validate that the JCEF-based consumer table performs acceptably at various data sizes.

## Usage

1. Run `./gradlew runIde`
2. In the launched IDE, go to **Tools > Table Performance Benchmark**
3. Select test sizes (row counts), operations, and iteration count
4. Click **OK** to run the benchmark
5. Review the comparison table showing JTable vs JCEF timings
6. Optionally export results to CSV

## What it measures

- **Batch Insert**: Time to add N rows to an empty table
- **Clear & Replace**: Time to replace all rows with a new dataset (pre-fills the table first)

Each operation runs against both JTable and JCEF implementations. Results show duration, throughput (rows/sec), which implementation is faster, and by what ratio.

## File structure

```
src/io/confluent/intellijplugin/
  actions/dev/
    JcefPerformanceBenchmarkAction.kt   # IDE action entry point (Tools menu)
    ui/
      JcefBenchmarkDialog.kt            # Config dialog (test sizes, operations, iterations)
      JcefBenchmarkPanel.kt             # Results panel with comparison table and CSV export
      PerformanceBenchmarkRunner.kt     # Benchmark orchestration and result aggregation
      TableBenchmark.kt                 # TableBenchmark interface + JTable/JCEF implementations
  consumer/editor/performance/
    PerformanceMetrics.kt               # Data classes for results and CSV export
    SyntheticRecordGenerator.kt         # Generates realistic KafkaRecord test data

resources/jcef/
  benchmark-table.html                  # Virtualized HTML table with performance instrumentation
```

## Reviewer notes

- The benchmark action is registered in `plugin.xml` under `ToolsMenu` with id `Kafka.Dev.JcefPerformanceBenchmark`
- JCEF benchmarks use `window.__perfCallback` to report render timing from JavaScript back to Kotlin
- JTable benchmarks measure `paintImmediately` time on EDT for a direct comparison
- If JCEF is unavailable on the platform, the tool falls back to JTable-only mode
- The HTML template (`benchmark-table.html`) implements virtual scrolling identical to the production consumer table
- Synthetic data uses a fixed random seed for reproducible results across runs
