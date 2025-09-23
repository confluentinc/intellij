package io.confluent.kafka.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.patterns.PlatformPatterns

class KafkaProducerGeneratorCompletionContributor : CompletionContributor() {
  private val PLACE_IN_PROPERTY = PlatformPatterns.psiElement()
    .withSuperParent(2, JsonProperty::class.java)
    .andNot(PlatformPatterns.psiElement().withParent(JsonStringLiteral::class.java))


  private val AFTER_COMMA_OR_BRACKET_IN_ARRAY = PlatformPatterns.psiElement()
    .afterLeaf("[", ",").withSuperParent(2, JsonArray::class.java)
    .andNot(PlatformPatterns.psiElement().withParent(JsonStringLiteral::class.java))

  private val IN_STRING_LITERAL = PlatformPatterns.psiElement().inside(JsonStringLiteral::class.java)


  init {
    extend(CompletionType.BASIC, IN_STRING_LITERAL, KafkaProducerGeneratorCompletionProvider(false))
    extend(CompletionType.BASIC, PLACE_IN_PROPERTY, KafkaProducerGeneratorCompletionProvider(true))
    extend(CompletionType.BASIC, AFTER_COMMA_OR_BRACKET_IN_ARRAY, KafkaProducerGeneratorCompletionProvider(true))
  }
}