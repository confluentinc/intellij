package io.confluent.intellijplugin.core.delegate

/**
 * Delegate class for simple events.
 *
 * Usage example
 * <pre>
 * val delegate = Delegate<String, Unit>()
 * delegate += { println("1>$it") }
 * delegate.notify("hello")
 * </pre>
 */
open class Delegate<T, R> {
    private var listeners = listOf<(T) -> R>()

    operator fun plusAssign(listener: (T) -> R) {
        listeners = listeners + listener
    }

    operator fun minusAssign(listener: (T) -> R) {
        listeners = listeners - listener
    }

    fun notify(p: T) {
        listeners.forEach { it(p) }
    }

    fun clear() {
        listeners = emptyList()
    }
}

/**
 * Delegate class for simple events.
 *
 * Usage example
 * <pre>
 * val delegate = Delegate<String, Int, Unit>()
 * delegate += { column, index -> println("$column | $index") }
 * delegate.notify("column1", 123)
 * </pre>
 */
open class Delegate2<T1, T2, R> {
    private var listeners = listOf<(T1, T2) -> R>()

    operator fun plusAssign(listener: (T1, T2) -> R) {
        listeners = listeners + listener
    }

    operator fun minusAssign(listener: (T1, T2) -> R) {
        listeners = listeners - listener
    }

    fun notify(p1: T1, p2: T2) {
        listeners.forEach { it(p1, p2) }
    }

    fun clear() {
        listeners = emptyList()
    }
}