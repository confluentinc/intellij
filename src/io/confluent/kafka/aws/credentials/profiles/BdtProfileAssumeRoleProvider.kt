package io.confluent.kafka.aws.credentials.profiles

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.kafka.aws.credentials.promptForMfaToken
import io.confluent.kafka.aws.credentials.utils.StsAuthenticationException
import org.jetbrains.annotations.TestOnly
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileProperty
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest
import software.amazon.awssdk.services.sts.model.StsException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

class BdtProfileAssumeRoleProvider(@get:TestOnly internal val parentProvider: AwsCredentialsProvider, val profile: Profile) :
  AwsCredentialsProvider, Disposable {
  private val stsClient: StsClient
  private val credentialsProvider: StsAssumeRoleCredentialsProvider

  val allowMfaDialog = AtomicBoolean(false)

  init {
    val roleArn = profile.requiredProperty(ProfileProperty.ROLE_ARN)
    val roleSessionName = profile.property(
      ProfileProperty.ROLE_SESSION_NAME).orElseGet { "aws-toolkit-jetbrains-${System.currentTimeMillis()}" }
    val externalId = profile.property(ProfileProperty.EXTERNAL_ID).orElse(null)
    val mfaSerial = profile.property(ProfileProperty.MFA_SERIAL).orElse(null)

    // https://docs.aws.amazon.com/sdkref/latest/guide/setting-global-duration_seconds.html
    val durationSecs = profile.property(ProfileProperty.DURATION_SECONDS).map { it.toIntOrNull() }.orElse(null) ?: 3600

    //In toolkit used
    //stsClient = AwsClientManager.getInstance().createUnmanagedClient(parentProvider, Region.of(region.id))
    stsClient = StsClient.builder().credentialsProvider(parentProvider).region(Region.AWS_GLOBAL).build()
    credentialsProvider = StsAssumeRoleCredentialsProvider.builder()
      .stsClient(stsClient)
      .refreshRequest(
        Supplier {
          createAssumeRoleRequest(
            profile.name(),
            mfaSerial,
            roleArn,
            roleSessionName,
            externalId,
            durationSecs,
            allowMfaDialog.get()
          )
        }
      )
      .build()
  }

  private fun createAssumeRoleRequest(
    profileName: String,
    mfaSerial: String?,
    roleArn: String,
    roleSessionName: String?,
    externalId: String?,
    durationSeconds: Int,
    allowMfaDialog: Boolean
  ): AssumeRoleRequest {
    val requestBuilder = AssumeRoleRequest.builder()
      .roleArn(roleArn)
      .roleSessionName(roleSessionName)
      .externalId(externalId)
      .durationSeconds(durationSeconds)

    mfaSerial?.let { _ ->
      requestBuilder
        .serialNumber(mfaSerial)
        .tokenCode(promptForMfaToken(profileName, mfaSerial, allowMfaDialog))
    }

    return requestBuilder.build()
  }

  override fun resolveCredentials(): AwsCredentials = try {
    credentialsProvider.resolveCredentials()
  }
  catch (_: StsException) {
    try {
      ProfileCredentialProviderFactory.instance.clearCache(profile.name(), configFilePath = null, credentialFilePath = null)
      credentialsProvider.resolveCredentials()
    }
    catch (t: StsException) {
      throw StsAuthenticationException(t)
    }
  }

  override fun dispose() {
    credentialsProvider.close()
    stsClient.close()

    (parentProvider as? Disposable)?.let { Disposer.dispose(it) }
  }
}
