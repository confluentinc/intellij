// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0
package io.confluent.intellijplugin.core.util

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.codeStyle.NameUtil

object StringUtils {
  /** Returns human readable string from camel cased name. "timeToLoad" -> "Time to load". */
  fun camelCaseToReadableString(column: String): @NlsSafe String {
    val name = NameUtil.nameToWords(column).joinToString(separator = " ")
    return if (name.isEmpty()) {
      name
    }
    else {
      name.substring(0, 1).uppercase() + name.substring(1)
    }
  }
}