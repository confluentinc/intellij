## Architecture Overview

This plugin is built using the [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/).  
For a deeper understanding of the platform’s structure, extension points, and best practices, refer to the official SDK
documentation

The plugin can be roughly divided into three layers: **UI**, **KafkaDataManager**, and **API Communication**

### UI

#### Tool Windows

The plugin's main tool window is declared in the `plugin.xml` and it can be considered as an entry point:

```xml
<toolWindow id="KafkaToolWindow" anchor="bottom" canCloseContents="true"
            icon="io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons.KafkaToolWindow"
            factoryClass="io.confluent.intellijplugin.toolwindow.KafkaToolWindowFactory"/>
```

`KafkaToolWindowFactory` creates `KafkaMonitoringToolWindowController` which setup `KafkaMainController`.

**KafkaMainController**

Displays the list of `Topics`, `Schema Registry`, and `Consumer Groups` as a tree `(myTree: ProjectViewTree)` on the
left side of the tool
window panel.

When a node in the tree is selected, `KafkaMainController` displays detailed information in a **side panel** to the
right of the tree.
These views are rendered by a set of dedicated sub-controllers:

- `TopicsController` – shows a list of topics.
- `TopicDetailsController` – shows topic-specific information.
- `ConsumerGroupsController` – shows available consumer groups.
- `ConsumerGroupOffsetsController` – shows offset details for a selected consumer group.
- `KafkaRegistryController` and `KafkaSchemaController` – handle schema registry data, if available.

The controller also manages a context-sensitive **toolbar**, which updates based on the currently selected node in the
tree:

- If a topic is selected, the following actions become available: `Create Topic`, `Delete Topic`, `Clear Topic` and
  `Add to Favourites`
- If a schema is selected, the toolbar shows: `Create Schema`, `Delete Schema`, `Clone Schema` and `Add to Favourites`

#### Editor

Kafka `Producer` and `Consumer` editors are registered in `plugin.xml`:

```xml
<fileEditorProvider implementation="io.confluent.intellijplugin.common.editor.KafkaEditorProvider"/>
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
            class="io.confluent.intellijplugin.toolwindow.actions.AddToFavoriteAction"
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

[Actions](https://plugins.jetbrains.com/docs/intellij/action-system.html) use the
`com.intellij.openapi.actionSystem.DataContext` mechanism
to access data from the current context. DataKeys and extension properties are declared in the `MainTreeController` to
simplify access:

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

The data classes used by KafkaDataManager are primarily stored in the `io.confluent.intellijplugin.model` package, which
includes
classes like: `TopicPresentable`, `ConsumerGroupPresentable`, `TopicConfig` etc.

### API Communication

The `KafkaClient` class is responsible for all communication between the plugin and Kafka APIs.
It performs HTTPS requests and deserializes the responses into data classes that are described in the KafkaDataManager
section.

Key responsibilities of KafkaClient include:

- Establishing and maintaining connections to Kafka brokers
- Retrieving information about topics, partitions, and consumer groups
- Managing topic configurations and offsets
- Interacting with schema registries (Confluent or AWS Glue)
- Handling authentication and security configurations

The client uses the official Apache Kafka client libraries. It handles connection errors, retries, and proper resource
disposal.

### Authentication Mechanisms

The plugin supports various authentication methods:

- Username/Password (SASL/PLAIN)
- SSL/TLS certificates
- AWS IAM authentication for MSK
- SSH tunneling for secure connections

### Spring Boot Integration

When the Spring Boot plugin is installed, the Kafka plugin
[can connect](https://www.jetbrains.com/help/idea/big-data-tools-kafka.html#connect_from_spring) to a Kafka cluster (or
reuse an
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

- `io.confluent.intellijplugin.core`
    - `core.connection` handles connection management, SSH tunneling, proxy settings, and connection exceptions
    - `core.monitoring` contains the custom monitoring classes for Kafka items, including tool windows and UI
      controllers.
      Implements the data visualization and interaction components
    - `core.rfs` Implements the Remote File System abstraction for Kafka resources, allowing them to be represented in
      the BDT panel view and
      navigation tree
    - `core.serializer` includes helper functions for serializing and deserializing data between JSON and Kotlin objects
      with a Moshi library
    - `core.setting` manages plugin settings, preferences, and persistent state classes
    - `core.ui` contains reusable custom UI components


- `io.confluent.intellijplugin.client`
    - Handles communication with Kafka brokers
    - Manages connections and authentication
    - Provides methods for topic management and message handling


- `io.confluent.intellijplugin.consumer`
    - Implements Kafka consumer functionality
    - Handles message deserialization
    - Provides UI for viewing consumed messages


- `io.confluent.intellijplugin.producer`
    - Implements Kafka producer functionality
    - Handles message serialization
    - Provides UI for creating and sending messages


- `io.confluent.intellijplugin.registry`
    - Integrates with schema registries (Confluent and AWS Glue)
    - Manages schema versions and compatibility
    - Provides schema validation


- `io.confluent.intellijplugin.aws`
    - Handles AWS authentication and credentials
    - Integrates with AWS Glue Schema Registry
    - Supports AWS MSK (Managed Streaming for Kafka)