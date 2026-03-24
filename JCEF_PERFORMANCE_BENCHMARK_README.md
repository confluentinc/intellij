# Table Performance Benchmark Tool

A tool for comparing JBTable (IntelliJ's Swing table) vs JCEF table rendering performance directly from the IDE. Used to validate that the JCEF-based consumer table performs acceptably at various data sizes.

## Usage    

1. Run `./gradlew runIde`
2. In the launched IDE, go to top menu **Tools > Table Performance Benchmark**
3. Select row counts, operations, and iteration count
4. Click **OK** to run the benchmark tests - it opens a UI where you can watch them run
5. Benchmarks produce an exportable comparison table showing JBTable vs JCEF timings

## What it measures

- **Batch Insert**: Time to add N rows to an empty table
- **Clear & Replace**: Time to replace all rows with a new dataset

Each operation runs against both JBTable and JCEF implementations. Results show duration, throughput (rows/sec), which implementation is faster, and by what ratio.

## Files

```
src/io/confluent/intellijplugin/
  actions/dev/
    JcefPerformanceBenchmarkAction.kt   # Entry point action for Tools menu
    ui/
      JcefBenchmarkDialog.kt            # Config dialog (test sizes, operations, iterations)
      JcefBenchmarkPanel.kt             # Results panel with comparison table and CSV export
      PerformanceBenchmarkRunner.kt     # Orchestration and result aggregation
      TableBenchmark.kt                 # Table interface + JBTable/JCEF implementations
  consumer/editor/performance/
    PerformanceMetrics.kt               # Data classes for results and CSV export
    SyntheticRecordGenerator.kt         # Generates realistic KafkaRecord test data

resources/jcef/
  benchmark-table.html                  # HTML table with performance instrumentation
```
