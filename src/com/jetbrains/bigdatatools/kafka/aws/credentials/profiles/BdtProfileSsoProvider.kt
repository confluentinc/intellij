package io.confluent.kafka.aws.credentials.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.aws.credentials.sso.BdtSsoAccessTokenProvider
import io.confluent.kafka.aws.credentials.sso.BdtSsoCredentialProvider
import io.confluent.kafka.aws.credentials.sso.SsoPrompt
import io.confluent.kafka.aws.credentials.sso.SsoSupport
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.profiles.internal.ProfileSection
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.ssooidc.SsoOidcClient

class BdtProfileSsoProvider(profile: Profile, profileFile: ProfileFile) : AwsCredentialsProvider, Disposable {
  private val ssoClient: SsoClient
  private val ssoOidcClient: SsoOidcClient
  val credentialsProvider: BdtSsoCredentialProvider

  init {
    val correctProfile = if (profile.propertyExists(ProfileSection.SSO_SESSION.propertyKeyName)) {
      val profileProperties = profile.properties()
      val ssokey = profile.property(ProfileSection.SSO_SESSION.propertyKeyName).get()
      val ssoSessionSection = profileFile.getSection(ProfileSection.SSO_SESSION.sectionTitle, ssokey).get()

      val combinedProperties = profileProperties + ssoSessionSection.properties()
      profile.toBuilder().properties(combinedProperties).build()
    }
    else {
      profile
    }

    val ssoRegion = correctProfile.requiredProperty(ProfileProperty.SSO_REGION)

    ssoClient = SsoClient.builder().region(Region.of(ssoRegion)).credentialsProvider(AnonymousCredentialsProvider.create()).build()
    ssoOidcClient = SsoOidcClient.builder().region(Region.of(ssoRegion)).credentialsProvider(AnonymousCredentialsProvider.create()).build()

    val ssoAccessTokenProvider = BdtSsoAccessTokenProvider(
      correctProfile.requiredProperty(ProfileProperty.SSO_START_URL),
      ssoRegion,
      SsoPrompt,
      SsoSupport.diskCache,
      ssoOidcClient
    )

    credentialsProvider = BdtSsoCredentialProvider(
      correctProfile.requiredProperty(ProfileProperty.SSO_ACCOUNT_ID),
      correctProfile.requiredProperty(ProfileProperty.SSO_ROLE_NAME),
      ssoClient,
      ssoAccessTokenProvider
    )

    Disposer.register(this, credentialsProvider)
  }

  override fun resolveCredentials(): AwsCredentials = credentialsProvider.resolveCredentials()

  override fun dispose() {
    ssoClient.close()
    ssoOidcClient.close()
  }
}
