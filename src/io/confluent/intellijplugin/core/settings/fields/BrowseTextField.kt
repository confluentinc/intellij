package io.confluent.intellijplugin.core.settings.fields

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.getValidator
import kotlin.reflect.KMutableProperty1

class BrowseTextField<D : ConnectionData>(
    prop: KMutableProperty1<D, String?>,
    key: ModificationKey,
    fileChooserDescriptor: FileChooserDescriptor,
    private val initSettings: D
) : WrappedNamedField<D, String?>(prop, key, initSettings) {
    private val pathWithBroseButton = TextFieldWithBrowseButton(field)
    private fun currentValue() = prop.get(initSettings) ?: ""

    init {
        pathWithBroseButton.addBrowseFolderListener(null, fileChooserDescriptor)
        pathWithBroseButton.textField.columns = 1

        field.text = currentValue()
    }

    override fun getComponent() = pathWithBroseButton

    override fun isModified(conn: D): Boolean =
        getTextComponent().text.ifBlank { null } != currentValue().ifBlank { null }

    override fun apply(conn: D) {
        prop.set(conn, field.text.ifBlank { null })
    }

    override fun getValidators(): List<ComponentValidator> {
        val component = getComponent()
        if (!component.isVisible || !component.isEnabled) return emptyList()
        return listOfNotNull(field.getValidator())
    }
}
