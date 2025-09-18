package com.jetbrains.bigdatatools.kafka.aws.credentials.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("unused")
class CliCompatibleInstantDeserializer : JsonAdapter<Instant>() {
  private val threadLocalFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz", Locale.US)
  }

  override fun fromJson(reader: JsonReader): Instant? {
    val dateString = reader.nextString()

    // CLI appends UTC, which Java refuses to parse. Convert it to a Z
    val sanitized = if (dateString.endsWith("UTC")) {
      dateString.dropLast(3) + 'Z'
    }
    else {
      dateString
    }

    return DateTimeFormatter.ISO_INSTANT.parse(sanitized) { Instant.from(it) }
  }

  override fun toJson(writer: JsonWriter, value: Instant?) {
    writer.value(DateTimeFormatter.ISO_INSTANT.format(value))
  }
}