// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DialogTitleCapitalization")

package io.confluent.intellijplugin.aws.credentials.sso


object SsoSupport {
    /**
     * Shared disk cache for SSO for the IDE
     */
    val diskCache by lazy { DiskCache() }
}


