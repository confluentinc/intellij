package io.confluent.intellijplugin.aws.credentials.profiles

enum class BdtCredentialSourceType {
    EC2_INSTANCE_METADATA, ECS_CONTAINER, ENVIRONMENT;

    companion object {
        fun parse(value: String) = when {
            value.equals("Ec2InstanceMetadata", ignoreCase = true) -> {
                EC2_INSTANCE_METADATA
            }

            value.equals("EcsContainer", ignoreCase = true) -> {
                ECS_CONTAINER
            }

            value.equals("Environment", ignoreCase = true) -> {
                ENVIRONMENT
            }

            else -> throw IllegalArgumentException("'$value' is not a valid credential_source")
        }
    }
}
