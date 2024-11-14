package com.jetbrains.bigdatatools.kafka.consumer.editor

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.common.settings.buildValidator
import com.jetbrains.bigdatatools.common.settings.registerValidator
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JFormattedTextField
import javax.swing.text.MaskFormatter

internal class TimestampTextField(uiDisposable: Disposable) : JFormattedTextField(maskFormatter) {
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
    isLenient = false
  }

  init {
    toolTipText = KafkaMessagesBundle.message("kafka.timestamp.tooltip.text")
    setDateTime(Date())

    val validator = buildValidator(
      this,
      { text },
      {
        val dateTime = getDateTime()
        if (dateTime == null) KafkaMessagesBundle.message("kafka.timestamp.validation.error") else null
      }
    )
    registerValidator(uiDisposable, validator, this)
  }

  fun getDateTime(): Date? = try {
    dateFormat.parse(text)
  }
  catch (e: Exception) {
    null
  }

  fun setDateTime(date: Any?) {
    text = try {
      dateFormat.format(date)
    }
    catch (_: Exception) {
      ""
    }
  }

  companion object {
    private val maskFormatter = MaskFormatter("####-##-## ##:##:##").apply {
      placeholderCharacter = '_'
    }
  }
}