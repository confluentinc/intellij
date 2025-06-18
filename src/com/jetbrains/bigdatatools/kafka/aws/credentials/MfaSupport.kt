// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.jetbrains.bigdatatools.kafka.aws.credentials

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.jetbrains.bigdatatools.kafka.core.rfs.exception.RfsAuthRequiredError
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.SwingUtilities

fun promptForMfaToken(name: String, mfaSerial: String, allowMfaDialog: Boolean): String {
  if (!allowMfaDialog)
    throw RfsAuthRequiredError("MFA challenge is required")
  var res: String? = null
  SwingUtilities.invokeAndWait {
    res = Messages.showInputDialog(
      KafkaMessagesBundle.message("credentials.mfa.message", mfaSerial),
      KafkaMessagesBundle.message("credentials.mfa.title", name),
      null
    )
  }
  return res ?: throw ProcessCanceledException(IllegalStateException("MFA challenge is required"))
}