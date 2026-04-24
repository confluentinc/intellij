---
name: intellij-unit-tests
description: |
  Guide writing unit tests for the Kafka IntelliJ plugin. Use when the user asks to
  "write tests", "add unit tests", "test this component", or needs help testing
  services, actions, UI panels, or data models in isolation. Also triggers on
  "how do I test", "mock service", "test action", or "test UI panel".
user-invocable: false
allowed-tools:
  - Read
  - Grep
  - Glob
  - WebFetch
  - WebSearch
  - Bash
---

# Unit Test Guide

Guide the creation of unit tests for the Kafka IntelliJ plugin following project conventions
and IntelliJ Platform best practices.

## Sources

### 1. Project Test Conventions (from CLAUDE.md)

The project uses JUnit 5 + mockito-kotlin with `@TestApplication` for IntelliJ API tests.
See CLAUDE.md Testing section for base conventions (naming, organization, fixtures).

### 2. Existing Tests

Existing unit tests in `test/io/confluent/intellijplugin/` demonstrate project conventions.
Always check for nearby tests before writing new ones — follow the established patterns.

## When to Write Unit Tests vs Integration Tests

**Rule of thumb:** If you need to mock a service to control the test scenario, use a unit test.
Integration tests verify that components are wired together correctly in the real IDE — they
test the golden path without mocks.

| Scenario                                    | Test Type        | Why                                                    |
| ------------------------------------------- | ---------------- | ------------------------------------------------------ |
| Service logic in isolation                  | Unit test        | Mock dependencies, test branches                       |
| Kafka client wrapper behavior               | Unit test        | Mock Kafka client, test error paths                    |
| Action update/presentation logic            | Unit test        | Use `TestActionEvent`, assert on presentation          |
| Data model serialization/parsing            | Unit test        | Pure logic, no platform needed                         |
| UI panel builds correct components          | Unit test        | Use `UIUtil` to check component tree in-memory         |
| UI panel responds to state changes          | Unit test        | Mock services, verify cards/visibility switch           |
| Action context with simple data (tree, ID)  | Unit test        | Mock `DataContext`, test `update()` logic              |
| Settings panel field validation             | Unit test        | Build panel, check validators                          |
| Action context with IDE state (editors, VFS)| Integration test | Needs real `FileEditorManager`, open files, etc.       |
| Tool window appears in real IDE             | Integration test | Verifies plugin.xml wiring, factory registration       |
| Plugin loads without errors                 | Integration test | Full IDE startup, no mocks                             |
| End-to-end connection flow                  | Integration test | Real network + UI interaction                          |
| UI responds to live Kafka events            | Integration test | Requires real event pipeline                           |

## Process

### 1. Identify What to Test

Before writing tests, understand the class under test:

```bash
# Find the source file
find src -name "MyClass.kt" -type f

# Check if tests already exist
find test -name "MyClassTest.kt" -type f

# Look at nearby tests for conventions
ls test/io/confluent/intellijplugin/<module>/
```

### 2. Set Up Test Class

Every test class follows this structure:

```kotlin
package io.confluent.intellijplugin.<module>

import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.*
import org.mockito.kotlin.*

@TestApplication
class MyServiceTest {

    @Nested
    inner class `when doing something` {

        @Test
        fun `should return expected result`() {
            // arrange / act / assert
        }
    }
}
```

**When to use `@TestApplication`:** Any test that touches IntelliJ Platform APIs (services,
actions, UI components, message bus, etc.). Omit it only for pure data/logic tests with no
platform dependencies.

### 3. Disposable Lifecycle

When tests need to register/unregister platform resources, use a disposable:

```kotlin
@TestApplication
class MyServiceTest {
    private lateinit var parentDisposable: Disposable

    @BeforeEach
    fun setUp() {
        parentDisposable = Disposer.newDisposable("test")
    }

    @AfterEach
    fun tearDown() {
        Disposer.dispose(parentDisposable)
    }
}
```

This pattern is established in the codebase (see `CCloudAuthServiceTest`, `CCloudTokenRefreshBeanTest`).

### 4. Testing Patterns

#### Testing Actions

Use `TestActionEvent.createTestEvent()` to test action update/presentation logic:

```kotlin
// Established pattern (used in ActionTelemetryListenerTest):
val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
action.update(event)
// Assert on event.presentation
assertThat(event.presentation.isEnabled).isTrue()
assertThat(event.presentation.isVisible).isTrue()
```

To provide custom context data, pass a `DataContext` implementation:

```kotlin
val event = TestActionEvent.createTestEvent(action) { key ->
    when (key) {
        ConnectionUtil.CONNECTION_ID.name -> "my-connection-id"
        else -> null
    }
}
action.update(event)
```

#### Replacing Services in Tests

Use `replaceService` to swap app- or project-scoped services with mocks.
Automatically restored when the disposable is disposed:

```kotlin
@TestApplication
class MyFeatureTest {
    private lateinit var parentDisposable: Disposable
    private val mockAuth: CCloudAuthService = mock()

    @BeforeEach
    fun setUp() {
        parentDisposable = Disposer.newDisposable("test")
        ApplicationManager.getApplication()
            .replaceService(CCloudAuthService::class.java, mockAuth, parentDisposable)
    }

    @AfterEach
    fun tearDown() = Disposer.dispose(parentDisposable)
}
```

**Note:** This pattern is not yet widely used in the codebase but is the recommended
IntelliJ Platform approach for service isolation in tests.

#### Testing UI Components (Kotlin UI DSL)

Use `UIUtil.findComponentOfType` / `UIUtil.findComponentsOfType` to traverse the built
component tree after calling a panel builder:

```kotlin
// Build the panel under test
val panel = myPanelBuilder.createPanel()

// Find a plain button (excludes ActionLink which also extends JButton)
val button = UIUtil.findComponentsOfType(panel, JButton::class.java)
    .filterNot { it is ActionLink }
    .firstOrNull()

// Find a link() — UI DSL renders link() as ActionLink (extends JButton)
val link = UIUtil.findComponentOfType(panel, ActionLink::class.java)

// Simulate a click
button?.doClick()
link?.doClick()
```

Key facts:
- `link()` in UI DSL v2 renders as `com.intellij.ui.components.ActionLink` which extends `JButton`
- `UIUtil.findComponentsOfType<JButton>` returns **both** plain buttons and `ActionLink`s —
  filter by `is ActionLink` to separate them
- `doClick()` triggers all registered action listeners synchronously
- The production code uses `ActionLink` directly in several places (see `KafkaConsumerPanel`,
  `TestConnectionPanelWrapper`, `RfsEditorErrorPanel`)

#### Mocking with mockito-kotlin

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

// Argument captors
val captor = argumentCaptor<String>()
verify(mockService).save(captor.capture())
assertThat(captor.firstValue).isEqualTo("expected")
```

#### Test Fixtures

**Never use inline JSON strings for mocked API responses.** Use fixture files:

```kotlin
// Load from test/resources/fixtures/
val json = javaClass.getResourceAsStream("/fixtures/sample-response.json")!!.readText()

// Or use the project's ResourceLoader utility:
val json = ResourceLoader.loadResource("ccloud-resources-mock-responses/list-clusters.json")
```

Fixture files go in `test/resources/` (or subdirectories like `test/resources/fixtures/`,
`test/resources/ccloud-resources-mock-responses/`).

### 5. Run Tests

```bash
# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "*.MyServiceTest"

# Run a single test method
./gradlew test --tests "*.MyServiceTest.should return expected result"
```

## Project Conventions

| Convention               | Rule                                                                  |
| ------------------------ | --------------------------------------------------------------------- |
| **Source root**           | `test/io/confluent/intellijplugin/`                                   |
| **Test naming**          | Backtick syntax: `` `should do X when Y`() ``                        |
| **Grouping**             | Use `@Nested` inner classes for related scenarios                     |
| **Platform annotation**  | `@TestApplication` for any test using IntelliJ APIs                   |
| **Fixtures**             | Store test data in `test/resources/` — never inline JSON              |
| **Mocking framework**    | `mockito-kotlin:5.4.0`                                               |
| **Disposables**          | Create in `@BeforeEach`, dispose in `@AfterEach`                      |

## Tips

- Check nearby test files before writing — follow the patterns already established in that module.
- Pure data classes and utility functions often don't need `@TestApplication`.
- When testing coroutine-based services, use `runTest` from `kotlinx-coroutines-test`.
- Prefer `assertThat` (AssertJ) or JUnit 5 `Assertions` — be consistent with nearby tests.
- If a test needs a `Project` instance, consider whether you really need it or can mock it.
