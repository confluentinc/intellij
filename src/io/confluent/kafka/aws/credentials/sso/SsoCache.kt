// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package io.confluent.kafka.aws.credentials.sso

interface SsoCache {
  fun loadClientRegistration(ssoRegion: String): ClientRegistration?
  fun saveClientRegistration(ssoRegion: String, registration: ClientRegistration)
  fun invalidateClientRegistration(ssoRegion: String)

  fun loadAccessToken(ssoUrl: String): BdtAccessToken?
  fun saveAccessToken(ssoUrl: String, accessToken: BdtAccessToken)
  fun invalidateAccessToken(ssoUrl: String)
}
