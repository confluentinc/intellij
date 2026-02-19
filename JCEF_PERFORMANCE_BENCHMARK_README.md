# JCEF Performance Benchmark Tool - Implementation Summary

## Overview

This implementation provides an interactive performance testing tool for the JCEF-based HTML table (`JcefRecordsTable`) introduced in PR #365. The tool allows developers to benchmark DOM manipulation performance with zero setup directly from the IDE.

## What Was Implemented

### 1. Core Architecture

**Location**: `src/io/confluent/intellijplugin/`

#### Data Models & Utilities (`consumer/editor/performance/`)
- **`PerformanceMetrics.kt`** - Data classes for benchmark results and CSV export
  - `PerformanceMetrics` - Captures operation name, row count, duration, throughput, and memory delta
  - `BenchmarkConfig` - Configuration for test sizes, operations, and iterations
  - `PerformanceReporter` - Formats results as tables and exports to CSV

- **`SyntheticRecordGenerator.kt`** - Generates realistic test data
  - Creates `KafkaRecord` objects with configurable size and error rates
  - Uses fixed random seed for reproducibility
  - Generates JSON payloads with configurable field counts

#### UI Components (`actions/dev/ui/`)
- **`PerformanceBenchmarkRunner.kt`** - Core benchmark execution logic
  - Creates JCEF table instance
  - Runs three types of benchmarks:
    - **Batch Insert**: Measures adding N rows in one operation
    - **Clear & Replace**: Measures replacing entire table contents
    - **Memory Stress**: Performs 5 insert/clear cycles to detect memory leaks
  - Calculates memory usage before/after operations
  - Aggregates results across multiple iterations

- **`JcefBenchmarkDialog.kt`** - Configuration UI
  - Checkboxes for test sizes: 100, 1K, 5K, 10K, 25K, 50K, 100K rows
  - Operation selection: Insert, Replace, Memory Stress
  - Iteration count (1-10) for result averaging
  - Uses Kotlin UI DSL v2

- **`JcefBenchmarkPanel.kt`** - Results display UI
  - Progress bar showing test execution status
  - Results table with sortable columns
  - Export to CSV functionality
  - Proper coroutine scope management

#### Main Action (`actions/dev/`)
- **`JcefPerformanceBenchmarkAction.kt`** - IDE action entry point
  - Checks JCEF platform support
  - Launches configuration dialog
  - Shows results in modal dialog
  - Registered in Tools menu

### 2. Enhanced HTML Template

**Location**: `resources/jcef/performance-test-table.html`

Enhanced version of `consumer-table.html` with:
- Performance timing instrumentation using `performance.now()`
- Callback mechanism for reporting metrics back to Kotlin
- Tracks DOM node count and operation duration
- Reports for `addRows`, `clearTable`, and `replaceAllRows` operations

### 3. Plugin Registration

**Modified**: `resources/META-INF/plugin.xml`

Added action registration:
```xml
<action id="Kafka.Dev.JcefPerformanceBenchmark"
        class="io.confluent.intellijplugin.actions.dev.JcefPerformanceBenchmarkAction"
        text="JCEF Performance Benchmark"
        description="Run DOM manipulation performance tests on JCEF browser">
    <add-to-group group-id="ToolsMenu" anchor="last"/>
</action>
```

## File Structure

```
intellij/
├── src/io/confluent/intellijplugin/
│   ├── actions/dev/
│   │   ├── JcefPerformanceBenchmarkAction.kt      # Main action (NEW)
│   │   └── ui/
│   │       ├── JcefBenchmarkDialog.kt             # Config dialog (NEW)
│   │       ├── JcefBenchmarkPanel.kt              # Results panel (NEW)
│   │       └── PerformanceBenchmarkRunner.kt      # Benchmark logic (NEW)
│   └── consumer/editor/
│       ├── JcefRecordsTable.kt                    # Table being tested (EXISTING)
│       ├── KafkaRecord.kt                         # Data model (EXISTING)
│       └── performance/
│           ├── PerformanceMetrics.kt              # Metrics classes (NEW)
│           └── SyntheticRecordGenerator.kt        # Test data gen (NEW)
├── resources/
│   ├── jcef/
│   │   ├── consumer-table.html                    # Base template (EXISTING)
│   │   └── performance-test-table.html            # Enhanced version (NEW)
│   └── META-INF/
│       └── plugin.xml                             # Action registration (MODIFIED)
└── JCEF_PERFORMANCE_BENCHMARK_README.md           # This file (NEW)
```

## How to Use

### 1. Build and Launch IDE

```bash
./gradlew runIde
```

### 2. Access the Tool

In the launched IDE:
- Navigate to **Tools > JCEF Performance Benchmark**

### 3. Configure Benchmark

In the configuration dialog:
1. **Select Test Sizes**: Check boxes for desired row counts
   - Recommended initial run: 100, 1K, 10K, 50K, 100K
2. **Select Operations**:
   - Batch Insert (recommended)
   - Clear & Replace (optional)
   - Memory Stress Test (optional)
3. **Set Iterations**: 3 (for averaging)
4. Click **OK**

### 4. View Results

The results panel shows:
- **Progress**: Real-time updates as tests execute
- **Results Table**:
  - Operation name
  - Row count
  - Duration (ms)
  - Throughput (rows/sec)
  - Memory delta (MB)
- **Export Button**: Save results as CSV for analysis

### 5. Interpret Results

**Expected Performance** (rough guidelines):
- **100 rows**: < 20ms
- **1K rows**: < 100ms
- **10K rows**: < 1s
- **50K rows**: < 5s
- **100K rows**: < 10s
- **Memory delta**: < 1MB per 1K rows

**Red Flags**:
- ⚠️ Duration increases super-linearly with row count
- ⚠️ Memory delta > 2MB per 1K rows
- ⚠️ Memory stress test shows increasing memory (leak)
- ⚠️ Browser timeout errors

## Testing the Implementation

### Verify Installation

```bash
# Check files exist
ls -la src/io/confluent/intellijplugin/actions/dev/
ls -la src/io/confluent/intellijplugin/consumer/editor/performance/
ls -la resources/jcef/performance-test-table.html

# Verify build
./gradlew build
```

### Manual Testing Checklist

1. **Basic Functionality**
   - [ ] Action appears in Tools menu
   - [ ] Configuration dialog opens
   - [ ] All checkboxes work
   - [ ] Results panel appears after clicking OK
   - [ ] Progress bar updates during execution

2. **Performance Tests**
   - [ ] 100 rows benchmark completes successfully
   - [ ] 1K rows benchmark completes successfully
   - [ ] 10K rows benchmark completes successfully
   - [ ] 50K rows benchmark completes successfully
   - [ ] 100K rows benchmark completes successfully

3. **Results Verification**
   - [ ] Results table populates with data
   - [ ] All metrics are reasonable (no negative values)
   - [ ] Export to CSV works
   - [ ] CSV file contains correct data

4. **Edge Cases**
   - [ ] Running benchmark multiple times (memory stability)
   - [ ] Closing results panel during execution
   - [ ] Very large row counts (stress test)
   - [ ] Zero operations selected (should disable OK button)

## Technical Details

### Threading Model

- **Configuration Dialog**: Runs on EDT (UI thread)
- **Benchmark Execution**: Runs on `Dispatchers.Default` (background)
- **JCEF Operations**: Dispatched to `Dispatchers.EDT` when needed
- **Progress Updates**: Posted to EDT via `SwingUtilities.invokeLater`

### Memory Measurement

```kotlin
fun getMemoryUsageMB(): Double {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory())
    return usedMemory / (1024.0 * 1024.0)
}
```

- Calls `System.gc()` before measurements
- Waits 100ms for GC to complete
- Measures used memory (total - free)

### JCEF Browser Lifecycle

1. **Initialization**: Browser created in `PerformanceBenchmarkRunner.init`
2. **Ready Check**: Simple timeout-based wait (5 seconds)
3. **Operations**: Execute via `Dispatchers.EDT`
4. **Cleanup**: Disposed via parent `Disposable`

### Synthetic Data Generation

- **Fixed Seed**: Ensures reproducible results across runs
- **JSON Payloads**: ~50 chars per field, configurable field count
- **Record Structure**: Matches real Kafka consumer records
- **Error Injection**: Optional (disabled by default)

## Future Enhancements

Potential improvements not in current scope:

1. **More Accurate Timing**
   - Use JavaScript callback to measure actual render completion
   - Currently uses simple delay heuristic

2. **Additional Benchmarks**
   - Scrolling performance
   - Row selection/deselection
   - Column resizing
   - Search/filter operations

3. **Comparison Mode**
   - Compare JCEF vs Swing table
   - Before/after optimization comparisons

4. **Automated Regression Detection**
   - Store baseline results
   - Alert on performance degradation

5. **Extended Metrics**
   - CPU usage during operations
   - Browser process memory
   - JavaScript heap size

## Troubleshooting

### Build Errors

```bash
# Clean build
./gradlew clean build

# Check Kotlin version
./gradlew dependencies | grep kotlin
```

### JCEF Not Available

- Check platform: macOS, Windows, Linux (x64)
- Verify IDE version: 2025.3+
- Check JBCefApp.isSupported() returns true

### Performance Issues

If benchmarks are very slow:
1. Check CPU/memory availability
2. Close other applications
3. Try smaller test sizes first
4. Check for background indexing in IDE

### Memory Errors

If out of memory:
1. Increase IDE heap: Help > Edit Custom VM Options
2. Add `-Xmx4g` or higher
3. Run fewer/smaller tests

## Related Files

- **PR #365**: Original JCEF table implementation
- **CLAUDE.md**: Project-wide guidelines
- **JcefRecordsTable.kt**: Core table implementation being tested

## Success Criteria

✅ **Complete** when:
- Tool launches with zero manual setup
- All test sizes (100 to 100K rows) complete successfully
- Performance metrics are reasonable and reproducible
- Memory usage is stable (no leaks after repeated runs)
- Visual feedback is clear and responsive
- Results can be exported to CSV
- Can be used to validate PR #365 JCEF table performance before merging

## Implementation Notes

- Uses IntelliJ Platform patterns: Services, Disposables, Coroutines
- Follows Kotlin UI DSL v2 for all dialogs
- Adheres to EDT threading rules
- Properly registers disposables to prevent leaks
- Uses project-standard logging with `thisLogger()`
