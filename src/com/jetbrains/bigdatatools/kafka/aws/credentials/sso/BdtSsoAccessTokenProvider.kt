package com.jetbrains.bigdatatools.kafka.aws.credentials.sso

import com.intellij.openapi.progress.ProgressManager
import com.jetbrains.bigdatatools.kafka.core.rfs.exception.RfsAuthRequiredError
import com.jetbrains.bigdatatools.kafka.core.util.sleepWithCancellation
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import software.amazon.awssdk.services.ssooidc.model.AuthorizationPendingException
import software.amazon.awssdk.services.ssooidc.model.InvalidClientException
import software.amazon.awssdk.services.ssooidc.model.SlowDownException
import java.time.Clock
import java.time.Duration
import java.time.Instant


class BdtSsoAccessTokenProvider(
  private val ssoUrl: String,
  private val ssoRegion: String,
  private val onPendingToken: SsoLoginCallback,
  private val cache: SsoCache,
  private val client: SsoOidcClient,
  private val clock: Clock = Clock.systemUTC()
) {
  fun accessToken(allowDialog: Boolean): BdtAccessToken {
    cache.loadAccessToken(ssoUrl)?.let {
      return it
    }

    if (!allowDialog)
      throw RfsAuthRequiredError()

    val token = pollForToken()

    cache.saveAccessToken(ssoUrl, token)

    return token
  }

  private fun registerClient(): ClientRegistration {
    cache.loadClientRegistration(ssoRegion)?.let {
      return it
    }

    // Based on botocore: https://github.com/boto/botocore/blob/5dc8ee27415dc97cfff75b5bcfa66d410424e665/botocore/utils.py#L1753
    val registerResponse = client.registerClient {
      it.clientType(CLIENT_REGISTRATION_TYPE)
      it.clientName("aws-toolkit-jetbrains-${Instant.now(clock)}")
    }

    val registeredClient = ClientRegistration(
      registerResponse.clientId(),
      registerResponse.clientSecret(),
      Instant.ofEpochSecond(registerResponse.clientSecretExpiresAt())
    )

    cache.saveClientRegistration(ssoRegion, registeredClient)

    return registeredClient
  }

  private fun authorizeClient(clientId: ClientRegistration): Authorization {
    // Should not be cached, only good for 1 token and short lived
    val authorizationResponse = try {
      client.startDeviceAuthorization {
        it.startUrl(ssoUrl)
        it.clientId(clientId.clientId)
        it.clientSecret(clientId.clientSecret)
      }
    }
    catch (e: InvalidClientException) {
      cache.invalidateClientRegistration(ssoRegion)
      throw e
    }

    return Authorization(
      authorizationResponse.deviceCode(),
      authorizationResponse.userCode(),
      authorizationResponse.verificationUri(),
      authorizationResponse.verificationUriComplete(),
      Instant.now(clock).plusSeconds(authorizationResponse.expiresIn().toLong()),
      authorizationResponse.interval().toLong()
    )
  }

  private fun pollForToken(): BdtAccessToken {
    val progressIndicator = ProgressManager.getInstance().progressIndicator
    val registration = registerClient()
    val authorization = authorizeClient(registration)

    onPendingToken.tokenPending(authorization)

    var backOffTime = Duration.ofSeconds(authorization.pollInterval)

    while (true) {
      try {
        val tokenResponse = client.createToken {
          it.clientId(registration.clientId)
          it.clientSecret(registration.clientSecret)
          it.grantType(GRANT_TYPE)
          it.deviceCode(authorization.deviceCode)
        }

        val expirationTime = Instant.now(clock).plusSeconds(tokenResponse.expiresIn().toLong())

        onPendingToken.tokenRetrieved()

        return BdtAccessToken(
          ssoUrl,
          ssoRegion,
          tokenResponse.accessToken(),
          expirationTime
        )
      }
      catch (_: SlowDownException) {
        backOffTime = backOffTime.plusSeconds(SLOW_DOWN_DELAY_SECS)
      }
      catch (_: AuthorizationPendingException) {
        // Do nothing, keep polling
      }
      catch (e: Exception) {
        onPendingToken.tokenRetrievalFailure(e)
        throw e
      }

      sleepWithCancellation(backOffTime, progressIndicator)
    }
  }

  fun invalidate() {
    cache.invalidateAccessToken(ssoUrl)
  }

  private companion object {
    const val CLIENT_REGISTRATION_TYPE = "public"
    const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

    const val SLOW_DOWN_DELAY_SECS = 5L
  }
}
