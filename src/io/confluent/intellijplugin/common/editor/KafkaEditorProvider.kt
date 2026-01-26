package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.confluent.intellijplugin.common.models.KafkaEditorType
import io.confluent.intellijplugin.consumer.editor.CCloudConsumerEditor
import io.confluent.intellijplugin.consumer.editor.KafkaConsumerEditor
import io.confluent.intellijplugin.core.data.StructuredFilesUtil
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.editor.KafkaProducerEditor

class KafkaEditorProvider : WeighedFileEditorProvider(), DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file.getUserData(KAFKA_EDITOR_TYPE) != null

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val type = file.getUserData(KAFKA_EDITOR_TYPE) ?: error("Kafka editor type is not found")
        val topic = file.getUserData(KAFKA_DEFAULT_TOPIC)

        // Check for CCloud connection first
        val ccloudManager = file.getUserData(CCLOUD_MANAGER_KEY)
        if (ccloudManager != null) {
            return when (type) {
                KafkaEditorType.CONSUMER -> CCloudConsumerEditor(project, ccloudManager, file, topic)
                KafkaEditorType.PRODUCER -> error("CCloud producer not yet implemented")
            }
        }

        // Native Kafka connection
        val manager = file.getUserData(KAFKA_MANAGER_KEY) ?: error("Kafka manager is not found")
        return when (type) {
            KafkaEditorType.CONSUMER -> KafkaConsumerEditor(project, manager, file, topic)
            KafkaEditorType.PRODUCER -> KafkaProducerEditor(project, manager, file, topic)
        }
    }

    override fun getEditorTypeId(): String = PROVIDER_ID
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun getWeight(): Double = StructuredFilesUtil.DEFAULT_EDITOR_WEIGHT - 1

    companion object {
        private const val PROVIDER_ID = "kafka-producer"

        val KAFKA_MANAGER_KEY = Key<KafkaDataManager>("KAFKA_MANAGER")
        val KAFKA_EDITOR_TYPE = Key<KafkaEditorType>("KAFKA_EDITOR_TYPE")
        val KAFKA_DEFAULT_TOPIC = Key<String>("KAFKA_DEFAULT_TOPIC")

        /** Key for CCloud data manager */
        val CCLOUD_MANAGER_KEY = Key<ClusterScopedDataManager>("CCLOUD_MANAGER")
    }
}

