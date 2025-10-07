package io.confluent.intellijplugin.core.settings.fields

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import io.confluent.intellijplugin.core.rfs.util.RfsNotificationUtils
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.getValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty1

class LoadingChooserComponent<D : ConnectionData>(
    prop: KMutableProperty1<D, String?>,
    key: ModificationKey,
    initSettings: D,
    emptyText: String,
    coroutineScope: CoroutineScope,
    isEditable: Boolean = false,
    chooseListener: suspend CoroutineScope.() -> String?
) : WrappedTextComponent<D, String?>(prop, key) {
    val textField = TextFieldWithBrowseButton(JBTextField().apply { this.emptyText.text = emptyText }) {
        eventListener(coroutineScope, chooseListener)
    }

    init {
        textField.text = prop.get(initSettings) ?: ""
        textField.isEditable = isEditable
    }

    fun setValue(value: String?) {
        textField.text = value ?: ""
    }

    override fun getTextComponent() = textField.textField

    override fun getValue(): String? = textField.text.ifBlank { null }
    override fun getComponent() = textField
    override fun isModified(conn: D): Boolean = prop.get(conn) != getValue()
    override fun apply(conn: D) = prop.set(conn, getValue())

    override fun getValidators(): List<ComponentValidator> = getTextComponent().let { component ->
        if (!component.isEnabled || !component.isVisible) emptyList() else component.getValidator()?.let { listOf(it) }
            ?: emptyList()
    }

    private fun eventListener(coroutineScope: CoroutineScope, chooseListener: suspend CoroutineScope.() -> String?) {
        coroutineScope.launch {
            try {
                val newText = chooseListener() ?: return@launch
                withContext(Dispatchers.EDT) {
                    textField.text = newText
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.EDT) {
                    RfsNotificationUtils.showExceptionMessage(project = null, t)
                }
            }
        }
    }
}