package io.confluent.intellijplugin.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.KafkaBundle"

internal object KafkaMessagesBundle {
  private val bundle = DynamicBundle(KafkaMessagesBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = bundle.getMessage(key, *params)

  // in some places, we have to show kafka properties which can be dynamic, and we can miss some keys
  @Nls
  fun messageOrKey(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    @Suppress("HardCodedStringLiteral")
    return bundle.messageOrDefault(key = key, defaultValue = key, params)!!
  }
}