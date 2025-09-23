package io.confluent.kafka.core.settings.fields

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.core.settings.components.BdtPropertyComponent
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.withValidator
import io.confluent.kafka.core.ui.components.ConnectionProperty
import io.confluent.kafka.core.util.toPresentableText
import kotlin.reflect.KMutableProperty1

/** Properties could contain also sensitive information and this component stores it in secure storage. */
class SecretPropertiesFieldComponent<D : ConnectionData>(
  project: Project,
  completionVariants: List<ConnectionProperty>,
  private val credentialsHolder: CredentialsHolder<D>,
  key: ModificationKey,
  initSettings: D,
  parentDisposable: Disposable
) : AbstractPropertiesFieldComponent<D>(project, completionVariants, key, initSettings, parentDisposable) {
  init {
    credentialsHolder.wrapUsernameField(propertyComponent.propertyField)
  }

  override fun apply(conn: D) = credentialsHolder.apply(conn)
  override fun isModified(conn: D): Boolean = credentialsHolder.isModified()
}

class PropertiesFieldComponent<D : ConnectionData>(
  project: Project,
  completionVariants: List<ConnectionProperty>,
  val prop: KMutableProperty1<D, String>,
  key: ModificationKey,
  initSettings: D, parentDisposable: Disposable
) : AbstractPropertiesFieldComponent<D>(project, completionVariants, key, initSettings, parentDisposable) {
  init {
    propertyComponent.propertyField.setTextWithoutScroll(prop.get(initSettings))
  }

  override fun apply(conn: D) = prop.set(conn, getValue())
  override fun isModified(conn: D): Boolean = prop.get(conn) != getValue()
}

abstract class AbstractPropertiesFieldComponent<D : ConnectionData> protected constructor(project: Project,
                                                                                          completionVariants: List<ConnectionProperty>,
                                                                                          key: ModificationKey,
                                                                                          val initSettings: D,
                                                                                          parentDisposable: Disposable) :
  WrappedNamedComponent<D>(key) {
  protected val propertyComponent = BdtPropertyComponent(project, completionVariants, key.label)

  init {
    propertyComponent.propertyField.withValidator(parentDisposable) { inputText ->
      try {
        BdtPropertyComponent.parseProperties(inputText)
        null
      }
      catch (t: Throwable) {
        t.message ?: t.toPresentableText()
      }
    }
  }

  override fun getValue(): String = propertyComponent.propertyField.text
  override fun getComponent() = propertyComponent.propertyField

  fun mergeConfig(props: Map<String, String?>) {
    val originalProps: Map<String, String?> = getProperties() ?: emptyMap()
    val newProperties = originalProps + props

    val text = BdtPropertyComponent.joinProperties(newProperties)
    getComponent().text = text
  }

  fun getProperties() = try {
    BdtPropertyComponent.parseProperties(getComponent().text).associate { (it.name ?: "") to (it.value ?: "") }
  }
  catch (t: Throwable) {
    null
  }
}