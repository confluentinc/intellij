package io.confluent.intellijplugin.common.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import io.confluent.intellijplugin.common.models.KafkaEditorType
import io.confluent.intellijplugin.consumer.editor.KafkaConsumerEditor
import io.confluent.intellijplugin.core.data.StructuredFilesUtil
import io.confluent.intellijplugin.data.BaseClusterDataManager
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.producer.editor.KafkaProducerEditor

class KafkaEditorProvider : WeighedFileEditorProvider(), DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file.getUserData(KAFKA_EDITOR_TYPE) != null

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val type = file.getUserData(KAFKA_EDITOR_TYPE) ?: error("Kafka editor type is not found")
        val topic = file.getUserData(KAFKA_DEFAULT_TOPIC)

        // Get either native Kafka or CCloud data manager
        val dataManager: BaseClusterDataManager = file.getUserData(KAFKA_MANAGER_KEY)
            ?: file.getUserData(CCLOUD_MANAGER_KEY)
            ?: error("Data manager is not found")

        return when (type) {
            KafkaEditorType.CONSUMER -> KafkaConsumerEditor(project, dataManager, file, topic)
            KafkaEditorType.PRODUCER -> KafkaProducerEditor(project, dataManager, file, topic)
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
        val CCLOUD_MANAGER_KEY = Key<CCloudClusterDataManager>("CCLOUD_MANAGER")
    }
}

