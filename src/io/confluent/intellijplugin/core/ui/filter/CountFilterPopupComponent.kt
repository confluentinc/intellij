package io.confluent.intellijplugin.core.ui.filter

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.fields.IntegerField
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

interface CountFilterListener {
    fun filterChanged(value: Int?)
}

class CountFilterPopupComponent(@Nls label: String, value: Int?) : FilterPopupComponent(label) {

    override var currentText: String = ""

    private var currentValue: Int?
        private set

    private val listeners = mutableListOf<CountFilterListener>()

    init {
        currentValue = value
        currentText = value?.toString() ?: KafkaMessagesBundle.message("countFilter.all.action")
    }

    fun addListener(listener: CountFilterListener) = listeners.add(listener)
    fun removeListener(listener: CountFilterListener) = listeners.remove(listener)

    override fun createActionGroup(): ActionGroup {

        val allAction = DumbAwareAction.create(KafkaMessagesBundle.message("countFilter.all.action")) {
            currentText = KafkaMessagesBundle.message("countFilter.all.text")
            currentValue = null
            valueChanged()
        }

        val selectAction = DumbAwareAction.create(KafkaMessagesBundle.message("countFilter.select")) {
            val countEditor = IntegerField(null, 1, Integer.MAX_VALUE)
            currentValue?.let { countEditor.value = it }
            val dateComponent = JPanel(BorderLayout())
            dateComponent.add(JLabel(KafkaMessagesBundle.message("countFilter.count") + " "), BorderLayout.LINE_START)
            dateComponent.add(countEditor, BorderLayout.CENTER)

            val db = DialogBuilder(this@CountFilterPopupComponent)
            db.addCancelAction()
            db.addOkAction()
            db.setCenterPanel(dateComponent)
            db.setPreferredFocusComponent(countEditor)
            db.setTitle(KafkaMessagesBundle.message("countFilter.selectDialogTitle"))

            if (DialogWrapper.OK_EXIT_CODE == db.show()) {
                try {
                    countEditor.validateContent()
                    currentText = countEditor.value.toString()
                    currentValue = countEditor.value
                    valueChanged()
                } catch (e: Exception) {
                }
            }
        }

        val countAction20 = DumbAwareAction.create("20") {
            currentText = "20"
            currentValue = 20
            valueChanged()
        }

        val countAction100 = DumbAwareAction.create("100") {
            currentText = "100"
            currentValue = 100
            valueChanged()
        }

        return DefaultActionGroup(
            allAction,
            selectAction,
            countAction20,
            countAction100
        )
    }

    override fun valueChanged() {
        super.valueChanged()
        listeners.forEach { it.filterChanged(currentValue) }
    }
}