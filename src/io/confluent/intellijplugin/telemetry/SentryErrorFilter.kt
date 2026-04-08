package io.confluent.intellijplugin.telemetry

import io.sentry.SentryEvent

/**
 * Filters Sentry events to only include plugin-related errors.
 * Separated from SentryClient to allow testing without triggering Sentry initialization.
 */
internal object SentryErrorFilter {

    /**
     * Checks if a Sentry event originated from plugin code.
     * Walks the entire cause chain and suppressed exceptions to detect plugin frames.
     *
     * @param event The Sentry event to check
     * @return true if the error is plugin-related, false otherwise
     */
    fun isPluginRelatedError(event: SentryEvent): Boolean {
        val throwable = event.throwable ?: return false
        return containsPluginFrame(throwable)
    }

    private fun containsPluginFrame(throwable: Throwable): Boolean {
        val visited = mutableSetOf<Throwable>()
        val toVisit = ArrayDeque<Throwable>()
        toVisit.add(throwable)

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeFirst()

            // Avoid infinite loops from circular references
            if (!visited.add(current)) {
                continue
            }

            // Check if any stack frame belongs to plugin package
            if (current.stackTrace.any { frame ->
                frame.className == TelemetryUtils.PLUGIN_PACKAGE ||
                frame.className.startsWith("${TelemetryUtils.PLUGIN_PACKAGE}.")
            }) {
                return true
            }

            // Walk cause chain and suppressed exceptions
            current.cause?.let(toVisit::addLast)
            current.suppressed.forEach(toVisit::addLast)
        }

        return false
    }
}
