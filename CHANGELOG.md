# Change Log
All noteworthy changes to this plugin will be documented in this file.

## Unreleased
### Changed
- Consumer and producer record tables now retain up to 10,000 records per session. Previously, leaving the "Consumer records limit" field empty (or set to 0) allowed unbounded growth.
### Added
### Removed
### Fixed

## 253.25910.0
### Changed
- Rebrand plugin from "Kafka" to "Confluent" and publish under Apache 2 license

### Added
- OAuth support for logging in to Confluent Cloud directly, with the ability to view & interact with environments and resources.

### Removed

### Fixed
- Consumer performance improvements: batch table updates and optimized storage