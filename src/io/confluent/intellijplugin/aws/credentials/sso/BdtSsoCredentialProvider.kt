@file:Suppress("BannedImports")

package io.confluent.intellijplugin.aws.credentials.sso

import com.intellij.openapi.Disposable
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.sso.model.UnauthorizedException
import software.amazon.awssdk.utils.cache.CachedSupplier
import software.amazon.awssdk.utils.cache.RefreshResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [AwsCredentialsProvider] that contains all the needed hooks to perform an end to end flow of an SSO-based credential.
 *
 * This credential provider will trigger an SSO login if required, unlike the low level SDKs.
 */
class BdtSsoCredentialProvider(
  private val ssoAccount: String,
  private val ssoRole: String,
  private val ssoClient: SsoClient,
  private val ssoAccessTokenProvider: BdtSsoAccessTokenProvider
) : AwsCredentialsProvider, Disposable {
  private val sessionCache: CachedSupplier<SsoCredentialsHolder> = CachedSupplier.builder(this::refreshCredentials).build()

  val allowDialog = AtomicBoolean(false)

  override fun resolveCredentials(): AwsCredentials = sessionCache.get().credentials

  private fun refreshCredentials(): RefreshResult<SsoCredentialsHolder> {
    val roleCredentials = try {
      val accessToken = ssoAccessTokenProvider.accessToken(allowDialog.get())

      ssoClient.getRoleCredentials {
        it.accessToken(accessToken.accessToken)
        it.accountId(ssoAccount)
        it.roleName(ssoRole)
      }
    }
    catch (e: UnauthorizedException) {
      // OIDC access token was rejected, invalidate the cache and throw
      ssoAccessTokenProvider.invalidate()
      throw e
    }

    val awsCredentials = AwsSessionCredentials.create(
      roleCredentials.roleCredentials().accessKeyId(),
      roleCredentials.roleCredentials().secretAccessKey(),
      roleCredentials.roleCredentials().sessionToken()
    )

    val expirationTime = Instant.ofEpochMilli(roleCredentials.roleCredentials().expiration())

    val ssoCredentials = SsoCredentialsHolder(awsCredentials, expirationTime)

    return RefreshResult.builder(ssoCredentials)
      .staleTime(expirationTime.minus(Duration.ofMinutes(1)))
      .prefetchTime(expirationTime.minus(Duration.ofMinutes(5)))
      .build()
  }

  override fun dispose() {
    sessionCache.close()
  }

  private data class SsoCredentialsHolder(val credentials: AwsSessionCredentials, val expirationTime: Instant)
}
