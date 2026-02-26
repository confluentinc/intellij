package io.confluent.intellijplugin.scaffold.service

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ScaffoldProjectServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Nested
    @DisplayName("extractZip")
    inner class ExtractZip {

        @Test
        fun `extracts files and directories from ZIP`() {
            val zipBytes = createZip(
                "project/build.gradle" to "apply plugin: 'java'",
                "project/src/Main.java" to "public class Main {}"
            )

            val outputDir = tempDir.resolve("output")
            ScaffoldProjectService.extractZip(zipBytes, outputDir)

            assertTrue(Files.exists(outputDir.resolve("project/build.gradle")))
            assertTrue(Files.exists(outputDir.resolve("project/src/Main.java")))
            assertEquals("apply plugin: 'java'", Files.readString(outputDir.resolve("project/build.gradle")))
            assertEquals("public class Main {}", Files.readString(outputDir.resolve("project/src/Main.java")))
        }

        @Test
        fun `creates output directory if it does not exist`() {
            val zipBytes = createZip("file.txt" to "hello")

            val outputDir = tempDir.resolve("nonexistent/nested/output")
            assertFalse(Files.exists(outputDir))

            ScaffoldProjectService.extractZip(zipBytes, outputDir)

            assertTrue(Files.exists(outputDir))
            assertTrue(Files.exists(outputDir.resolve("file.txt")))
        }

        @Test
        fun `handles empty ZIP`() {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { /* empty */ }
            val zipBytes = baos.toByteArray()

            val outputDir = tempDir.resolve("empty-output")
            ScaffoldProjectService.extractZip(zipBytes, outputDir)

            assertTrue(Files.exists(outputDir))
            assertEquals(0, Files.list(outputDir).count())
        }

        @Test
        fun `creates explicit directory entries`() {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("mydir/"))
                zos.closeEntry()
            }
            val zipBytes = baos.toByteArray()

            val outputDir = tempDir.resolve("dir-output")
            ScaffoldProjectService.extractZip(zipBytes, outputDir)

            assertTrue(Files.isDirectory(outputDir.resolve("mydir")))
        }

        @Test
        fun `rejects zip slip attack entries`() {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("../../../etc/passwd"))
                zos.write("malicious".toByteArray())
                zos.closeEntry()
            }
            val zipBytes = baos.toByteArray()

            val outputDir = tempDir.resolve("secure-output")
            assertThrows(SecurityException::class.java) {
                ScaffoldProjectService.extractZip(zipBytes, outputDir)
            }
        }
    }

    private fun createZip(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
