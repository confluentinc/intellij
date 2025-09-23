package io.confluent.kafka.consumer.editor

import com.intellij.openapi.Disposable
import io.confluent.kafka.core.settings.buildValidator
import io.confluent.kafka.core.settings.registerValidator
import io.confluent.kafka.util.KafkaMessagesBundle
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
    setDateTime(Date().time)

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

  fun setDateTime(time: Long?) {
    text = try {
      dateFormat.format(time)
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