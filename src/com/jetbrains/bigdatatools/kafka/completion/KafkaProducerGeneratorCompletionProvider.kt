package com.jetbrains.bigdatatools.kafka.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.util.ProcessingContext
import com.jetbrains.bigdatatools.kafka.util.generator.FieldTemplateGenerator

class KafkaProducerGeneratorCompletionProvider(private val addQuotas: Boolean) : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val isKafkaJson = parameters.originalFile.virtualFile?.getUserData(KAFKA_JSON_WITH_GENERATOR) ?: false
    if (!isKafkaJson)
      return

    FieldTemplateGenerator.FieldType.entries.forEach {
      val key = if (addQuotas) "\"${it.textRepresentation}\"" else it.textRepresentation
      val element = LookupElementBuilder.create(key)
        .withBoldness(true)
        .withTypeText(it.desc)
        .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)

      result.addElement(PrioritizedLookupElement.withPriority(element, 1000500.0))
    }
  }

  companion object {
    val KAFKA_JSON_WITH_GENERATOR = Key<Boolean>("KAFKA_JSON_WITH_GENERATOR")
  }
}