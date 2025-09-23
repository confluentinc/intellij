package io.confluent.kafka.core.rfs.driver

import java.io.IOException

open class DriverException(message: String, cause: Throwable? = null) : IOException(message, cause)

class DriverConnectionBrokenException(val driver: Driver): DriverException("Connection is broken for driver $driver")