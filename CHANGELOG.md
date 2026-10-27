# Change Log
All noteworthy changes to this plugin will be documented in this file.

## Unreleased
### Changed
- Consumer and producer record tables now retain up to 50,000 records per session. Previously, leaving the "Consumer records limit" field empty (or set to 0) allowed unbounded growth.
- Confluent Cloud requests are now retried with exponential backoff and jitter, honoring `Retry-After`: on rate limiting (429) for any request, and on temporary server errors (502/503/504) and connection failures for requests that are safe to repeat.
### Added
- MessagePack serialization support for consumer and producer records
### Removed
### Fixed
- The Confluent Cloud consumer no longer retries server errors forever, or keeps polling after you sign out. It now retries network errors and timeouts, and stops with an error after 10 consecutive failures.

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