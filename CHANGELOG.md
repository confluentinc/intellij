# Change Log
All noteworthy changes to this plugin will be documented in this file.

## Unreleased
### Changed
- Consumer and producer record tables now retain up to 10,000 records per session. Previously, leaving the "Consumer records limit" field empty (or set to 0) allowed unbounded growth.
- Free-text search in the consumer/producer record table is computed on a background thread; per-row visibility during repaint is now a constant-time bitset lookup instead of a per-cell scan, eliminating typing lag on large tables.
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