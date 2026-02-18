# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Kafka Plugin for JetBrains IDEs** - IntelliJ platform plugin enabling developers to work with Apache Kafka directly from JetBrains IDEs. Developed by Confluent.

- **Plugin ID:** `com.intellij.bigdatatools.kafka`
- **Target IDE:** IntelliJ IDEA 2025.3+

## Build & Test Commands

```bash
./gradlew build                    # Build plugin
./gradlew test                     # Run all tests
./gradlew test --tests "*.SomeTest"                    # Single class
./gradlew test --tests "*.SomeTest.methodName"         # Single method
./gradlew runIde                   # Launch IDE with plugin installed
./gradlew buildPlugin              # Build distribution ZIP
```

**Prerequisites:** JDK 21+, Gradle 8.5+ (install via `sdk env install`)

## Source Structure

All source code under `src/io/confluent/intellijplugin/`:

| Directory                  | Purpose                                                                      |
|----------------------------|------------------------------------------------------------------------------|
| `core/`                    | Plugin infrastructure: settings, RFS (Remote File System), connections, UI  |
| `client/`                  | Kafka client wrappers (`BdtKafkaAdminClient`, `KafkaClientBuilder`)          |
| `consumer/`, `producer/`   | Consumer and producer functionality                                          |
| `registry/`                | Schema Registry integration (Confluent + AWS Glue)                           |
| `aws/`                     | AWS/MSK integration (IAM auth, SSO, credentials)                             |
| `ccloud/`                  | Confluent Cloud OAuth authentication                                         |
| `telemetry/`               | Sentry error reporting + Segment analytics                                   |

**Key files:**

- `resources/META-INF/plugin.xml` - Plugin descriptor, extension points, actions
- `resources/messages/KafkaBundle.properties` - Localized UI strings

**Persisted settings** (stored in IDE config via `@State`/`@Storage`):

| Storage File                              | Purpose                              | Service Class                     |
|-------------------------------------------|--------------------------------------|-----------------------------------|
| `confluent_kafka_settings.xml`            | Kafka connection configs             | `Global/LocalConnectionSettings`  |
| `kafka_plugin_settings.xml`               | Plugin preferences (telemetry, etc.) | `KafkaPluginSettings`             |
| `confluent-kafka-config-template.xml`     | Consumer/producer run configs        | `KafkaConfigStorage`              |
| `confluent_kafka_toolwindow.xml`          | Tool window UI state                 | `KafkaToolWindowSettings`         |
| `confluent_kafka_statistics_settings.xml` | Usage statistics prefs               | `StatisticsSettings`              |
| `confluent_kafka_kerberos_settings.xml`   | Kerberos auth settings               | `KerberosSettings`                |

## IntelliJ Platform Patterns

### Services

```kotlin
// Project-scoped (one instance per project)
@Service(Service.Level.PROJECT)
class MyService {
    companion object {
        fun getInstance(project: Project) = project.service<MyService>()
    }
}

// Application-scoped (singleton)
@Service(Service.Level.APP)
class MyAppService {
    companion object {
        fun getInstance() = service<MyAppService>()
    }
}
```

### Generated Configuration

Build generates `SentryConfig.kt` and `SegmentConfig.kt` from environment variables at compile time.

### Persistent State

Use `@State` + `@Storage` for persisted settings (see `core/settings/LocalConnectionSettings.kt`).

### Threading & Coroutines

**Never block EDT.** Use coroutines (preferred for 2024.1+) with proper scopes and dispatchers.

**Coroutine Scopes** - Use service scope injection (never `Application/Project.getCoroutineScope()`):

```kotlin
@Service(Service.Level.PROJECT)
class MyService(private val scope: CoroutineScope) {
    fun doWork() = scope.launch { /* work */ }
}
```

**Dispatchers:**

- `Dispatchers.Default` - CPU-bound work
- `Dispatchers.IO` - File/network I/O (use narrowly, right before actual I/O)
- `Dispatchers.EDT` - UI updates (prefer over `Dispatchers.Main`)

**Documentation:**

- [Coroutine Scopes](https://plugins.jetbrains.com/docs/intellij/coroutine-scopes.html)
- [Coroutine Dispatchers](https://plugins.jetbrains.com/docs/intellij/coroutine-dispatchers.html)
- [Coroutine Read Actions](https://plugins.jetbrains.com/docs/intellij/coroutine-read-actions.html)
- [EDT and Locks](https://plugins.jetbrains.com/docs/intellij/coroutine-edt-and-locks.html)

### Disposables

Always register disposables with a parent to prevent memory leaks:

```kotlin
Disposer.register(parentDisposable, myDisposable)
```

## Testing

- **Framework:** JUnit 5 (Jupiter) + [mockito-kotlin](https://github.com/mockito/mockito-kotlin)
- **Required:** `@TestApplication` annotation for tests using IntelliJ Platform APIs
- **Naming:** Use backtick syntax for descriptive names: `` `should do something when condition`() ``
- **Organization:** Use `@Nested` inner classes to group related tests
- **Location:** `test/io/confluent/intellijplugin/`
- **Documentation:** [Testing Plugins](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)

### Mocking with mockito-kotlin

Uses `mockito-kotlin:5.4.0` with Kotlin-friendly DSL:

```kotlin
import org.mockito.kotlin.*

// Create mock with inline stubbing
val mockService = mock<UserService> {
    on { getUsername() } doReturn "testuser"
    onBlocking { fetchData() } doReturn data  // for suspend functions
}

// Verify calls
verify(mockService, times(1)).getUsername()
verify(mockService, never()).delete(any())
```

### Test Configuration

```kotlin
test {
    useJUnitPlatform()
    systemProperty("ccloud.callback-port", "26639")
}
```

### Test Fixtures

**Never use inline JSON strings in tests when it comes to mocked API endpoints.** Instead, create separate fixture files in `test/resources/` directory.

**Bad:**

```kotlin
@Test
fun `should parse response`() {
    val json = """{"id": 1, "name": "test", "config": {...}}"""
    val result = parser.parse(json)
    // ...
}
```

**Good:**

```kotlin
@Test
fun `should parse response`() {
    val json = javaClass.getResourceAsStream("/fixtures/sample-response.json")!!.readText()
    val result = parser.parse(json)
    // ...
}
```

**Benefits:**

- Improved readability and maintainability
- Easier to update test data
- Syntax highlighting and validation in JSON files
- Reduced test file clutter

### Testing UI Components (Kotlin UI DSL)

Use `UIUtil.findComponentOfType` / `UIUtil.findComponentsOfType` to traverse the built component tree:

```kotlin
// Find a plain button (excludes ActionLink which also extends JButton)
val button = UIUtil.findComponentsOfType(panel, JButton::class.java)
    .filterNot { it is ActionLink }
    .firstOrNull()

// Find a link() — UI DSL renders link() as ActionLink extends JButton
val link = UIUtil.findComponentOfType(panel, ActionLink::class.java)

// Simulate a click
button?.doClick()
link?.doClick()
```

Key facts:
- `link()` in UI DSL v2 renders as `com.intellij.ui.components.ActionLink extends JButton`
- `UIUtil.findComponentsOfType<JButton>` returns **both** plain buttons and `ActionLink`s — filter by `is ActionLink` to separate them
- `doClick()` triggers all registered action listeners synchronously

### Testing Actions

Use `TestActionEvent.createTestEvent()` with a `DataContext` lambda to provide context data, then assert on `event.presentation`:

```kotlin
val event = TestActionEvent.createTestEvent(action) { key ->
    if (key == ConnectionUtil.CONNECTION_ID.name) "ccloud" else null
}
action.update(event)
// assert on event.presentation.text / .icon / .isVisible / .isEnabled
```

### Replacing Services in Tests

Use `replaceService` to swap app- or project-scoped services with mocks. Restored automatically when the disposable is disposed:

```kotlin
@TestApplication
class MyTest {
    private val disposable = Disposer.newDisposable("MyTest")

    @BeforeEach
    fun setUp() {
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mock(), disposable)
    }

    @AfterEach
    fun tearDown() = Disposer.dispose(disposable)
}
```

## UI Development

Uses [Kotlin UI DSL v2](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html) (`com.intellij.ui.dsl.builder.*`).

**Patterns:**

- Structure: `panel { group("Title") { row("Label:") { textField() } } }`
- Visibility control: `rowsRange { ... }.visible(condition)`
- Alignment: `cell(component).align(AlignX.FILL).resizableColumn()`
- Validators: See `core/settings/ValidationUtils.kt`
- Field wrappers: `core/settings/fields/` (`StringNamedField`, `PasswordNamedField`, `ComboBoxField`)
- Localized strings: `KafkaMessagesBundle.message("dialog.key")`

## Extension Points

Custom extension point `connectionSettingProvider` defined in `plugin.xml` for pluggable connection settings.

## Code Style

- Handle exceptions gracefully with user-visible error messages
- Use `thisLogger()` for logging

## Common Mistakes to Avoid

- Blocking EDT with network calls or long computations
- Holding `Project` references in application-level services
- Forgetting to dispose resources (listeners, coroutine scopes)
