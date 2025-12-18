package io.confluent.intellijplugin.ccloud.config

/**
 * Configuration for Confluent Cloud API endpoints and settings.
 * this file will be later modified or removed
 */
object CloudConfig {

    const val BASE_PATH = "confluent.cloud"

    val CONTROL_PLANE_BASE_URL: String
        get() = "https://api.$BASE_PATH"

    object ControlPlane {
        const val ORG_LIST_URI = "/org/v2/organizations"
        const val ENV_LIST_URI = "/org/v2/environments"
        const val LKC_LIST_URI = "/cmk/v2/clusters?environment=%s"
        const val SR_LIST_URI = "/srcm/v3/clusters?environment=%s"
    }
}

