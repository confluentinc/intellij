package io.confluent.intellijplugin.rfs

import com.intellij.credentialStore.Credentials
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.constants.BdtConnectionType
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.intellijplugin.core.settings.DoNotSerialize
import io.confluent.intellijplugin.core.settings.connections.ConnectionConfigurable
import io.confluent.intellijplugin.core.settings.connections.ConnectionGroup
import io.confluent.intellijplugin.core.settings.connections.CredentialId
import javax.swing.Icon

/**
 * Connection data for Confluent Cloud.
 * Stores the API key/secret for authenticating with Confluent Cloud APIs.
 */
class ConfluentConnectionData(
    name: String = "Confluent Cloud"
) : RemoteFsDriverProvider(name) {

    @DoNotSerialize
    var apiKey: String
        get() = getCredentials(API_KEY_CREDENTIAL_ID)?.userName ?: ""
        set(value) {
            setCredentials(Credentials(value, apiSecret), API_KEY_CREDENTIAL_ID)
        }

    @DoNotSerialize
    var apiSecret: String
        get() = getCredentials(API_KEY_CREDENTIAL_ID)?.getPasswordAsString() ?: ""
        set(value) {
            setCredentials(Credentials(apiKey, value), API_KEY_CREDENTIAL_ID)
        }

    override fun credentialIds() = super.credentialIds() + API_KEY_CREDENTIAL_ID

    override fun getIcon(): Icon = AllIcons.Nodes.Folder

    override fun createDriverImpl(project: Project?, isTest: Boolean): Driver =
        ConfluentDriver(this, project, testConnection = isTest)

    override fun rfsDriverType() = BdtConnectionType.KAFKA // Reusing Kafka type for now

    override fun createConfigurable(project: Project, parentGroup: ConnectionGroup): ConnectionConfigurable<*, *> {
        throw UnsupportedOperationException("Confluent Cloud connections are configured through the tool window")
    }

    fun hasCredentials(): Boolean = apiKey.isNotBlank() && apiSecret.isNotBlank()

    companion object {
        val API_KEY_CREDENTIAL_ID = CredentialId("confluent.cloud.api.key")
    }
}

