package io.confluent.intellijplugin.aws.credentials.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.aws.credentials.ToolkitCredentialProcessProvider
import io.confluent.intellijplugin.aws.credentials.profiles.BdtEc2MetadataConfigProvider.getEc2MedataEndpoint
import io.confluent.intellijplugin.aws.credentials.profiles.loader.BdtProfileReader
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.profiles.internal.ProfileSection

@Service
class ProfileCredentialProviderFactory : Disposable {
  private val cache: MutableMap<KeyCache, ValueCache> = mutableMapOf()

  override fun dispose() {}

  fun getOrCreate(profileName: String,
                  configFilePath: String?,
                  credentialFilePath: String?): AwsCredentialsProvider {
    val key = KeyCache(profileName,
                       configFilePath = configFilePath?.ifBlank { null },
                       credentialFilePath = credentialFilePath?.ifBlank { null })

    val profileFile = BdtProfileReader.getProfileFile(configFilePath, credentialFilePath)
    val profile = BdtProfileReader.readAndValidate(profileName, profileFile)

    return synchronized(this) {
      val cachedValue = cache[key]
      if (cachedValue?.profile == profile)
        return cachedValue.credentialsProvider

      clearCache(profileName, configFilePath, credentialFilePath)
      val provider = createAwsCredentialProvider(profile, profileFile)

      (provider as? Disposable)?.let { Disposer.register(this, it) }
      cache[key] = ValueCache(profile, provider)
      provider
    }
  }

  fun clearCache(profileName: String,
                 configFilePath: String?,
                 credentialFilePath: String?) {
    val removed = cache.remove(KeyCache(profileName,
                                        configFilePath = configFilePath?.ifBlank { null },
                                        credentialFilePath = credentialFilePath?.ifBlank { null }))
    executeOnPooledThread {
      (removed?.credentialsProvider as? Disposable)?.let {
        Disposer.dispose(it)
      }
    }
  }

  private fun createAwsCredentialProvider(profile: Profile, profileFile: ProfileFile) = when {
    profile.propertyExists(ProfileSection.SSO_SESSION.propertyKeyName) ||
    profile.propertyExists(ProfileProperty.SSO_START_URL) -> createSsoProvider(profile, profileFile)
    profile.propertyExists(ProfileProperty.ROLE_ARN) -> createAssumeRoleProvider(profile, profileFile)
    profile.propertyExists(ProfileProperty.AWS_SESSION_TOKEN) -> createStaticSessionProvider(profile)
    profile.propertyExists(ProfileProperty.AWS_ACCESS_KEY_ID) -> createBasicProvider(profile)
    profile.propertyExists(ProfileProperty.CREDENTIAL_PROCESS) -> createCredentialProcessProvider(profile)
    else -> {
      throw IllegalArgumentException(KafkaMessagesBundle.message("credentials.profile.unsupported", profile.name()))
    }
  }

  private fun createSsoProvider(profile: Profile, profileFile: ProfileFile): AwsCredentialsProvider = BdtProfileSsoProvider(profile,
                                                                                                                            profileFile)

  private fun createAssumeRoleProvider(profile: Profile, profileFile: ProfileFile): AwsCredentialsProvider {
    val sourceProfileName = profile.property(ProfileProperty.SOURCE_PROFILE)
    val credentialSource = profile.property(ProfileProperty.CREDENTIAL_SOURCE)

    val parentCredentialProvider = when {
      sourceProfileName.isPresent -> {
        val sourceProfile = profileFile.profile(sourceProfileName.get()).orElseGet { null }
                            ?: throw IllegalStateException(KafkaMessagesBundle.message("credentials.profile.removed", sourceProfileName))
        createAwsCredentialProvider(sourceProfile, profileFile)
      }
      credentialSource.isPresent -> {
        // Can we parse the credential_source
        credentialSourceCredentialProvider(BdtCredentialSourceType.parse(credentialSource.get()), profile)
      }
      else -> {
        throw IllegalArgumentException(KafkaMessagesBundle.message("credentials.profile.assume_role.missing_source", profile.name()))
      }
    }

    return BdtProfileAssumeRoleProvider(parentCredentialProvider, profile)
  }

  private fun credentialSourceCredentialProvider(credentialSource: BdtCredentialSourceType, profile: Profile): AwsCredentialsProvider =
    when (credentialSource) {
      BdtCredentialSourceType.ECS_CONTAINER -> ContainerCredentialsProvider.builder().build()
      BdtCredentialSourceType.EC2_INSTANCE_METADATA -> {
        // The IMDS credentials provider should source the endpoint config properties from the currently active profile
        InstanceProfileCredentialsProvider.builder()
          .endpoint(profile.getEc2MedataEndpoint())
          .build()
      }
      BdtCredentialSourceType.ENVIRONMENT -> AwsCredentialsProviderChain.builder()
        .addCredentialsProvider(SystemPropertyCredentialsProvider.create())
        .addCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .build()
    }

  private fun createBasicProvider(profile: Profile) = StaticCredentialsProvider.create(
    AwsBasicCredentials.create(
      profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID),
      profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY)
    )
  )

  private fun createStaticSessionProvider(profile: Profile) = StaticCredentialsProvider.create(
    AwsSessionCredentials.create(
      profile.requiredProperty(ProfileProperty.AWS_ACCESS_KEY_ID),
      profile.requiredProperty(ProfileProperty.AWS_SECRET_ACCESS_KEY),
      profile.requiredProperty(ProfileProperty.AWS_SESSION_TOKEN)
    )
  )

  private fun createCredentialProcessProvider(profile: Profile) =
    ToolkitCredentialProcessProvider(profile.requiredProperty(ProfileProperty.CREDENTIAL_PROCESS))

  companion object {
    private data class KeyCache(val profileName: String, val configFilePath: String?, val credentialFilePath: String?)
    private data class ValueCache(val profile: Profile, val credentialsProvider: AwsCredentialsProvider)

    val instance: ProfileCredentialProviderFactory
      get() = service()
  }
}

