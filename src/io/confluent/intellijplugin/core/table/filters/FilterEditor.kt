package io.confluent.intellijplugin.core.table.filters

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.ui.components.fields.ExtendableTextField
import io.confluent.intellijplugin.core.ui.SearchExtension
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class FilterEditor(var modelIndex: Int) : JComponent(), UiDataProvider {

    private val listeners = mutableListOf<FilerEditorChangeListener>()

    private val editor = ExtendableTextField()

    private val leftExtension = SearchExtension()
    private var leftExtensionShown = false

    init {
        isOpaque = false

        editor.apply {
            isOpaque = false
            border = null
        }
        showLeftExtension()

        editor.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                hideLeftExtension()
            }

            override fun focusLost(e: FocusEvent?) {
                if (editor.text.isNullOrBlank()) showLeftExtension()
            }
        })

        editor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = changed()
            override fun removeUpdate(e: DocumentEvent?) = changed()
            override fun changedUpdate(e: DocumentEvent?) = changed()

            private fun changed() {
                if (!editor.text.isNullOrBlank()) {
                    hideLeftExtension()
                } else if (!editor.hasFocus()) {
                    showLeftExtension()
                }
                listeners.forEach { it.onChange() }
            }
        })

        layout = BorderLayout()

        add(editor, BorderLayout.CENTER)
    }

    internal val isSearchIconVisibleInTest: Boolean
        @TestOnly get() = leftExtensionShown

    private fun showLeftExtension() {
        if (!leftExtensionShown) {
            editor.addExtension(leftExtension)
            leftExtensionShown = true
        }
    }

    private fun hideLeftExtension() {
        if (leftExtensionShown) {
            editor.removeExtension(leftExtension)
            leftExtensionShown = false
        }
    }

    var text: String?
        get() = editor.text
        set(value) {
            if (editor.text != value) {
                editor.text = value ?: ""
            }
        }

    fun addListener(listener: FilerEditorChangeListener) {
        listeners += listener
    }

    fun removeListener(listener: FilerEditorChangeListener) {
        listeners -= listener
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink.setNull(CommonDataKeys.EDITOR)
    }
}