// Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package io.confluent.intellijplugin.aws.credentials.profiles

import com.intellij.util.text.nullize
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import software.amazon.awssdk.profiles.Profile
import software.amazon.awssdk.profiles.ProfileProperty

fun Profile.traverseCredentialChain(profiles: Map<String, Profile>): Sequence<Profile> = sequence {
    val profileChain = linkedSetOf<String>()
    var currentProfile = this@traverseCredentialChain

    yield(currentProfile)

    while (currentProfile.propertyExists(ProfileProperty.ROLE_ARN)) {
        val currentProfileName = currentProfile.name()
        if (!profileChain.add(currentProfileName)) {
            val chain = profileChain.joinToString("->", postfix = "->$currentProfileName")
            throw IllegalArgumentException(
                KafkaMessagesBundle.message(
                    "credentials.profile.circular_profiles",
                    name(),
                    chain
                )
            )
        }

        val sourceProfile = currentProfile.property(ProfileProperty.SOURCE_PROFILE)
        val credentialSource = currentProfile.property(ProfileProperty.CREDENTIAL_SOURCE)

        if (sourceProfile.isPresent && credentialSource.isPresent) {
            throw IllegalArgumentException(
                KafkaMessagesBundle.message(
                    "credentials.profile.assume_role.duplicate_source",
                    currentProfileName
                )
            )
        }

        if (sourceProfile.isPresent) {
            val sourceProfileName = sourceProfile.get()
            currentProfile = profiles[sourceProfileName]
                ?: throw IllegalArgumentException(
                    KafkaMessagesBundle.message(
                        "credentials.profile.source_profile_not_found",
                        currentProfileName,
                        sourceProfileName
                    )
                )

            yield(currentProfile)
        } else if (credentialSource.isPresent) {
            return@sequence
        } else {
            throw IllegalArgumentException(
                KafkaMessagesBundle.message(
                    "credentials.profile.assume_role.missing_source",
                    currentProfileName
                )
            )
        }
    }
}

fun Profile.propertyExists(propertyName: String): Boolean = this.property(propertyName).isPresent

fun Profile.requiredProperty(propertyName: String): String = this.property(propertyName)
    .filter { it.nullize() != null }
    .orElseThrow {
        IllegalArgumentException(
            KafkaMessagesBundle.message(
                "credentials.profile.missing_property",
                this.name(),
                propertyName
            )
        )
    }
