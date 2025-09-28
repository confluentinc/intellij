package io.confluent.intellijplugin.rfs

import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.TestApplication
import io.confluent.intellijplugin.core.settings.ConnectionSettingsBase
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionSettingProviderEP
import kotlinx.metadata.*
import kotlinx.metadata.jvm.KotlinClassMetadata
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import kotlin.reflect.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

@TestApplication
class ConnectionDataTest {

  private val registeredConnectionDataClasses
    get() = ConnectionSettingProviderEP.getConnectionFactories().map {
      it.createBlankData().javaClass
    }.sortedBy { it.name }
  private val serializableProperties
    get() = registeredConnectionDataClasses.flatMap { registeredClass ->
      generateSequence<Class<out ConnectionData>>(registeredClass) { c ->
        if (ConnectionData::class.java.isAssignableFrom(c.superclass))
          c.superclass.asSubclass(ConnectionData::class.java)
        else
          null
      }
    }.distinct().flatMap { classWithProperties ->
      classWithProperties.declaredProperties()
    }.filter {
      ConnectionSettingsBase.shouldSerialize(it.p)
    }.sortedBy { it.p.name }

  companion object {

    fun <T : Any> Class<T>.declaredProperties(): List<DeclaredProperty<T>> {
      return this.kotlin.declaredMemberProperties.map { DeclaredProperty(this, it) }
    }

    data class DeclaredProperty<T>(val c: Class<T>, val p: KProperty1<T, *>)

    fun KmProperty.hasGetter(): Boolean {
      return this.getter.isNotDefault
    }

    fun KmProperty.hasSetter(): Boolean {
      return this.setter?.isNotDefault == true
    }

    fun KmProperty.isAbstract(): Boolean {
      return this.modality == Modality.ABSTRACT
    }

    fun DeclaredProperty<*>.isFinal(): Boolean {
      return this.findKmProperty().modality == Modality.FINAL || Modifier.isFinal(this.c.modifiers)
    }

    fun KmProperty.isDelegated(): Boolean {
      return this.isDelegated || this.kind == MemberKind.DELEGATION
    }

    fun <T : ConnectionData> Class<T>.superclasses(): List<Class<out ConnectionData>> {
      return generateSequence<Class<out ConnectionData>>(this) { c ->
        if (ConnectionData::class.java.isAssignableFrom(c.superclass))
          c.superclass.asSubclass(ConnectionData::class.java)
        else
          null
      }.toList()
    }

    fun <T> Class<T>.superclassesAndInterfaces(): Set<Class<*>> {
      return generateSequence<Set<Class<*>>>(setOf(this)) { classes ->
        classes.flatMap { it.interfaces.toList() + it.superclass }.filterNotNull().toSet().minus(classes).takeIf { it.isNotEmpty() }
      }.drop(1).reduce(Set<Class<*>>::plus)
    }

    fun <T> DeclaredProperty<T>.overriding(): List<DeclaredProperty<out Any>> {
      return c.superclassesAndInterfaces().flatMap { superclass -> superclass.declaredProperties() }.filter { it.p.name == p.name }
    }

    fun <T : ConnectionData> DeclaredProperty<T>.isOverrideMentioned(): Boolean {
      val superClasses = c.superclasses().drop(1)
      val overriding = superClasses.flatMap { superclass -> superclass.declaredProperties() }.filter { it.p.name == p.name }
      return overriding.any {
        ConnectionData::class.java.isAssignableFrom(it.c) && ConnectionSettingsBase.shouldSerialize(
          it.p)
      }
    }

    fun <T> DeclaredProperty<T>.isOverride(): Boolean {
      return overriding().isNotEmpty()
    }

    fun <T> DeclaredProperty<T>.findKmProperty(): KmProperty {
      return c.getKotlinMetadata()!!.properties.single { it.name == p.name }
    }

    fun Class<*>.getKotlinMetadata(): KmClass? {
      val meta = getAnnotation(Metadata::class.java) ?: return null
      return (KotlinClassMetadata.readStrict(meta) as? KotlinClassMetadata.Class)?.kmClass
    }
  }

  fun visitTypes(type: KType): List<KType> {
    return when (val classifier = type.classifier) {
      is KClass<*> -> {
        classifier.declaredMemberProperties.map { it.returnType }
      }
      is KTypeParameter -> {
        classifier.upperBounds
      }
      else -> error(type)
    }
  }

  @Test
  @Disabled
  fun connectionDataFields() {
    val nameToKind = mutableListOf<Pair<String, String>>()
    val types = LinkedHashSet<KClass<*>>()
    serializableProperties.forEach { property ->
      val fqName = property.c.name + "::" + property.p.name
      val presentation = StringBuilder()
      presentation.append("serializable property")
      if (property.p.javaField == null) {
        presentation.append(", without field")
      }
      val kmProperty = property.findKmProperty()
      if (property.p !is KMutableProperty<*>) {
        presentation.append(", val")
      }
      if (kmProperty.hasGetter()) {
        presentation.append(", with getter")
      }
      if (kmProperty.hasSetter()) {
        presentation.append(", with setter")
      }
      if (kmProperty.isAbstract()) {
        presentation.append(", abstract")
      }
      if (property.isOverrideMentioned()) {
        presentation.append(", override")
      }
      if (property.isOverride()) {
        presentation.append(", override-implement")
      }
      if (kmProperty.isDelegated()) {
        presentation.append(", delegated")
      }
      nameToKind.add(fqName to presentation.toString())
      types.addAll(generateSequence(setOf(property.p.returnType)) { kTypes: Set<KType> ->
        kTypes.flatMap(::visitTypes).toSet().minus(kTypes).takeIf { it.isNotEmpty() }
      }.reduce(Set<KType>::plus).map { it.classifier }.filterIsInstance<KClass<*>>())
    }
    val propertyList = StringBuilder()

    val kindToNames = nameToKind.groupBy { it.second }.mapValues { it.value.map(Pair<String, String>::first) }.toMutableMap()

    propertyList.appendLine("@file:Suppress(\"unused\", \"DEPRECATION\", \"RemoveRedundantQualifierName\")")
    propertyList.appendLine("package com.jetbrains.bigdatatools.rfs")
    propertyList.appendLine()
    propertyList.appendLine("typealias Prop = kotlin.reflect.KMutableProperty1<out com.jetbrains.bigdatatools.common.settings.connections.ConnectionData, *>")
    propertyList.appendLine()
    propertyList.appendLine("object SerializableProperties {")

    fun printGroup(name: String, filterKind: String? = null) {
      if (filterKind != null) {
        propertyList.appendLine("  // ${filterKind}")
      }
      propertyList.appendLine("  val $name = listOf<Prop>(")
      for (kind in (filterKind?.let { listOf(filterKind) } ?: kindToNames.keys).toList()) {
        if (filterKind == null) {
          propertyList.appendLine("    // ${kind}")
        }
        for (fqName in kindToNames.remove(kind) ?: emptyList()) {
          propertyList.appendLine("    $fqName,")
        }
      }
      propertyList.appendLine("  )")
    }

    val groupIntoLists = mapOf(
      "simpleProperties" to "serializable property",
      "migratingSetterProperties" to "serializable property, with setter",
      "implementingProperties" to "serializable property, override-implement",
      "otherProperties" to null
    )
    groupIntoLists.forEach { (k, v) -> printGroup(k, v) }

    propertyList.appendLine()
    propertyList.appendLine("  val allProperties = ${groupIntoLists.keys.joinToString(" + ")}")

    propertyList.appendLine()
    propertyList.appendLine("  val usedClasses = listOf(")
    for (t in types) {
      propertyList.appendLine("    ${t.qualifiedName}::class,")
    }
    propertyList.appendLine("  )")
    propertyList.appendLine("}")
    UsefulTestCase.assertSameLinesWithFile("${PathManager.getHomePath()}/plugins/bigdatatools/tests/test/com/jetbrains/bigdatatools/rfs/SerializableProperties.kt", propertyList.toString())
  }

}