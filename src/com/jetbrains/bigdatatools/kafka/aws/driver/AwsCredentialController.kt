package com.jetbrains.bigdatatools.kafka.aws.driver

import com.jetbrains.bigdatatools.kafka.aws.connection.auth.common.BdtAwsCredentialsProvider
import com.jetbrains.bigdatatools.kafka.aws.credentials.profiles.BdtProfileAssumeRoleProvider
import com.jetbrains.bigdatatools.kafka.aws.credentials.profiles.BdtProfileSsoProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

class AwsCredentialController(val credentialsProvider: BdtAwsCredentialsProvider) {
  var credentials: AwsCredentialsProvider? = null
    get() {
      if (field == null)
        field = credentialsProvider.getCredentials()

      return field
    }

  fun <T> wrapWithAllowDialogs(calledByUser: Boolean, body: () -> T): T = try {
    if (calledByUser) {
      //We need to reload credentials to force refresh session tokens #BDIDE-3590
      credentials = credentialsProvider.getCredentials()

      (credentials as? BdtProfileAssumeRoleProvider)?.allowMfaDialog?.set(true)
      ((credentials as? BdtProfileAssumeRoleProvider)?.parentProvider as? BdtProfileSsoProvider)?.credentialsProvider?.allowDialog?.set(
        true)
      (credentials as? BdtProfileSsoProvider)?.credentialsProvider?.allowDialog?.set(true)
    }

    body()
  }
  finally {
    (credentials as? BdtProfileAssumeRoleProvider)?.allowMfaDialog?.set(false)
    (credentials as? BdtProfileSsoProvider)?.credentialsProvider?.allowDialog?.set(false)
    ((credentials as? BdtProfileAssumeRoleProvider)?.parentProvider as? BdtProfileSsoProvider)?.credentialsProvider?.allowDialog?.set(false)
  }
}