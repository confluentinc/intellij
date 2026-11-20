package io.confluent.intellijplugin.client

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Local-machine record of which (file, token endpoint) pairs the user has explicitly approved for OAuth
 * private-key JWT client authentication.
 *
 * This is deliberately kept apart from connection settings: connection settings can be imported or shared,
 * and an approval must never travel with them. Roaming is disabled so settings sync and export skip it too:
 * the same path on another machine may hold a different file.
 */
@Service(Service.Level.APP)
@State(
    name = "ConfluentKafkaOAuthFileApprovals",
    storages = [Storage("confluent_kafka_oauth_file_approvals.xml", roamingType = RoamingType.DISABLED)]
)
class OAuthBearerFileApprovals : PersistentStateComponent<OAuthBearerFileApprovals.ApprovalState> {

    class ApprovalState {
        var approved: MutableSet<String> = LinkedHashSet()
    }

    // Connections are opened concurrently on IO threads.
    private val approved = LinkedHashSet<String>()

    @Synchronized
    fun isApproved(file: String, endpoint: String?): Boolean = key(file, endpoint) in approved

    @Synchronized
    fun approve(file: String, endpoint: String?) {
        approved.add(key(file, endpoint))
    }

    @Synchronized
    override fun getState() = ApprovalState().also { it.approved = LinkedHashSet(approved) }

    @Synchronized
    override fun loadState(state: ApprovalState) {
        approved.clear()
        approved.addAll(state.approved)
    }

    companion object {
        fun getInstance(): OAuthBearerFileApprovals = service()

        // A file is approved only for the token endpoint it was approved with: the risk is the pairing.
        private fun key(file: String, endpoint: String?) = "$file\n${endpoint.orEmpty()}"
    }
}
