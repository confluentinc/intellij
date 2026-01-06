# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **Kafka Plugin for JetBrains IDEs** - an IntelliJ platform plugin that enables developers to work with Apache Kafka directly from IntelliJ-based IDEs. The plugin is developed by Confluent.

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run tests
./gradlew test

# Run single test class
./gradlew test --tests "io.confluent.intellijplugin.KafkaMigrationTest"

# Run single test method
./gradlew test --tests "io.confluent.intellijplugin.KafkaMigrationTest.testMethodName"

# Run IDE with plugin installed (development mode)
./gradlew runIde

# Build plugin ZIP for distribution
./gradlew buildPlugin
```

### Prerequisites

- JDK 21 or later
- Gradle 8.5 or later
- Install prerequisites via SDKMAN: `sdk env install`

### Application Secrets (for telemetry/Sentry)

```bash
vault_login
. scripts/get-secrets.sh
./gradlew build
```

## Architecture

### Source Structure

All source code is under `src/io/confluent/intellijplugin/`:

- **`core/`** - Core plugin infrastructure
  - `settings/` - Connection settings UI and management
  - `rfs/` - Remote File System abstraction for Kafka resources
  - `monitoring/` - Monitoring and data visualization components
  - `connection/` - Connection lifecycle, tunneling, exceptions
  - `ui/` - Shared UI components (choosers, filters)

- **`client/`** - Kafka client wrappers (`BdtKafkaAdminClient`, `KafkaClientBuilder`)

- **`consumer/`** - Consumer functionality (client, editor UI, models)

- **`producer/`** - Producer functionality (client, editor UI, models)

- **`registry/`** - Schema Registry integration
  - `confluent/` - Confluent Schema Registry
  - `glue/` - AWS Glue Schema Registry
  - `serde/` - Serializers/deserializers

- **`aws/`** - AWS integration (MSK IAM auth, SSO, credentials, profiles)

- **`ccloud/`** - Confluent Cloud integration (OAuth authentication)

- **`telemetry/`** - Sentry error reporting and Segment analytics

- **`toolwindow/`** - Main Kafka tool window UI

- **`common/`** - Shared components (editor, settings, models)

- **`spring/`** - Spring Boot Kafka integration

### Key Extension Points

The plugin defines extension point `connectionSettingProvider` in `plugin.xml` for custom connection settings.

### Generated Configuration

Build generates `SentryConfig.kt` and `SegmentConfig.kt` from environment variables at compile time.

## Testing

### Framework and Dependencies

- **JUnit 5** (Jupiter) for test execution
- **mockito-kotlin** for mocking (preferred over plain Mockito)
- **@TestApplication** annotation required for tests using IntelliJ Platform APIs
- JUnit 4 runtime included due to IntelliJ Platform compatibility (IJPL-159134)

### Running Tests

```bash
# Run all tests
./gradlew test

# Run single test class
./gradlew test --tests "io.confluent.intellijplugin.TelemetryServiceTest"

# Run single test method
./gradlew test --tests "io.confluent.intellijplugin.TelemetryServiceTest.sendTrackEvent respects user opt-out"

# Run tests matching pattern
./gradlew test --tests "*Telemetry*"
```

### Test Structure

Tests are in `test/io/confluent/intellijplugin/`:

- `KafkaMigrationTest` - Connection migration tests
- `GenerateRandomDataTest` - Schema data generation (Avro, Protobuf, JSON Schema)
- `rfs/ConnectionDataTest` - Connection serialization
- `telemetry/` - Telemetry and action listener tests
- `ccloud/auth/` - OAuth flow tests

### Writing Tests

**Basic test structure:**
```kotlin
@TestApplication  // Required for IntelliJ Platform API access
class MyServiceTest {

    @BeforeEach
    fun setUp() {
        // Initialize test fixtures
    }

    @AfterEach
    fun tearDown() {
        // Cleanup, restore original state
    }

    @Test
    fun `descriptive test name with backticks`() {
        // Test implementation
    }
}
```

**Nested test groups** (organize related tests):
```kotlin
@TestApplication
class TelemetryServiceTest {

    @Nested
    @DisplayName("sendTrackEvent")
    inner class SendTrackEvent {

        @BeforeEach
        fun setUpTrackEvent() {
            // Setup specific to this group
        }

        @Test
        fun `respects user opt-out`() { ... }

        @Test
        fun `sends events when user opted in`() { ... }
    }
}
```

### Mocking with mockito-kotlin

**Stubbing pattern:**
```kotlin
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.never

private lateinit var mockAnalytics: Analytics

@BeforeEach
fun setUp() {
    mockAnalytics = mock(Analytics::class.java)
}

@Test
fun `example test`() {
    // Stub behavior
    doThrow(RuntimeException("Test exception")).`when`(mockAnalytics).enqueue(any())

    // Verify interactions
    verify(mockAnalytics, times(1)).enqueue(any())
    verify(mockAnalytics, never()).flush()
}
```

### Test Configuration

System properties can be passed to tests via `build.gradle.kts`:
```kotlin
test {
    useJUnitPlatform()
    systemProperty("ccloud.callback-port", "26639")
}
```

## UI Development (Kotlin UI DSL)

This plugin uses **Kotlin UI DSL Version 2** (`com.intellij.ui.dsl.builder`) for building UI components.

### Core Imports

```kotlin
import com.intellij.ui.dsl.builder.*
```

### Basic Panel Structure

```kotlin
val myPanel = panel {
    group("Group Title") {
        row("Label:") {
            textField().align(AlignX.FILL)
        }
        row {
            checkBox("Enable feature").bindSelected(settings::enableFeature)
        }
    }
}
```

### Key UI DSL Concepts

**Rows and Cells:**
```kotlin
panel {
    row("Field Label:") {
        cell(myComponent).align(AlignX.FILL).resizableColumn()
    }
    row {
        label("Status")
        textField()
        contextHelp("Help text", "Title")
    }.layout(RowLayout.PARENT_GRID)
}
```

**Visibility Control with `rowsRange`:**
```kotlin
lateinit var settingsGroup: RowsRange

panel {
    settingsGroup = rowsRange {
        row { /* content */ }
    }
}

// Later: toggle visibility
settingsGroup.visible(condition)
```

**Indentation and Nesting:**
```kotlin
panel {
    row { checkBox("Enable SASL") }
    indent {
        row("Username:") { textField() }
        row("Password:") { passwordField() }
    }
}
```

### Custom UI Extensions

The plugin defines helper extensions in `core/ui/BdtUiDslExtensions.kt`:

```kotlin
// Add labeled component that fills horizontally
fun Panel.row(component: WrappedNamedComponent<*>): Row

// Add component that fills both directions
fun Panel.block(component: JComponent): Row
```

### Validation Pattern

Validators are defined in `core/settings/ValidationUtils.kt`:

```kotlin
// Add validator to text field
myTextField.withValidator(uiDisposable) { text ->
    if (text.isBlank()) "Field cannot be empty" else null
}

// Common validators
field.withNotEmptyValidator(uiDisposable)
field.withNumberValidator(uiDisposable)
field.withPortValidator(uiDisposable)
field.withUrlValidator(uiDisposable)
field.withEmptyOrFileExistValidator(uiDisposable, canBeEmpty = false)
```

**Registering validators with ComponentValidator:**
```kotlin
val validator = Supplier {
    if (condition) ValidationInfo("Error message", component) else null
}
ComponentValidator(uiDisposable)
    .withValidator(validator)
    .andStartOnFocusLost()
    .installOn(component)
```

### Settings Configurable Pattern

```kotlin
class MySettingsConfigurable : SearchableConfigurable {
    private lateinit var panel: DialogPanel

    override fun createComponent(): JComponent {
        panel = panel {
            group("Settings Group") {
                row {
                    checkBox("Option").bindSelected(settings::option)
                }
            }
        }
        return panel
    }

    override fun isModified(): Boolean = panel.isModified()
    override fun apply() { panel.apply() }
    override fun reset() { panel.reset() }
}
```

### Dialog Pattern

```kotlin
val builder = DialogBuilder()
builder.addOkAction()
builder.addCancelAction()
builder.title("Dialog Title")

val centerPanel = panel {
    row("Name:") {
        cell(nameField).align(AlignX.FILL).resizableColumn()
    }
}
builder.centerPanel(centerPanel)

if (builder.showAndGet()) {
    // User clicked OK
}
```

### Wrapped Component Pattern

The plugin uses `WrappedNamedComponent<D>` (in `core/settings/fields/`) to bind UI components to connection data:

```kotlin
class KafkaBrokerSettings(
    val connectionData: KafkaConnectionData,
    private val uiDisposable: Disposable,
    // ...
) {
    val propertiesSource = RadioGroupField(
        KafkaConnectionData::propertySource,
        KEY,
        connectionData,
        KafkaPropertySource.entries
    ).apply {
        addItemListener { onUpdatePropertiesSource() }
    }
}
```

### Message Bundles

UI strings come from `messages/KafkaBundle.properties`:

```kotlin
KafkaMessagesBundle.message("dialog.create.topic.name")
```

## Plugin Configuration

- **Plugin ID**: `com.intellij.bigdatatools.kafka`
- **Plugin XML**: `resources/META-INF/plugin.xml`
- **Message Bundle**: `messages/KafkaBundle`
- **Target IDE version**: IntelliJ IDEA 2025.3+
