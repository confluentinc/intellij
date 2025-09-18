package com.jetbrains.bigdatatools.kafka.model

data class Metric(val type: String, val canonicalName: String, val params: Map<String, String>, val value: Map<String, Int>)