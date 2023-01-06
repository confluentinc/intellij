package com.jetbrains.bigdatatools.kafka.util

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.KafkaBundle"

object KafkaMessagesBundle : DynamicBundle(BUNDLE) {
  @Nls
  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)

  // In some places we have to show kafka properties which can be dynamic and we can miss some keys.
  @Nls
  @JvmStatic
  fun messageOrKey(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
    @Suppress("HardCodedStringLiteral") // We are providing key as a default result.
    BundleBase.messageOrDefault(resourceBundle, key, key, params)
}