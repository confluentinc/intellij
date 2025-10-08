# Kafka Plugin for JetBrains IDEs

### Overview

The [Kafka plugin](https://plugins.jetbrains.com/plugin/21704-kafka/) is designed to help developers work
with [Apache Kafka](https://kafka.apache.org/) directly from an IntelliJ-based IDE. It provides a comprehensive set of
tools for monitoring
and managing Kafka event streaming processes.

Key features include:

- Connect to Kafka clusters with support for various authentication methods
- Produce and consume messages in different formats (JSON, Avro, Protobuf, etc.)
- Manage topics and monitor consumer groups
- Integrate with schema registries (Confluent Schema Registry and AWS Glue Schema Registry)
- Support for SSH tunneling and SSL connections
- Spring Boot integration for Kafka connections

The plugin provides a dedicated tool window with an intuitive UI for interacting with Kafka clusters, making it easier
to develop and test
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

You can install the prerequisites through [SDKMAN!](https://sdkman.io/) by running `sdk env install` in the root
directory of this project.
You can find instructions on how to install SDKMAN! in the [SDKMAN! docs](https://sdkman.io/install).

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

This creates a ZIP file in `build/distributions` that can be installed in IntelliJ IDEA
`Settings -> Plugins -> Install Plugin from Disk`.

### Publishing to JetBrains Marketplace

There are two ways to publish new versions of the plugin to
the [Marketplace](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html):

- Manual upload via the plugin’s detail page on the Marketplace
- Automatic upload using Gradle tasks

To publish using Gradle:

1. [Configure](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#providing-your-personal-access-token-to-gradle)
   your
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
