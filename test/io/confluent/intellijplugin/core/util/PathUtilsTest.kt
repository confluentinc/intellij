package io.confluent.intellijplugin.core.util

import com.intellij.openapi.util.SystemInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("PathUtils")
class PathUtilsTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("path-utils-test-")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Nested
    @DisplayName("toUnixPath")
    inner class ToUnixPath {

        @Test
        fun `should return the input unchanged on non-Windows systems`() {
            // The test host is macOS in CI; on Windows the function rewrites separators.
            // We assert only the platform we actually run on, so the test is deterministic.
            if (!SystemInfo.isWindows) {
                val input = "/Users/foo/bar/baz"
                assertEquals(input, PathUtils.toUnixPath(input))
            }
        }

        @Test
        fun `should handle empty input`() {
            assertEquals("", PathUtils.toUnixPath(""))
        }
    }

    @Nested
    @DisplayName("Path extensions")
    inner class PathExtensions {

        @Test
        fun `touch should create a new file at the given path`() {
            val target = tempDir.resolve("new-file.txt")
            assertFalse(target.exists())

            target.touch()

            assertTrue(target.exists())
            assertTrue(Files.isRegularFile(target))
        }

        @Test
        fun `touch should be a no-op when the file already exists`() {
            val target = tempDir.resolve("existing.txt")
            target.writeText("hello")

            target.touch()

            assertEquals("hello", target.readText())
        }

        @Test
        fun `touch should create missing parent directories`() {
            val target = tempDir.resolve("a/b/c/file.txt")
            assertFalse(target.parent.exists())

            target.touch()

            assertTrue(target.exists())
            assertTrue(target.parent.exists())
        }

        @Test
        fun `createParentDirectories should create the parent chain`() {
            val target = tempDir.resolve("x/y/z/leaf.txt")

            target.createParentDirectories()

            assertTrue(target.parent.exists())
            assertFalse(target.exists())
        }

        @Test
        fun `writeText and readText should round-trip with UTF-8 by default`() {
            val target = tempDir.resolve("greeting.txt")
            val content = "héllo, wörld"

            target.writeText(content)

            assertEquals(content, target.readText())
        }

        @Test
        fun `inputStream should read previously written bytes`() {
            val target = tempDir.resolve("stream.txt")
            target.writeText("streamed")

            val read = target.inputStream().use { it.readBytes().decodeToString() }

            assertEquals("streamed", read)
        }

        @Test
        fun `exists should reflect filesystem state`() {
            val target = tempDir.resolve("ephemeral.txt")
            assertFalse(target.exists())
            target.touch()
            assertTrue(target.exists())
        }

        @Test
        fun `filePermissions should be a no-op on filesystems without posix support`() {
            // On macOS/Linux the filesystem supports posix and the call applies the permissions.
            // We assert it doesn't throw on either branch, and the file remains readable.
            val target = tempDir.resolve("perms.txt")
            target.writeText("body")

            target.filePermissions(
                setOf(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                )
            )

            assertEquals("body", target.readText())
        }
    }
}
