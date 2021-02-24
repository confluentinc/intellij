package com.jetbrains.bigdatatools.kafka.util

import com.intellij.AbstractBundle
import com.intellij.reference.SoftReference
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*

object KafkaMessagesBundle {
  private const val BUNDLE_NAME = "messages.kafka"
  private var ourBundle: Reference<ResourceBundle?>? = null

  private val bundle: ResourceBundle?
    get() {
      var bundle = SoftReference.dereference(ourBundle)
      if (bundle == null) {
        bundle = ResourceBundle.getBundle(BUNDLE_NAME)
        ourBundle = java.lang.ref.SoftReference(bundle)
      }
      return bundle
    }

  fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): String {
    return AbstractBundle.message(bundle!!, key, *params)
  }
}