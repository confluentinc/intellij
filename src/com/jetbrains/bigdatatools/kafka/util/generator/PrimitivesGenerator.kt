package com.jetbrains.bigdatatools.kafka.util.generator

import kotlin.random.Random

object PrimitivesGenerator {
  fun generateString(minLength: Int = 10, maxLength: Int = 20): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..Random.nextInt(minLength, maxLength)).map { allowedChars.random() }.joinToString("")
  }

  fun generateLong(from: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE): String = Random.nextLong(from, until).toString()

  fun generateDouble(from: Double = Double.MIN_VALUE, until: Double = Double.MAX_VALUE): String = Random.nextDouble(from, until).toString()

  fun generateFloat(from: Float = Float.MIN_VALUE, until: Float = Float.MAX_VALUE): String =
    (from + Random.nextFloat() * (until - from)).toString()

  fun generateBytes(minSize: Int = 10, maxSize: Int = 100): String = Random.nextBytes(Random.nextInt(minSize, maxSize)).toString()
}