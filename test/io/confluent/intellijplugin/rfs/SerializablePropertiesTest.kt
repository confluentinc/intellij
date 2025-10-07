package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.core.settings.DoNotSerialize
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

@Suppress("DEPRECATION")
class SerializablePropertiesTest {
    companion object {
        val allSerializableProperties: List<ConnectionDataTest.Companion.DeclaredProperty<out ConnectionData>> =
            SerializableProperties.allProperties.map { p -> toDeclaredProperty(p) }
            @JvmStatic get

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> toDeclaredProperty(property: KProperty1<T, *>): ConnectionDataTest.Companion.DeclaredProperty<T> {
            val declaringClass = property.javaGetter!!.declaringClass as Class<T>
            return ConnectionDataTest.Companion.DeclaredProperty(declaringClass, property)
        }
    }

    @ParameterizedTest
    @MethodSource("getAllSerializableProperties")
    fun testEditable(property: ConnectionDataTest.Companion.DeclaredProperty<out ConnectionData>) {
        Assertions.assertInstanceOf(
            KMutableProperty1::class.java, property.p,
            "property should be mutable or marked with ${DoNotSerialize::class.simpleName}: ${property.p}"
        )
    }

    @Disabled
    @ParameterizedTest
    @MethodSource("getAllSerializableProperties")
    fun testReadsDirectlyBackingField(property: ConnectionDataTest.Companion.DeclaredProperty<out ConnectionData>) {
        Assertions.assertNotNull(property.p.javaField, "serializable property should have backing field: ${property.p}")
        val hasGetter = with(ConnectionDataTest.Companion) {
            toDeclaredProperty(property.p).findKmProperty().hasGetter()
        }
        val isFinal = with(ConnectionDataTest.Companion) {
            toDeclaredProperty(property.p).isFinal()
        }
        Assertions.assertFalse(hasGetter, "serializable property should not have custom getter: ${property.p}")
        Assertions.assertTrue(isFinal, "serializable property should be final: ${property.p}")
    }
}