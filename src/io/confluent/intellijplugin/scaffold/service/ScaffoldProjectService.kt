package io.confluent.intellijplugin.scaffold.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import io.confluent.intellijplugin.scaffold.client.ScaffoldHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

@Service(Service.Level.PROJECT)
class ScaffoldProjectService(private val scope: CoroutineScope) {

    private val client = ScaffoldHttpClient()

    suspend fun generateProject(
        collectionName: String = "intellij",
        templateName: String,
        options: Map<String, String>,
        outputDirectory: Path
    ) {
        val zipBytes = client.applyTemplate(
            collectionName = collectionName,
            templateName = templateName,
            options = options
        )

        thisLogger().debug("Received ZIP (${zipBytes.size} bytes), extracting to $outputDirectory")

        withContext(Dispatchers.IO) {
            extractZip(zipBytes, outputDirectory)
        }

        thisLogger().debug("Project extracted to $outputDirectory")
    }

    suspend fun openGeneratedProject(projectPath: Path) {
        withContext(Dispatchers.EDT) {
            ProjectManager.getInstance().loadAndOpenProject(projectPath.toString())
        }
    }

    companion object {
        fun getInstance(project: Project) = project.service<ScaffoldProjectService>()

        fun extractZip(zipBytes: ByteArray, outputDirectory: Path) {
            Files.createDirectories(outputDirectory)

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryPath = outputDirectory.resolve(entry.name).normalize()

                    if (!entryPath.startsWith(outputDirectory)) {
                        throw SecurityException("Zip entry outside target directory: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        Files.createDirectories(entryPath)
                    } else {
                        Files.createDirectories(entryPath.parent)
                        Files.newOutputStream(entryPath).use { out ->
                            zis.copyTo(out)
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }
}
