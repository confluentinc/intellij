package io.confluent.intellijplugin.ccloud.util

/**
 * Utility for loading test resource files.
 */
object ResourceLoader {
    /**
     * Loads a resource file from the classpath.
     * @param path Path relative to the test resources directory
     * @see <a href="file:test/resources/ccloud-resources-mock-responses/">test/resources/ccloud-resources-mock-responses/</a>
     * @return The contents of the resource file
     * @throws IllegalStateException if the resource is not found
     */
    fun loadResource(path: String): String {
        return this::class.java.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Resource not found: $path")
    }
}
