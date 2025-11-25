package io.confluent.intellijplugin.ccloud.model

/**
 * Cloud provider enumeration.
 */
enum class CloudProvider {
    AWS,
    GCP,
    AZURE,
    NONE;

    companion object {
        fun of(provider: String?): CloudProvider {
            if (provider == null) return NONE
            return when (provider.uppercase()) {
                "AWS" -> AWS
                "GCP" -> GCP
                "AZURE" -> AZURE
                else -> NONE
            }
        }
    }
}

