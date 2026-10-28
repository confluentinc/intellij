package io.confluent.intellijplugin.client

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Local-machine record of which (file, token endpoint) pairs the user has explicitly approved for OAuth
 * private-key JWT client authentication.
 *
 * This is deliberately kept apart from connection settings: connection settings can be imported or shared,
 * and an approval must never travel with them.
 */
@Service(Service.Level.APP)
@State(
    name = "ConfluentKafkaOAuthFileApprovals",
    storages = [Storage("confluent_kafka_oauth_file_approvals.xml")]
)
class OAuthBearerFileApprovals : PersistentStateComponent<OAuthBearerFileApprovals> {

    var approved: MutableSet<String> = LinkedHashSet()

    fun isApproved(file: String, endpoint: String?): Boolean = key(file, endpoint) in approved

    fun approve(file: String, endpoint: String?) {
        approved.add(key(file, endpoint))
    }

    override fun getState() = this

    override fun loadState(state: OAuthBearerFileApprovals) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): OAuthBearerFileApprovals = service()

        // A file is approved only for the token endpoint it was approved with: the risk is the pairing.
        private fun key(file: String, endpoint: String?) = "$file\n${endpoint.orEmpty()}"
    }
}
