package com.jetbrains.bigdatatools.kafka.core.rfs.statistics.v2

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.instanceParameter

/**
 * [Task](https://youtrack.jetbrains.com/issue/BDIDE-4723/Core-Add-statistic-v2.0)
 *
 * [Documentation](https://docs.google.com/document/d/1EA81k_vnqqhFSXJSsEIvNYlT8qCDhZpLc1So_kCP7_s/edit?usp=sharing)
 */
abstract class BdtStatisticCollector : CounterUsagesCollector() {
  protected val index: AtomicInteger = AtomicInteger(0)
  val groupDescription: String = "Big Data Tools Settings"

  /**
   * Required to check in tests that statistic is setup for UI components as listener
   */
  @VisibleForTesting
  var isSetupForFields: Boolean = false

  private val componentEvents = mutableMapOf<KProperty1<out Any, Any?>, BaseEventId>()
  private val componentActionsEvents = mutableMapOf<KProperty1<out Any, Any?>, BaseEventId>()

  abstract val connectionType: BdtConnectionType

  open fun init() {
    collectors[connectionType.nameForStat] = this
  }

  fun initPanel(settings: Any) {
    index.set(0)

    componentEvents.forEach {
      @Suppress("UNCHECKED_CAST")
      val property = it.key as KProperty1<Any, Any>
      val propertyReceiver = property.instanceParameter?.type?.classifier as KClass<*>
      if (propertyReceiver.isInstance(settings)) {
        val component = property.get(settings)
        try {
          BdtStatisticUtils.attachCollector(component, it.value, index, connectionType)
        }
        catch (t: Throwable) {
          throw Exception("Cannot add statistic listener to ${it.key.name} field", t)
        }
      }
    }

    componentActionsEvents.forEach {
      @Suppress("UNCHECKED_CAST")
      val property = it.key as KProperty1<Any, Any>
      val propertyReceiver = property.instanceParameter?.type?.classifier as KClass<*>
      if (propertyReceiver.isInstance(settings)) {
        val component = property.get(settings)
        try {
          BdtStatisticUtils.attachActionCollector(component, it.value, index, connectionType)
        }
        catch (t: Throwable) {
          throw Exception("Cannot add action statistic listener to ${it.key.name} field", t)
        }
      }
    }

    isSetupForFields = true
  }

  protected fun registryEvent(field: KProperty1<out Any, Any?>, prefix: String = "") {
    val eventId = "$prefix${field.name}.changed"

    val event = events[eventId] ?: group.registerEvent(eventId, indexField, typeField)
    if (!events.containsKey(eventId)) {
      events[eventId] = event
    }

    componentEvents += field to event
  }

  protected fun registryFieldAction(field: KProperty1<out Any, Any?>, prefix: String = "") {
    val eventId = "$prefix${field.name}.invoke"

    val event = events[eventId] ?: group.registerEvent(eventId, indexField, typeField)
    if (!events.containsKey(eventId)) {
      events[eventId] = event
    }

    componentActionsEvents += field to event
  }

  protected fun registryFieldActionEnum(field: KProperty1<out Any, Any?>,
                                        prefix: String = ""): EventId3<Int, BdtConnectionType, Class<*>> =
    group.registerEvent("$prefix${field.name}.invoke", indexField, typeField, EventFields.Class("selected")).also {
      componentActionsEvents += field to it
    }

  protected fun registryCustomBooleanEvent(actionId: String): EventId3<Int, BdtConnectionType, Boolean> =
    group.registerEvent(actionId, indexField, typeField, boolNewValueField)

  protected fun registryEventId1(actionId: String): EventId2<Int, BdtConnectionType> = group.registerEvent(actionId, indexField, typeField)

  protected fun registryActionEnum(actionId: String, enumValues: List<String>): EventId3<Int, BdtConnectionType, String> =
    group.registerEvent(actionId, indexField, typeField, stringField("status", enumValues))

  fun registryStringEnumEvent(field: KProperty1<out Any, Any?>, allowedTypes: List<String>, prefix: String = "") {
    val eventId = "$prefix${field.name}.changed"

    val event = events[eventId] ?: group.registerEvent(eventId, indexField, typeField, stringField("value", allowedTypes))
    if (!events.containsKey(eventId)) {
      events[eventId] = event
    }

    componentEvents += field to event
  }

  fun registryCheckboxEvent(field: KProperty1<out Any, Any?>, prefix: String = "") {
    val eventId = "$prefix${field.name}.changed"

    val event = events[eventId] ?: group.registerEvent(eventId, indexField, typeField, boolNewValueField)
    if (!events.containsKey(eventId)) {
      events[eventId] = event
    }

    componentEvents += field to event
  }

  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("kafka.connection.settings", 1)
    private val events: MutableMap<String, BaseEventId> = mutableMapOf<String, BaseEventId>()
    val collectors: MutableMap<String, BdtStatisticCollector> = mutableMapOf<String, BdtStatisticCollector>()

    val typeField: EnumEventField<BdtConnectionType> = EventFields.Enum<BdtConnectionType>("type")
    val indexField: IntEventField = EventFields.Int("index")
    val boolNewValueField: BooleanEventField = EventFields.Boolean("value")
    val enabledField: BooleanEventField = EventFields.Boolean("enabled")

    fun stringField(name: String, enumValues: List<String>): StringEventField = EventFields.String(name, enumValues)
    fun getInstance(groupId: String): BdtStatisticCollector? = collectors[groupId]
  }
}