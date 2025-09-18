// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.jetbrains.bigdatatools.kafka.aws.credentials.sso

import com.jetbrains.bigdatatools.kafka.aws.credentials.utils.AwsJson
import com.jetbrains.bigdatatools.kafka.core.util.filePermissions
import com.jetbrains.bigdatatools.kafka.core.util.touch
import com.jetbrains.bigdatatools.kafka.core.util.writeText
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Caches the [BdtAccessToken] to disk to allow it to be re-used with other tools such as the CLI.
 */
class DiskCache(
  private val cacheDir: Path = Paths.get(System.getProperty("user.home"), ".aws", "sso", "cache"),
  private val clock: Clock = Clock.systemUTC()
) : SsoCache {
  override fun loadClientRegistration(ssoRegion: String): ClientRegistration? {
    val path = clientRegistrationCache(ssoRegion)
    if (!path.exists())
      return null
    val text = path.readText()
    text.ifBlank { return null }

    val clientRegistration = AwsJson.fromJsonToClass(text, ClientRegistration::class.java)
    return if (clientRegistration.expiresAt.isNotExpired()) {
      clientRegistration
    }
    else {
      null
    }
  }

  override fun saveClientRegistration(ssoRegion: String, registration: ClientRegistration) {
    val registrationCache = clientRegistrationCache(ssoRegion)
    registrationCache.touch()
    registrationCache.filePermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))

    registrationCache.writeText(AwsJson.toJson(registration))
  }

  override fun invalidateClientRegistration(ssoRegion: String) {
    clientRegistrationCache(ssoRegion).deleteIfExists()
  }

  override fun loadAccessToken(ssoUrl: String): BdtAccessToken? {
    val cacheFile = accessTokenCache(ssoUrl)
    if (!cacheFile.exists())
      return null
    val inputText = cacheFile.readText()

    return try {
      val clientRegistration = AwsJson.fromJsonToClass(inputText, BdtAccessToken::class.java)
      // Use same expiration logic as client registration even though RFC/SEP does not specify it.
      // This prevents a cache entry being returned as valid and then expired when we go to use it.
      if (clientRegistration.expiresAt.isNotExpired()) {
        clientRegistration
      }
      else {
        null
      }
    }
    catch (_: Throwable) {
      null
    }
  }

  override fun saveAccessToken(ssoUrl: String, accessToken: BdtAccessToken) {
    val accessTokenCache = accessTokenCache(ssoUrl)
    accessTokenCache.touch()
    accessTokenCache.filePermissions(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))

    accessTokenCache.writeText(AwsJson.toJson(accessToken))
  }

  override fun invalidateAccessToken(ssoUrl: String) {
    Files.deleteIfExists(accessTokenCache(ssoUrl))
  }

  private fun clientRegistrationCache(ssoRegion: String): Path = cacheDir.resolve("aws-toolkit-jetbrains-client-id-$ssoRegion.json")

  @OptIn(ExperimentalStdlibApi::class)
  private fun accessTokenCache(ssoUrl: String): Path {
    val digest = MessageDigest.getInstance("SHA-1")
    val sha = digest.digest(ssoUrl.toByteArray(Charsets.UTF_8)).toHexString()
    val fileName = "$sha.json"
    return cacheDir.resolve(fileName)
  }

  // If the item is going to expire in the next 15 mins, we must treat it as already expired
  private fun Instant.isNotExpired(): Boolean = this.isAfter(Instant.now(clock).plus(15, ChronoUnit.MINUTES))

}
