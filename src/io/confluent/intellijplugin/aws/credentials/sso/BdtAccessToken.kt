// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package io.confluent.intellijplugin.aws.credentials.sso

import software.amazon.awssdk.services.sso.SsoClient
import software.amazon.awssdk.services.ssooidc.SsoOidcClient
import java.time.Instant

/**
 * Access token returned from [SsoOidcClient.createToken] used to retrieve AWS Credentials from [SsoClient.getRoleCredentials].
 */
data class BdtAccessToken(
  val startUrl: String,
  val region: String,
  val accessToken: String,
  val expiresAt: Instant
)
