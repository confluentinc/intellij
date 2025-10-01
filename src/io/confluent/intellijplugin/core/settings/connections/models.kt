package io.confluent.intellijplugin.core.settings.connections

import java.io.Serializable

data class Property(var name: String? = "", var value: String? = "") : Serializable