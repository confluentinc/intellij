package io.confluent.intellijplugin.util

import io.confluent.intellijplugin.core.ui.components.ConnectionProperty
import org.apache.kafka.clients.admin.AdminClientConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader

/**
 * In the IDE, these calls run on the EDT, whose context classloader is the IDE's and cannot see the plugin's
 * Kafka jars. kafka-clients resolves class-typed config defaults (e.g. `sasl.oauthbearer.jwt.retriever.class`,
 * added in 4.1) through the context classloader while initializing `AdminClientConfig`/`ConsumerConfig`, so
 * calling them without switching classloaders breaks the config class for the rest of the session.
 *
 * The test JVM's context classloader sees everything, so each test loads the plugin and Kafka classes into a
 * fresh, isolated classloader (fresh static initialization) and runs with a context classloader that cannot
 * see Kafka, as on the EDT.
 */
class KafkaPropertiesUtilsClassLoaderTest {

    @Test
    fun `admin property descriptions load when the context classloader cannot see Kafka`() {
        val result = callInIsolation("getAdminPropertiesDescriptions") as List<*>
        assertFalse(result.isEmpty())
    }

    @Test
    fun `consumer config keys load when the context classloader cannot see Kafka`() {
        val result = callInIsolation("getConsumerConfigKeys") as Map<*, *>
        assertFalse(result.isEmpty())
    }

    private fun callInIsolation(methodName: String): Any? {
        val classpath = listOf(
            KafkaPropertiesUtils::class.java,
            ConnectionProperty::class.java,
            AdminClientConfig::class.java,
            Unit::class.java,
            LoggerFactory::class.java,
        ).map(::classpathRootOf).distinct().toTypedArray()

        val platform = ClassLoader.getPlatformClassLoader()
        URLClassLoader(classpath, platform).use { isolated ->
            val utils = isolated.loadClass(KafkaPropertiesUtils::class.java.name)
            val instance = utils.getField("INSTANCE").get(null)
            val thread = Thread.currentThread()
            val previous = thread.contextClassLoader
            thread.contextClassLoader = platform
            try {
                return utils.getMethod(methodName).invoke(instance)
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            } finally {
                thread.contextClassLoader = previous
            }
        }
    }

    // The IDE test classloader does not always set a CodeSource, so derive the jar/directory from the .class URL.
    private fun classpathRootOf(type: Class<*>): URL {
        val resource = type.name.replace('.', '/') + ".class"
        val url = requireNotNull(type.classLoader.getResource(resource)) { "No class file for ${type.name}" }
        return when (url.protocol) {
            "jar" -> URI(url.path.substringBefore("!/")).toURL()
            "file" -> URI(url.toString().removeSuffix(resource)).toURL()
            else -> error("Unsupported class location $url")
        }
    }
}
