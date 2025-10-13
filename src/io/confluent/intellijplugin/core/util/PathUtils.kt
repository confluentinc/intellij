// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package io.confluent.intellijplugin.core.util

import com.intellij.execution.wsl.WslPath.Companion.parseWindowsUncPath
import com.intellij.openapi.util.SystemInfo
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

fun Path.inputStream(): InputStream = Files.newInputStream(this)

fun Path.touch() {
    this.createParentDirectories()
    try {
        Files.createFile(this)
    } catch (_: FileAlreadyExistsException) {
    }
}

fun Path.createParentDirectories() = Files.createDirectories(this.parent)
fun Path.exists() = Files.exists(this)
fun Path.readText(charset: Charset = Charsets.UTF_8) = toFile().readText(charset)
fun Path.writeText(text: String, charset: Charset = Charsets.UTF_8) = toFile().writeText(text, charset)
fun Path.filePermissions(permissions: Set<PosixFilePermission>) {
    // Comes from PosixFileAttributeView#name()
    if ("posix" in this.fileSystem.supportedFileAttributeViews()) {
        Files.setPosixFilePermissions(this, permissions)
    }
}

object PathUtils {
    fun toUnixPath(path: String): String {
        return if (SystemInfo.isWindows) {
            if (parseWindowsUncPath(path) != null) {
                return path
            }
            path.replace("\\\\".toRegex(), "/")
        } else {
            path
        }
    }
}
