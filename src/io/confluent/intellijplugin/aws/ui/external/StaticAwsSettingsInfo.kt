package io.confluent.intellijplugin.aws.ui.external

import com.intellij.openapi.util.NlsSafe

data class StaticAwsSettingsInfo(override val authenticationType: String,
                                 @NlsSafe override val profile: String? = null,
                                 override val accessKey: String? = null,
                                 override val secretKey: String? = null,
                                 override val region: String? = null,
                                 override val customCredentialPath: String? = null,
                                 override val customConfigPath: String? = null) : AwsSettingsInfo