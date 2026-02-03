# Kafka Plugin for IntelliJ - AI Coding Agent Guide

## Project Overview

This is an IntelliJ IDEA plugin for Apache Kafka, providing a comprehensive UI for connecting to Kafka clusters,
producing/consuming messages, managing topics, and integrating with schema registries (Confluent and AWS Glue). Built
with the IntelliJ Platform Plugin SDK using Kotlin.

**Core Architecture**: Three-layer design (UI → KafkaDataManager → API Communication via KafkaClient)

## Essential Build & Development Commands

### Setup (Required First Step)

```bash
# Install SDKMAN and JDK 21 (defined in .sdkmanrc)
sdk env install
```

### Development Workflow

```bash
# Build the plugin
./gradlew build

# Run plugin in development IDE instance
./gradlew runIde

# Run tests (JUnit 5)
./gradlew test

# Build distributable ZIP
./gradlew buildPlugin  # Output: build/distributions/
```

Run `./gradlew clean` to remove dependencies from prior builds before running `./gradlew build`, when you've updated the build tooling, after larger refactors, or when old changes keep lingering around longer than expected.

### Secrets for Telemetry (Sentry/Segment)

**For production builds only** - local development does not require telemetry setup.

Required environment variables: `SENTRY_AUTH_TOKEN`, `SENTRY_DSN`, `SEGMENT_WRITE_KEY`

```bash
# From Vault (Confluent internal - only needed for production builds)
vault_login
. scripts/get-secrets.sh
```

Without these environment variables, telemetry tasks are automatically disabled but builds should still work fine for
local development.

### CI/Build Cache

Uses Confluent's `cc-mk-include` system. Makefile auto-downloads required mk files from GitHub.

## Architecture Patterns

### Plugin Entry Points

1. **Tool Window**: `KafkaToolWindowFactory` → `KafkaMonitoringToolWindowController` → `KafkaMainController`
2. **Editors**: `KafkaEditorProvider` creates `KafkaProducerEditor` and `KafkaConsumerEditor`
3. **Actions**: Defined in `plugin.xml` under `<actions>` groups (`Kafka.Topic.Actions`, `Kafka.Schema.Actions`, etc.)

### Controller Pattern

Controllers manage UI components and coordinate with `KafkaDataManager`:

- `KafkaMainController` - Main tree view and navigation
- `TopicsController`, `TopicDetailsController` - Topic management
- `ConsumerGroupsController`, `ConsumerGroupOffsetsController` - Consumer groups
- `KafkaRegistryController`, `KafkaSchemaController` - Schema registry

All controllers extend base types and follow `Disposable` pattern (register with `Disposer.register(parent, child)`).

### Data Context Mechanism

Actions access data via `DataKey` extension properties:

```kotlin
// In controller companion object
val DATA_MANAGER: DataKey<MonitoringDataManager> = DataKey.create("kafka.data.manager")

// Extension property for easy access
val AnActionEvent.dataManager
get() = dataContext.getData(DATA_MANAGER)
```

### Data Models

Domain objects use `*Presentable` suffix (e.g., `TopicPresentable`, `ConsumerGroupPresentable`):

- Located in `io.confluent.intellijplugin.model`
- Annotated with rendering hints: `@NoRendering`, `@LoadingRendering`
- Include companion object with `renderableColumns` for table display
- Define localization keys referencing `KafkaBundle.properties`

### Localization

All user-facing strings use `KafkaMessagesBundle.message("key")` with keys in
`resources/messages/KafkaBundle.properties`.
Use `@Nls` annotation for localized strings, `@NlsSafe` for non-localized (e.g., technical identifiers).

## Key Package Structure

```
src/io/confluent/intellijplugin/
├── actions/         # Plugin actions and action handlers
├── aws/             # AWS authentication and MSK support
├── client/          # Kafka broker communication, authentication
├── common/          # Common utilities and shared code
├── completion/      # Code completion support
├── consumer/        # Consumer functionality and UI
├── core/            # Shared infrastructure
│   ├── connection/  # Connection management, SSH tunneling, proxies
│   ├── constants/   # Constant values and definitions
│   ├── data/        # Data management utilities
│   ├── delegate/    # Kotlin delegates and property delegation
│   ├── filestorages/# File-based storage implementations
│   ├── monitoring/  # Monitoring UI and data visualization
│   ├── rfs/         # Remote File System abstraction for Kafka resources
│   ├── serializer/  # Moshi-based JSON serialization utilities
│   ├── settings/    # Plugin settings and preferences
│   ├── table/       # Table UI components and utilities
│   ├── ui/          # Reusable UI components
│   └── util/        # Core utility classes
├── data/            # Data providers and data-related utilities
├── model/           # Data classes (*Presentable types)
├── producer/        # Producer functionality and UI
├── registry/        # Schema registry integration (Confluent + AWS Glue)
│   ├── confluent/   # Confluent Schema Registry specific
│   └── glue/        # AWS Glue SR specific
├── rfs/             # Remote File System implementations (top-level)
├── settings/        # Plugin settings UI and configuration
├── spring/          # Spring Boot integration (optional dependency)
├── telemetry/       # Sentry and Segment integration
├── toolwindow/      # Main UI components and controllers
├── updater/         # Plugin update functionality
└── util/            # General utility classes and helper functions
```

## Code Generation & Build-Time Tasks

Build generates configuration files from environment variables:

- `SentryConfig.kt` - Embeds `SENTRY_DSN` at compile time
- `SegmentConfig.kt` - Embeds `SEGMENT_WRITE_KEY` at compile time

Generated sources go to `build/generated/sources/{sentryconfig,segmentconfig}/kotlin` and are included in
`compileKotlin`.

## Testing Conventions

- **Framework**: JUnit 5 (with JUnit 4 runtime for platform compatibility)
- **Test Location**: `test/io/confluent/intellijplugin/`
- **Platform Tests**: Use `@TestApplication` annotation for IntelliJ platform context
- **Mocking**: `mockito-kotlin` for mock objects

Example test structure:

```kotlin
@TestApplication
class MyFeatureTest {
    @Test
    fun `descriptive test name in backticks`() {
        // Arrange, Act, Assert
    }
}
```

## Spring Boot Integration

Optional dependency (declared in `plugin.xml`):

```xml

<depends config-file="spring-boot.xml" optional="true">com.intellij.spring.boot</depends>
```

When Spring Boot plugin is present, adds:

- Gutter icons in `application.properties`/`application.yml` for Kafka configuration
- Ability to create connections from Spring config files
- Line markers via `KafkaSpringBootConfigLineMarkers`

## Configuration & Settings

- **Plugin settings**: `plugin.xml` (declarations, extensions, actions)
- **Gradle config**: Version catalog in `gradle/libs.versions.toml`
- **JVM target**: Java 21 (defined in `build.gradle.kts`)
- **Kotlin compiler args**: `-Xjvm-default=all`
- **IntelliJ version**: Targets `2025.2`
- **Dependency management**: Kafka serializers explicitly exclude `kafka-clients` to avoid version conflicts; SLF4J is excluded globally

## Telemetry & Privacy

Plugin collects anonymous usage statistics (opt-out in Settings → Tools → Kafka):

- Action usage (create/delete topics, produce/consume, etc.)
- Connection events (type, auth method, success/failure)
- No PII, message content, or connection credentials
- Implementation: `src/io/confluent/intellijplugin/telemetry/`
- See `docs/telemetry.md` for full details

## Documentation References

- Architecture deep-dive: `docs/architecture-overview.md`
- User manual: `docs/kafka-plugin-manual.md`
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/)
- [Plugin Marketplace](https://plugins.jetbrains.com/plugin/21704-kafka/)

## Plugin File Conventions

- **Icons**: `resources/icons/` with mapping in `KafkaIconMappings.json`
- **Resources**: `resources/` (not `src/main/resources`)
- **Generated code**: `gen/` for auto-generated sources (e.g., from grammar definitions)

## When Adding New Features

1. **UI Actions**: Add to `plugin.xml` under appropriate `<group id="Kafka.*">`
2. **Data models**: Create `*Presentable` classes in `model/` with `renderableColumns`
3. **Localization**: Add keys to `KafkaBundle.properties`, use `@Nls` annotations
4. **Controllers**: Extend base controller types, implement `Disposable`, register with parent
5. **Data context**: Define `DataKey` constants for action data access
6. **Tests**: Create JUnit 5 tests with `@TestApplication` for platform features

## Pull Request Reviews

When reviewing pull requests, verify changes follow IntelliJ Platform best practices. Reference the
[IntelliJ Platform SDK documentation](https://plugins.jetbrains.com/docs/intellij/) for authoritative guidance.

### Key Review Checkpoints

**Threading Model** ([docs](https://plugins.jetbrains.com/docs/intellij/threading-model.html)):
- Write actions must run on EDT only (`WriteAction.run()` / `WriteAction.compute()`)
- Read actions from background threads use `ReadAction.compute()` or `ReadAction.nonBlocking()`
- Long operations must not block EDT - use background tasks or coroutines
- PSI/VFS modifications never from UI renderers or `SwingUtilities.invokeLater()`

**IDE Infrastructure** ([docs](https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html)):
- Use `Logger.getInstance()` for logging, not log4j directly
- Check `Disposable` cleanup - register children with `Disposer.register(parent, child)`
- Network requests use `HttpConfigurable` for proxy support

**Services & Extensions**:
- Services registered in `plugin.xml` with appropriate scope (application/project)
- Extensions use correct extension points
- No static state that persists across projects

**Localization**:
- All user-facing strings use `KafkaMessagesBundle.message("key")`
- New strings added to `KafkaBundle.properties`
- Use `@Nls` / `@NlsSafe` annotations appropriately

### Auto-Generated Files (Skip in Reviews)

The following files are automatically generated and should not be reviewed or modified manually:

- `THIRD_PARTY_NOTICES.txt` - Auto-generated license notices from dependencies
- `build/generated/` - Build-time generated sources (SentryConfig.kt, SegmentConfig.kt)
- `gen/` - Auto-generated sources from grammar definitions
- `mk-files/` - Auto-downloaded Makefile includes from `cc-mk-include`
- `.mk-include-timestamp` - Build system timestamp file
- `*.iml` files - IntelliJ module files (auto-managed by IDE)
- `gradle/wrapper/` - Gradle wrapper files (managed by Gradle)
