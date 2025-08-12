# Kafka Plugin for JetBrains IDEs

### Overview

The [Kafka plugin](https://plugins.jetbrains.com/plugin/21704-kafka/) is designed to help developers work
with [Apache Kafka](https://kafka.apache.org/) directly from an IntelliJ-based IDE. It provides a comprehensive set of tools for monitoring
and managing Kafka event streaming processes.

Key features include:

- Connect to Kafka clusters with support for various authentication methods
- Produce and consume messages in different formats (JSON, Avro, Protobuf, etc.)
- Manage topics and monitor consumer groups
- Integrate with schema registries (Confluent Schema Registry and AWS Glue Schema Registry)
- Support for SSH tunneling and SSL connections
- Spring Boot integration for Kafka connections

The plugin provides a dedicated tool window with an intuitive UI for interacting with Kafka clusters, making it easier to develop and test
Kafka-based applications.

### Installing and Getting Started

To start using the Kafka plugin for IntelliJ IDEA, check out
the [plugin documentation](https://www.jetbrains.com/help/idea/big-data-tools-kafka.html).
It explains how to install the plugin and begin working with it.

### Reporting Issues

If you find a bug or something doesn’t work as expected, please report it
in [bug tracker](https://youtrack.jetbrains.com/issues/IJPL?q=Subsystem:%20%7BTools.%20Kafka%7D&u=1).

## Developing the Plugin

### Prerequisites

- JDK 21 or later
- Gradle 8.5 or later

#### Dependencies

The plugin depends on several libraries:

- Apache Kafka Clients (4.0.0)
- Confluent Schema Registry Client (7.2.0)
- AWS SDK components for Glue and SSO integration
- Various serializers for Avro, JSON, and Protobuf Schemas

### Building the Plugin

1. Clone the repository and navigate to the root of the plugin directory:
2. Build the plugin using Gradle:
   ```bash
   ./gradlew build
   ```

### Running in Development Mode

To run the plugin in development mode:

1. Execute the Gradle task:
   ```bash
   ./gradlew runIde
   ```

   This will start a development instance of IntelliJ IDEA with the plugin installed.

2. Once the IDE is running, you can access the Kafka tool window from the bottom toolbar.

### Running Tests

The plugin uses JUnit for testing. To run all tests:

```bash
./gradlew test
```

#### Test Structure

Tests are located in the `test` directory and include:

- Tests for data generation for schemas
- Tests for migrating Kafka connections
- Tests for connection data serialization

### Building for Deployment

To build the plugin for deployment:

```bash
./gradlew buildPlugin
```

This creates a ZIP file in `build/distributions` that can be installed in IntelliJ IDEA `Settings -> Plugins -> Install Plugin from Disk`.

### Publishing to JetBrains Marketplace

There are two ways to publish new versions of the plugin to
the [Marketplace](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html):

- Manual upload via the plugin’s detail page on the Marketplace
- Automatic upload using Gradle tasks

To publish using Gradle:

1. [Configure](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#providing-your-personal-access-token-to-gradle) your
   `Personal Access Token` via Gradle
2. Run the publishPlugin task:
   ```bash
   ./gradlew publishPlugin
   ```

#### Additional Helpful Gradle Tasks

To explore other useful tasks, run:

```bash
./gradlew tasks
```

This will list all available tasks, including those provided by the plugin and the IntelliJ Platform

## Architecture Overview

This plugin is built using the [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/).  
For a deeper understanding of the platform’s structure, extension points, and best practices, refer to the official SDK documentation

The plugin can be roughly divided into three layers: **UI**, **KafkaDataManager**, and **API Communication**

### UI

#### Tool Windows

The plugin's main tool window is declared in the `plugin.xml` and it can be considered as an entry point:

```xml
<toolWindow id="KafkaToolWindow" anchor="bottom" canCloseContents="true"
            icon="com.intellij.bigdatatools.kafka.icons.BigdatatoolsKafkaIcons.KafkaToolWindow"
            factoryClass="com.jetbrains.bigdatatools.kafka.toolwindow.KafkaToolWindowFactory"/>
```

`KafkaToolWindowFactory` creates `KafkaMonitoringToolWindowController` which setup `KafkaMainController`.

**KafkaMainController**

Displays the list of `Topics`, `Schema Registry`, and `Consumer Groups` as a tree `(myTree: ProjectViewTree)` on the left side of the tool
window panel.

When a node in the tree is selected, `KafkaMainController` displays detailed information in a **side panel** to the right of the tree.
These views are rendered by a set of dedicated sub-controllers:

- `TopicsController` – shows a list of topics.
- `TopicDetailsController` – shows topic-specific information.
- `ConsumerGroupsController` – shows available consumer groups.
- `ConsumerGroupOffsetsController` – shows offset details for a selected consumer group.
- `KafkaRegistryController` and `KafkaSchemaController` – handle schema registry data, if available.

The controller also manages a context-sensitive **toolbar**, which updates based on the currently selected node in the tree:

- If a topic is selected, the following actions become available: `Create Topic`, `Delete Topic`, `Clear Topic` and `Add to Favourites`
- If a schema is selected, the toolbar shows: `Create Schema`, `Delete Schema`, `Clone Schema` and `Add to Favourites`

#### Editor

Kafka `Producer` and `Consumer` editors are registered in `plugin.xml`:

```xml
<fileEditorProvider implementation="com.jetbrains.bigdatatools.kafka.common.editor.KafkaEditorProvider"/>
```

`KafkaEditorProvider` is responsible for creating editor tabs when actions ⚙️Producer and ⚙️Consumer are triggered.
It opens a custom UI panel in the IntelliJ editor area, depending on the editor type:

- `KafkaConsumerEditor` – provides an interface for consuming messages from Kafka topics.
- `KafkaProducerEditor` – provides an interface for producing messages to Kafka topics.

#### Actions

Several key actions in the Kafka plugin are defined in `plugin.xml` and grouped by context:

```xml
<actions resource-bundle="messages.KafkaBundle">
  <!-- Action groups for different contexts -->
  <group id="Kafka.Actions">
    <!-- General actions like create producer/consumer -->
    <group id="Kafka.General.Actions">...</group>

    <!-- Add to favorites action -->
    <action id="Kafka.AddToFavoriteAction"
            class="com.jetbrains.bigdatatools.kafka.toolwindow.actions.AddToFavoriteAction"
            icon="AllIcons.Nodes.Favorite">
    </action>

    <!-- Topic-specific actions -->
    <group id="Kafka.Topic.Actions">...</group>

    <!-- Consumer group actions -->
    <group id="Kafka.Consumer.Group.Actions">...</group>

    <!-- Schema-related actions -->
    <group id="Kafka.Schema.Actions">...</group>
  </group>
</actions>
```

[Actions](https://plugins.jetbrains.com/docs/intellij/action-system.html) use the `com.intellij.openapi.actionSystem.DataContext` mechanism
to access data from the current context. DataKeys and extension properties are declared in the `MainTreeController` to simplify access:

```kotlin
// Companion object of MainTreeController 
val DATA_MANAGER: DataKey<MonitoringDataManager> = DataKey.create("kafka.data.manager")
// ...  
val AnActionEvent.dataManager
  get() = dataContext.getData(DATA_MANAGER)
```

### KafkaDataManager

The `KafkaDataManager` class serves as a middle layer between the UI and API Communication `(KafkaClient)`.  
It acts as a container for various data classes and provides methods for managing Kafka-related data operations:

- Caching and managing Kafka data (topics, consumer groups, schemas)
- Providing access to data models for UI components
- Handling data operations like creating/deleting topics and schemas

The data classes used by KafkaDataManager are primarily stored in the `com.jetbrains.bigdatatools.kafka.model` package, which includes
classes like: `TopicPresentable`, `ConsumerGroupPresentable`, `TopicConfig` etc.

### API Communication

The `KafkaClient` class is responsible for all communication between the plugin and Kafka APIs.
It performs HTTPS requests and deserializes the responses into data classes that are described in the KafkaDataManager section.

Key responsibilities of KafkaClient include:

- Establishing and maintaining connections to Kafka brokers
- Retrieving information about topics, partitions, and consumer groups
- Managing topic configurations and offsets
- Interacting with schema registries (Confluent or AWS Glue)
- Handling authentication and security configurations

The client uses the official Apache Kafka client libraries. It handles connection errors, retries, and proper resource disposal.

### Authentication Mechanisms

The plugin supports various authentication methods:

- Username/Password (SASL/PLAIN)
- SSL/TLS certificates
- AWS IAM authentication for MSK
- SSH tunneling for secure connections

### Spring Boot Integration

When the Spring Boot plugin is installed, the Kafka plugin
[can connect](https://www.jetbrains.com/help/idea/big-data-tools-kafka.html#connect_from_spring) to a Kafka cluster (or reuse an
existing connection) using configuration properties from your spring application.

To connect:

1. Open your `application.properties` or `application.yml` file with at least the `bootstrap-servers` property defined.
2. In the gutter, click the Kafka icon and select **Create Kafka connection**.

This integration is possible because the plugin declares an optional dependency on the Spring Boot plugin:

```xml
<depends config-file="spring-boot.xml" optional="true">com.intellij.spring.boot</depends>
```

If the Spring Boot plugin isn’t installed or enabled, the Kafka plugin will still work normally,
but Spring-specific conveniences will be unavailable.

### Key Packages

- `com.jetbrains.bigdatatools.kafka.core`
  - `core.connection` handles connection management, SSH tunneling, proxy settings, and connection exceptions
  - `core.monitoring` contains the custom monitoring classes for Kafka items, including tool windows and UI controllers.
    Implements the data visualization and interaction components
  - `core.rfs` Implements the Remote File System abstraction for Kafka resources, allowing them to be represented in the BDT panel view and
    navigation tree
  - `core.serializer` includes helper functions for serializing and deserializing data between JSON and Kotlin objects with a Moshi library
  - `core.setting` manages plugin settings, preferences, and persistent state classes
  - `core.ui` contains reusable custom UI components


- `com.jetbrains.bigdatatools.kafka.client`
  - Handles communication with Kafka brokers
  - Manages connections and authentication
  - Provides methods for topic management and message handling


- `com.jetbrains.bigdatatools.kafka.consumer`
  - Implements Kafka consumer functionality
  - Handles message deserialization
  - Provides UI for viewing consumed messages


- `com.jetbrains.bigdatatools.kafka.producer`
  - Implements Kafka producer functionality
  - Handles message serialization
  - Provides UI for creating and sending messages


- `com.jetbrains.bigdatatools.kafka.registry`
  - Integrates with schema registries (Confluent and AWS Glue)
  - Manages schema versions and compatibility
  - Provides schema validation


- `com.jetbrains.bigdatatools.kafka.aws`
  - Handles AWS authentication and credentials
  - Integrates with AWS Glue Schema Registry
  - Supports AWS MSK (Managed Streaming for Kafka)
