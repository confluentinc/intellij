package io.confluent.intellijplugin.telemetry

import io.sentry.SentryEvent

/**
 * Filters Sentry events to only include plugin-related errors.
 * Separated from SentryClient to allow testing without triggering Sentry initialization.
 */
internal object SentryErrorFilter {

    /**
     * Checks if a Sentry event originated from Confluent code.
     * Walks the entire cause chain and suppressed exceptions to detect Confluent frames.
     *
     * @param event The Sentry event to check
     * @return true if the error is Confluent-related, false otherwise
     */
    fun isPluginRelatedError(event: SentryEvent): Boolean {
        val throwable = event.throwable ?: return false
        return containsConfluentFrame(throwable)
    }

    private fun containsConfluentFrame(throwable: Throwable): Boolean {
        val visited = mutableSetOf<Throwable>()
        val toVisit = ArrayDeque<Throwable>()
        toVisit.add(throwable)

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeFirst()

            // Avoid infinite loops from circular references
            if (!visited.add(current)) {
                continue
            }

            // Check if any stack frame belongs to Confluent package
            if (current.stackTrace.any { frame ->
                frame.className.startsWith(TelemetryUtils.CONFLUENT_PACKAGE_PREFIX)
            }) {
                return true
            }

            // Check if exception message mentions Confluent package
            if (current.message?.contains(TelemetryUtils.CONFLUENT_PACKAGE_PREFIX) == true) {
                return true
            }

            // Walk cause chain and suppressed exceptions
            current.cause?.let(toVisit::addLast)
            current.suppressed.forEach(toVisit::addLast)
        }

        return false
    }
}
