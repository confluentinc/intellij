package com.jetbrains.bigdatatools.kafka.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.util.ProcessingContext

class KafkaProducerGeneratorCompletionProvider(val addQuotas: Boolean) : CompletionProvider<CompletionParameters>() {
  private val variants = mapOf(
    "random.int" to "Random Integer",
    "random.email" to "Generate Email",
  )

  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val isKafkaJson = parameters.originalFile.virtualFile?.getUserData(KAFKA_JSON_WITH_GENERATOR) ?: false
    if (!isKafkaJson)
      return

    variants.forEach {
      val key = if (addQuotas) "\"\${{${it.key}}}\"" else "\${{${it.key}}}"
      val element = LookupElementBuilder.create(key)
        .withTypeText(it.value)
        .withAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE)


      result.addElement(PrioritizedLookupElement.withPriority(element, 1000500.0))
    }
  }

  companion object {
    val KAFKA_JSON_WITH_GENERATOR = Key<Boolean>("KAFKA_JSON_WITH_GENERATOR")
  }
}