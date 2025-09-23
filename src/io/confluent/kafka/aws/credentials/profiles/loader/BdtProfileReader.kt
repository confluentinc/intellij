// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package io.confluent.kafka.aws.credentials.profiles.loader

import com.intellij.openapi.diagnostic.thisLogger
import io.confluent.kafka.aws.credentials.profiles.BdtCredentialSourceType
import io.confluent.kafka.aws.credentials.profiles.propertyExists
import io.confluent.kafka.aws.credentials.profiles.requiredProperty
import io.confluent.kafka.aws.credentials.profiles.traverseCredentialChain
import io.confluent.kafka.util.KafkaMessagesBundle
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.profiles.internal.ProfileSection
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.jvm.optionals.getOrElse


object BdtProfileReader {
  fun getProfilesNames(configPath: String?, credentialPath: String?): List<String> = try {
    val profileFile = getProfileFile(configPath, credentialPath)
    profileFile.profiles().values.map { it.name() }
  }
  catch (t: Throwable) {
    thisLogger().info("Cannot request profile names for config $configPath, creds: $credentialPath", t)
    emptyList()
  }

  fun getProfileFile(configPath: String?, credentialPath: String?): ProfileFile {
    val config = configPath?.let { Path(it) }
    val credentials = credentialPath?.let { Path(it) }

    if (config?.exists() == false)
      error(KafkaMessagesBundle.message("connection.error.profile.config.file.is.not.found", config.pathString))

    if (credentials?.exists() == false)
      error(KafkaMessagesBundle.message("connection.error.profile.creds.file.is.not.found", credentials.pathString))

    if (config == null && credentials == null)
      return ProfileFile.defaultProfileFile()

    val profileFileBuilder = ProfileFile.aggregator()
    if (config != null) {
      val configProfile = ProfileFile.builder().content(config).type(ProfileFile.Type.CONFIGURATION).build()
      profileFileBuilder.addFile(configProfile)
    }
    if (credentials != null) {
      val configProfile = ProfileFile.builder().content(credentials).type(ProfileFile.Type.CREDENTIALS).build()
      profileFileBuilder.addFile(configProfile)
    }

    return profileFileBuilder.build()
  }


  fun readAndValidate(profileName: String, profileFile: ProfileFile): Profile {
    val allProfiles = profileFile.profiles()
    if (allProfiles.isEmpty())
      error("Cannot find any profiles, please specify path or setup credentials and/or config files in .aws directory")
    val profile = profileFile.profile(profileName).orElseGet {
      error(KafkaMessagesBundle.message("credentials.profile.removed", profileName))
    }

    validateProfile(profile, profileFile)

    return profile
  }


  private fun validateProfile(profile: Profile, profileFile: ProfileFile) {
    when {
      profile.propertyExists(ProfileProperty.SSO_START_URL) -> validateSsoProfile(profile)
      profile.propertyExists(ProfileSection.SSO_SESSION.propertyKeyName) -> validateSsoWithSessionProfile(profile, profileFile)
      profile.propertyExists(ProfileProperty.ROLE_ARN) -> validateAssumeRoleProfile(profile, profileFile)
      profile.propertyExists(ProfileProperty.AWS_SESSION_TOKEN) -> validateStaticSessionProfile(profile)
      profile.propertyExists(ProfileProperty.AWS_ACCESS_KEY_ID) -> validateBasicProfile(profile)
      profile.propertyExists(ProfileProperty.CREDENTIAL_PROCESS) -> {
        // NO-OP Always valid
      }
      else -> {
        throw IllegalArgumentException(KafkaMessagesBundle.message("credentials.profile.unsupported", profile.name()))
      }
    }
  }

  private fun validateSsoProfile(profile: Profile) {
    profile.requiredProperty(ProfileProperty.SSO_ACCOUNT_ID)
    profile.requiredProperty(ProfileProperty.SSO_REGION)
    profile.requiredProperty(ProfileProperty.SSO_ROLE_NAME)
  }

  private fun validateSsoWithSessionProfile(profile: Profile, profileFile: ProfileFile) {
    profile.requiredProperty(ProfileProperty.SSO_ACCOUNT_ID)
    profile.requiredProperty(ProfileProperty.SSO_ROLE_NAME)

    val ssoKey = profile.property(ProfileSection.SSO_SESSION.propertyKeyName).get()
    profileFile.getSection(ProfileSection.SSO_SESSION.sectionTitle, ssoKey).getOrElse {
      throw IllegalArgumentException(KafkaMessagesBundle.message("sso.session.is.not.found", ssoKey))
    }
  }

  private fun validateAssumeRoleProfile(profile: Profile, profileFile: ProfileFile) {
    val rootProfile = profile.traverseCredentialChain(profileFile.profiles()).last()
    val credentialSource = rootProfile.property(ProfileProperty.CREDENTIAL_SOURCE)

    if (credentialSource.isPresent) {
      try {
        BdtCredentialSourceType.parse(credentialSource.get())
      }
      catch (_: Exception) {
        throw IllegalArgumentException(
          KafkaMessagesBundle.message("credentials.profile.assume_role.invalid_credential_source", rootProfile.name()))
      }
    }
    else {
      validateProfile(rootProfile, profileFile)
    }
  }

  private fun validateStaticSessionProfile(profile: Profile) {
    profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID)
    profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY)
    profile.requiredProperty(ProfileProperty.AWS_SESSION_TOKEN)
  }

  private fun validateBasicProfile(profile: Profile) {
    profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID)
    profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY)
  }
}
