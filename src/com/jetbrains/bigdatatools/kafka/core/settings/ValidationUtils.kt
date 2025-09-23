package io.confluent.kafka.core.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenFocusGained
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.ui.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.Cell
import io.confluent.kafka.core.connection.ProxyEnableType
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.fields.*
import io.confluent.kafka.core.ui.applyRecursively
import io.confluent.kafka.core.ui.doOnChange
import io.confluent.kafka.core.util.BdtUrlUtils
import io.confluent.kafka.core.util.toPresentableText
import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.awt.Container
import java.awt.event.ItemListener
import java.io.File
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.text.JTextComponent

fun <D : ConnectionData> WrappedTextComponent<D, *>.withValidator(uiDisposable: Disposable, validate: (String) -> @Nls String?):
  WrappedTextComponent<D, *> {
  val validator = buildValidator(getTextComponent(), { getTextComponent().text }, validate)
  registerValidator(uiDisposable, validator, getTextComponent())
  return this
}

fun <D : ConnectionData> WrappedTextComponent<D, *>.withValidator(uiDisposable: Disposable, supplier: Supplier<ValidationInfo?>):
  WrappedTextComponent<D, *> {
  registerValidator(uiDisposable, supplier, getTextComponent())
  return this
}

fun <D : ConnectionData, E : Any?> NullableComboBoxField<D, E>.withValidator(uiDisposable: Disposable, validate: (E?) -> @Nls String?):
  NullableComboBoxField<D, E> {
  registerValidator(uiDisposable, {
    validate(getValue())?.let {
      ValidationInfo(it, getComponent())
    }
  }, getComponent())
  return this
}

fun <D : ConnectionData> UsernameNamedField<D>.withValidator(uiDisposable: Disposable, validate: (String) -> String?):
  UsernameNamedField<D> {
  val validator = buildValidator(getComponent(), { getValue() }, validate)
  registerValidator(uiDisposable, validator, getTextComponent())
  return this
}

fun <D : ConnectionData> WrappedDropDownList<D, *>.withValidator(uiDisposable: Disposable, validate: (String) -> String?):
  WrappedDropDownList<D, *> {
  val validator = buildValidator(getComponent(), { getComponent().item?.id ?: "" }, validate)
  registerValidator(uiDisposable, validator, getComponent())
  return this
}

fun <D : ConnectionData> PasswordNamedField<D>.withValidator(uiDisposable: Disposable, validate: (String) -> String?):
  PasswordNamedField<D> {
  val validator = buildValidator(getComponent(), { String(getValue()) }, validate)
  registerValidator(uiDisposable, validator, getTextComponent())
  return this
}

fun <D : ConnectionData> UsernameSecuredField<D>.withValidator(uiDisposable: Disposable, validate: (String) -> String?):
  UsernameSecuredField<D> {
  val validator = buildValidator(getComponent(), { String(getValue()) }, validate)
  registerValidator(uiDisposable, validator, getTextComponent())
  return this
}

/** Validates that the text field is empty of a valid integer is written. */
fun <D : ConnectionData> WrappedTextComponent<D, *>.withNumberValidator(uiDisposable: Disposable): WrappedTextComponent<D, *> =
  this.withValidator(uiDisposable) { txt ->
    validateIsNumber(txt)
  }

fun <D : ConnectionData> WrappedTextComponent<D, *>.withNumberValidatorOrEmpty(uiDisposable: Disposable): WrappedTextComponent<D, *> =
  this.withValidator(uiDisposable) { txt ->
    return@withValidator numOrEmpty(txt)
  }

private fun validateIsNumber(txt: String): @Nls String? = if (txt.isNotEmpty())
  try {
    Integer.parseInt(txt)
    null
  }
  catch (nex: NumberFormatException) {
    KafkaMessagesBundle.message("validator.number")
  }
else
  KafkaMessagesBundle.message("validator.number")

fun <D : ConnectionData> WrappedTextComponent<D, *>.withPortValidator(uiDisposable: Disposable): WrappedTextComponent<D, *> =
  this.withValidator(uiDisposable) { txt ->
    val value = txt.toIntOrNull()
    if (value == null || value < 0 || value > 65535) {
      return@withValidator KafkaMessagesBundle.message("validator.portNumber")
    }
    return@withValidator null
  }

fun isParentVisible(component: JComponent): Boolean {
  var c: Container? = component
  while (c != null) {
    c = if (!c.isVisible)
      return false
    else c.parent
  }
  return true
}

fun isValidateable(component: JComponent): Boolean = component.isEnabled && isParentVisible(component)

/**
 * Returns string without trailing ":" if it exists.
 *
 * In warning and error messages we need to provide some information about component location an the only way we currently have is
 * to use component label. For our components the label usually looks like "Some component:" and this method cut ":".
 */
fun labelToName(label: String): String = if (label.endsWith(":")) label.substring(0, label.length - 1) else label

/** Validates that the text field is not empty. */
fun <D : ConnectionData> WrappedTextComponent<D, *>.withNotEmptyValidator(uiDisposable: Disposable,
                                                                          componentName: String? = null): WrappedTextComponent<D, *> =
  this.withValidator(uiDisposable, nonEmptyValidator(componentName ?: labelToName(labelComponent.text)))

fun <D : ConnectionData> WrappedDropDownList<D, *>.withNotEmptyValidator(uiDisposable: Disposable,
                                                                         componentName: String? = null) =
  this.withValidator(uiDisposable, nonEmptyValidator(componentName ?: labelToName(labelComponent.text)))

fun <D : ConnectionData> UsernameNamedField<D>.withNotEmptyValidator(uiDisposable: Disposable,
                                                                     componentName: String? = null): UsernameNamedField<D> =
  this.withValidator(uiDisposable, nonEmptyValidator(componentName ?: labelToName(labelComponent.text)))

fun <D : ConnectionData> UsernameSecuredField<D>.withNotEmptyValidator(uiDisposable: Disposable,
                                                                       componentName: String? = null): UsernameSecuredField<D> =
  this.withValidator(uiDisposable, nonEmptyValidator(componentName ?: labelToName(labelComponent.text)))

fun <D : ConnectionData> PasswordNamedField<D>.withNotEmptyValidator(uiDisposable: Disposable,
                                                                     componentName: String? = null): PasswordNamedField<D> =
  this.withValidator(uiDisposable, nonEmptyValidator(componentName ?: labelToName(labelComponent.text)))

private fun <D : ConnectionData> WrappedNamedComponent<D>.nonEmptyValidator(componentName: String?): (String) -> String? = { txt ->
  if (isValidateable(getComponent()) && txt.isBlank()) {
    if (componentName == null) KafkaMessagesBundle.message("validator.notEmpty")
    else KafkaMessagesBundle.message("validator.notEmptyNamed", componentName)
  }
  else {
    null
  }
}

fun JComponent.getValidator(): ComponentValidator? = ComponentValidator.getInstance(this).orElse(null)

fun EditorTextField.withValidator(uiDisposable: Disposable, validate: (String) -> String?): EditorTextField {
  val validator = buildValidator(this, { text }, validate)
  registerValidator(uiDisposable, validator, this).enableValidation()
  return this
}

fun EditorTextField.withValidator(uiDisposable: Disposable, validate: (JComponent, String) -> String?): EditorTextField {
  val validator = buildValidator(this, { text }, validate)
  registerValidator(uiDisposable, validator, this).enableValidation()
  return this
}

fun JTextComponent.withValidator(uiDisposable: Disposable, validate: (String) -> String?): JTextComponent {
  val validator = buildValidator(this, { text }, validate)
  registerValidator(uiDisposable, validator, this)
  return this
}

fun JTextComponent.withPositiveIntValidator(uiDisposable: Disposable): JTextComponent =
  withValidator(uiDisposable) {
    val res = it.toIntOrNull()
    if (res == null || res < 0) KafkaMessagesBundle.message("validator.positiveNumber") else null
  }

fun JTextComponent.withNumberOrEmptyValidator(uiDisposable: Disposable): JTextComponent =
  withValidator(uiDisposable) {
    numOrEmpty(it)
  }

fun JTextComponent.withNonEmptyValidator(uiDisposable: Disposable): JTextComponent = withValidator(uiDisposable) {
  if (text.isBlank())
    KafkaMessagesBundle.message("validator.notEmpty")
  else
    null
}

fun <T> ComboBox<T>.withValidator(uiDisposable: Disposable, validationFun: () -> ValidationInfo?): ComboBox<T> {
  val validator = Supplier { validationFun() }
  registerValidator(uiDisposable, validator, this)
  return this
}

fun <D : ConnectionData> WrappedTextComponent<D, *>.withProxyHostValidator(uiDisposable: Disposable,
                                                                           proxyTypeComboBox: ComboBoxField<*, ProxyEnableType>) =
  withValidator(uiDisposable) {
    if (proxyTypeComboBox.getComponent().item != ProxyEnableType.CUSTOM)
      return@withValidator null

    val error = BdtUrlUtils.validateUrl(it)?.toPresentableText()
    if (error != null) KafkaMessagesBundle.message("proxy.host.format.error.detailed", error)
    else null
  }

fun <D : ConnectionData> WrappedTextComponent<D, *>.withUrlValidator(uiDisposable: Disposable, allowEmpty: Boolean = false) =
  withValidator(uiDisposable) {
    if (it.isBlank()) {
      if (!allowEmpty) {
        return@withValidator KafkaMessagesBundle.message("url.format.error.empty")
      }
      return@withValidator null
    }

    val error = BdtUrlUtils.validateUrl(it)?.toPresentableText()
    if (error != null) KafkaMessagesBundle.message("url.format.error.detailed", error)
    else null
  }

fun Cell<TextFieldWithBrowseButton>.withEmptyOrFileExistValidator(uiDisposable: Disposable, canBeEmpty: Boolean) {
  component.childComponent.withEmptyOrFileExistValidator(uiDisposable, canBeEmpty)
}

fun BrowseTextField<*>.withEmptyOrFileExistValidator(uiDisposable: Disposable, canBeEmpty: Boolean) {
  getComponent().textField.withEmptyOrFileExistValidator(uiDisposable, canBeEmpty)
}

fun JTextComponent.withEmptyOrFileExistValidator(uiDisposable: Disposable, canBeEmpty: Boolean) {
  val validator: Supplier<ValidationInfo?> = Supplier {
    when {
      !canBeEmpty && this.text.isNullOrBlank() -> ValidationInfo(KafkaMessagesBundle.message("validator.file.empty"), this)
      this.text.isNullOrBlank() -> null
      File(this.text).exists() -> null
      else -> ValidationInfo(KafkaMessagesBundle.message("validator.file.not.found", this.text), this)
    }
  }

  ComponentValidator(uiDisposable)
    .withValidator(validator)
    .withFocusValidator(validator)
    .andStartOnFocusLost()
    .installOn(this)

  var hasValidationError = false
  this.whenFocusLost {
    hasValidationError = this.getValidationInfo() != null
  }

  this.doOnChange {
    // If we already have error, update it, if not - do nothing
    if (hasValidationError && this.getValidationInfo() != null) {
      this.revalidateComponent()
    }
  }
}

fun JComponent.revalidateComponent() = ComponentValidator.getInstance(this).ifPresent { it.revalidate() }

fun JComponent.revalidateComponentRecursive(): Boolean {
  var isValid = true
  applyRecursively { component ->
    val validator = component.getValidator() ?: return@applyRecursively
    validator.revalidate()
    if (validator.validationInfo != null) {
      isValid = false
    }
  }
  return isValid
}

fun buildValidator(component: JComponent, getText: () -> String, validate: (String) -> String?) = Supplier {
  return@Supplier validate(getText())?.let {
    @Suppress("HardCodedStringLiteral") // Here could be any text for example raw exception.
    ValidationInfo(it, component)
  }
}

fun buildValidator(component: JComponent, getText: () -> String, validate: (JComponent, String) -> String?) = Supplier {
  return@Supplier validate(component, getText())?.let {
    @Suppress("HardCodedStringLiteral") // Here could be any text for example raw exception.
    ValidationInfo(it, component)
  }
}

fun buildValidator(component: JComponent, getText: () -> String, validator: InputValidatorEx?) = Supplier {
  return@Supplier validator?.getErrorText(getText())?.let { ValidationInfo(it, component) }
}

fun registerValidator(uiDisposable: Disposable, validator: Supplier<ValidationInfo?>, component: ComboBox<*>): ComponentValidator {
  val componentValidator = ComponentValidator(uiDisposable)
    .withValidator(validator)
    .withFocusValidator(validator)
    .andStartOnFocusLost()
    .installOn(component)

  val itemListener = ItemListener {
    componentValidator.revalidate()
  }
  component.addItemListener(itemListener)

  val listListener = object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent?) = componentValidator.revalidate()
    override fun intervalRemoved(e: ListDataEvent?) = componentValidator.revalidate()
    override fun contentsChanged(e: ListDataEvent?) = componentValidator.revalidate()
  }
  component.model.addListDataListener(listListener)

  Disposer.register(uiDisposable, Disposable {
    component.removeItemListener(itemListener)
    component.model.removeListDataListener(listListener)
  })

  return componentValidator
}

// Special validator for our current logic.
// Validation will be shown only on focusLost.
// On focusGain previous validation result will not be shown.
private fun registerTextValidator(uiDisposable: Disposable,
                                  validator: Supplier<ValidationInfo?>,
                                  component: JComponent): ComponentValidator {

  val focusValidator = Supplier<ValidationInfo?> {
    if (component.isFocusOwner) {
      null
    }
    else {
      validator.get()
    }
  }

  component.whenFocusGained {
    component.getValidator()?.apply {
      revalidate()
    }
  }

  return ComponentValidator(uiDisposable)
    .withValidator(focusValidator)
    .withFocusValidator(focusValidator)
    .andStartOnFocusLost()
    .installOn(component)
}

fun registerValidator(uiDisposable: Disposable, validator: Supplier<ValidationInfo?>, component: JTextComponent): ComponentValidator {
  return registerTextValidator(uiDisposable, validator, component)
}

fun registerValidator(uiDisposable: Disposable, validator: Supplier<ValidationInfo?>, component: EditorTextField): ComponentValidator {
  return registerTextValidator(uiDisposable, validator, component)
}

fun JComponent.getValidationInfo(): ValidationInfo? {
  if (!isValidateable(this)) return null
  val validator = getValidator() ?: return null
  validator.revalidate()
  return validator.validationInfo
}

fun getValidationInfos(components: Iterable<WrappedComponent<*>>): List<ValidationInfo> {
  val validators = components
    .filter { isValidateable(it.getComponent()) }
    .flatMap { it.getValidators() }
  return validators
    .mapNotNull { validator ->
      validator.revalidate()
      validator.validationInfo
    }
}

fun getValidationErrors(components: Iterable<WrappedComponent<*>>): List<ValidationInfo> =
  getValidationInfos(components).filter { !it.warning }

private fun numOrEmpty(txt: String): String? {
  return if (txt.isEmpty()) null
  else if (txt.toIntOrNull() == null) KafkaMessagesBundle.message("validator.number")
  else null
}

/**
 * We can create a component with special validation method:
 * private fun validateValue(component: JComponent, text: String) : String?
 *
 * Assign this validator:
 * editorField = EditorTextField().withValidator(this, ::validateValue)
 *
 * We can place this flag on JComponent, and use it in validation method to skip validation.
 * editorField.putUserData(SKIP_VALIDATION, true)
 *
 * And then, in validateValue write
 *   private fun validateValue(component: JComponent, text: String) : String? {
 *     if(component.getUserData(SKIP_VALIDATION) == true) {
 *       component.putUserData(SKIP_VALIDATION, false)
 *       return null
 *     }
 *     return validate(text)
 *   }
 */
val SKIP_VALIDATION = Key<Boolean>("SKIP_VALIDATION")