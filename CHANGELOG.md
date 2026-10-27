# Change Log
All noteworthy changes to this plugin will be documented in this file.

## Unreleased
### Changed
- Consumer and producer record tables now retain up to 50,000 records per session. Previously, leaving the "Consumer records limit" field empty (or set to 0) allowed unbounded growth.
- Schema updates now time out after 3 minutes instead of waiting indefinitely; Cancel stops waiting for a pending update (the registry may still apply it), and the limit can be changed with the Registry key `bdide.kafka.schema.registry.call.timeout.ms`.
- Confluent Cloud requests are now retried with exponential backoff and jitter, honoring `Retry-After`: on rate limiting (429) for any request, and on temporary server errors (502/503/504) and connection failures for read requests. If the server asks to wait more than a minute, the request fails right away instead of retrying sooner than asked.
- Tool window auto-refresh now varies each interval by up to ±20%. While a connection is unreachable, auto-refresh keeps re-checking it, waiting progressively longer (up to about 5 minutes), and resumes the normal interval once the connection recovers. Connections that use an SSH tunnel are not re-checked automatically; use Refresh to reconnect them.
### Added
- MessagePack serialization support for consumer and producer records
### Removed
### Fixed
- Deleting a consumer group that the broker rejects (for example, one that still has active members) now shows an error instead of appearing to succeed.
- The Topics table no longer shows "Create a topic" mixed into the connection error message when an auto-refresh fails.
- Producer send timeouts now show as error rows in the results table instead of silently ending the batch. Auto mode stops after 3 consecutive timeouts, and timed-out sends no longer count toward the request total.
- When producing from a CSV file in auto mode, a row whose send times out after reaching the producer's queue (for example, its batch expires after `delivery.timeout.ms`) is no longer sent again, since it may already have been written. Only rows that never left the client are retried.
- The producer and consumer status label now shows an error icon when an error occurs and keeps it after stopping. An error that happens before any record arrives no longer changes to "Canceled" when stopped.
- The Confluent Cloud consumer no longer retries server errors forever, or keeps polling after you sign out. It now retries network errors, timeouts, rate limiting and server errors, and stops with an error after 10 consecutive failed polls (about 2 minutes for most errors).
- Confluent Cloud sessions whose refresh token is rejected now sign out right away instead of retrying for several minutes. Temporary token refresh failures back off between tries and sign out after 10 failed attempts.
- Producer and consumer connections now report their own client IDs (`… - Producer`, `… - Consumer`) to the broker instead of the admin client's `… - Admin` ID.
- The Confluent Cloud producer now shows an error row and stops when its retries run out, instead of only showing a notification. It no longer repeats rate-limited (429) requests that were already retried.

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