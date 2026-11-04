# Change Log
All noteworthy changes to this plugin will be documented in this file.

## Unreleased
### Changed
- Consumer and producer record tables now retain up to 50,000 records per session. Previously, leaving the "Consumer records limit" field empty (or set to 0) allowed unbounded growth.
- Schema updates now time out after 3 minutes instead of waiting indefinitely; Cancel stops waiting for a pending update (the registry may still apply it), and the limit can be changed with the Registry key `bdide.kafka.schema.registry.call.timeout.ms`.
- Tool window auto-refresh now varies each interval by up to ±20%. While a connection is unreachable, auto-refresh keeps re-checking it, waiting progressively longer (up to about 5 minutes), and resumes the normal interval once the connection recovers.
### Added
- MessagePack serialization support for consumer and producer records
### Removed
### Fixed
- Deleting a consumer group that the broker rejects (for example, one that still has active members) now shows an error instead of appearing to succeed.
- The Topics table no longer shows "Create a topic" mixed into the connection error message when an auto-refresh fails.

## 253.25910.2
### Fixed
- Plugin failed to load with a `NoClassDefFoundError` in IDEs without the Remote/SSH module, such as WebStorm (https://github.com/confluentinc/intellij/issues/628)
- Key & Value fields in the Consumer details panel stayed cramped for large values on tall displays (https://github.com/confluentinc/intellij/issues/588)

## 253.25910.1
### Changed
- Adjust error reporting filter to remove non-Confluent errors

### Fixed
- Value & Key fields in Consumer details panel too small to read (https://github.com/confluentinc/intellij/issues/601)
- Viewing schemas that reference other schemas throws an error (https://github.com/confluentinc/intellij/issues/591)

## 253.25910.0
### Changed
- Rebrand plugin from "Kafka" to "Confluent" and publish under Apache 2 license

### Added
- OAuth support for logging in to Confluent Cloud directly, with the ability to view & interact with environments and resources.

### Removed

### Fixed
- Consumer performance improvements: batch table updates and optimized storage