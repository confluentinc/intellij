package io.confluent.intellijplugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.applyIf
import io.confluent.intellijplugin.util.generator.FieldTemplateGenerator

class KafkaProducerGeneratorCompletionProvider(private val addQuotas: Boolean) : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val isKafkaJson = parameters.originalFile.virtualFile?.getUserData(KAFKA_JSON_WITH_GENERATOR) == true
    if (!isKafkaJson)
      return

    FieldTemplateGenerator.FieldType.entries.forEach {
      val element = LookupElementBuilder.create(it.type)
        .withBoldness(true)
        .withTailText(it.parameters)
        .withTypeText(it.description)
        .withInsertHandler(getInsertHandler(addQuotas, it.parameters != null))
        .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)

      result.addElement(PrioritizedLookupElement.withPriority(element, 1000500.0))
    }
  }

  private fun getInsertHandler(addQuotas: Boolean, withParameters: Boolean): InsertHandler<LookupElement> =
    InsertHandler { context: InsertionContext, item: LookupElement ->
      val editor = context.editor
      val document = context.document
      val startOffset = context.startOffset
      val endOffset = context.tailOffset

      val insertedText = document.getText(TextRange(startOffset, endOffset))
      val replacement = insertedText.applyIf(withParameters) { "$this()" }
      val wrapped = "\${$replacement}".applyIf(addQuotas) { StringUtil.wrapWithDoubleQuote(this) }

      document.replaceString(startOffset, endOffset, wrapped)

      if (withParameters) {
        val caretOffset = startOffset + wrapped.indexOf("()") + 1
        editor.caretModel.moveToOffset(caretOffset)
      }
    }

  companion object {
    internal val KAFKA_JSON_WITH_GENERATOR: Key<Boolean> = Key<Boolean>("KAFKA_JSON_WITH_GENERATOR")
  }
}