package io.confluent.intellijplugin.consumer.editor

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.common.editor.ListTableModel
import io.confluent.intellijplugin.core.ui.ExpansionPanel

/**
 * Common interface for Kafka records output implementations.
 * Allows switching between Swing-based and JCEF-based implementations.
 */
interface IKafkaRecordsOutput : Disposable {
    val dataPanel: ExpansionPanel
    val detailsPanel: ExpansionPanel
    val outputModel: ListTableModel<KafkaRecord>

    fun replace(output: List<KafkaRecord>)
    fun stop()
    fun start()
    fun setMaxRows(limit: Int)
    fun addBatchRows(pollTime: Long, elements: List<KafkaRecord>)
    fun addError(element: KafkaRecord)
    fun getElements(): List<KafkaRecord>
}
