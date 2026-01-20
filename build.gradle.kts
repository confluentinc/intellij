import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

rootProject.extensions.add("gradle.version", "8.14.3")
rootProject.extensions.add("kotlin.jvmTarget", "21")
rootProject.extensions.add("kotlin.freeCompilerArgs", listOf("-Xjvm-default=all"))
rootProject.extensions.add("java.sourceCompatibility", "21")
rootProject.extensions.add("java.targetCompatibility", "21")

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("io.sentry.jvm.gradle") version "5.12.1"
}

sentry {
    val sentryAuthToken = System.getenv("SENTRY_AUTH_TOKEN")
    includeSourceContext = !sentryAuthToken.isNullOrEmpty()
    org.set("confluent")
    projectName.set("intellij-plugin")
    authToken.set(sentryAuthToken)
}

// Generate SentryConfig.kt with embedded DSN at build time
val generateSentryConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/sentryconfig/kotlin")
    val sentryDsn = System.getenv("SENTRY_DSN") ?: ""

    outputs.dir(outputDir)
    inputs.property("sentryDsn", sentryDsn)

    doLast {
        val configFile = outputDir.get().asFile.resolve("io/confluent/intellijplugin/telemetry/SentryConfig.kt")
        configFile.parentFile.mkdirs()
        configFile.writeText("""
            package io.confluent.intellijplugin.telemetry

            /** Sentry configuration embedded at build time from SENTRY_DSN env var. */
            object SentryConfig {
                const val DSN = "$sentryDsn"
                val isConfigured = DSN.isNotBlank()
            }

        """.trimIndent())
    }
}

// Generate SegmentConfig.kt with embedded write key at build time
val generateSegmentConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/segmentconfig/kotlin")
    val segmentWriteKey = System.getenv("SEGMENT_WRITE_KEY") ?: ""

    outputs.dir(outputDir)
    inputs.property("segmentWriteKey", segmentWriteKey)

    doLast {
        val configFile = outputDir.get().asFile.resolve("io/confluent/intellijplugin/telemetry/SegmentConfig.kt")
        configFile.parentFile.mkdirs()
        configFile.writeText("""
            package io.confluent.intellijplugin.telemetry

            /** Segment configuration embedded at build time from SEGMENT_WRITE_KEY env var. */
            object SegmentConfig {
                const val WRITE_KEY = "$segmentWriteKey"
            }

        """.trimIndent())
    }
}

repositories {
    intellijPlatform {
        defaultRepositories()
        snapshots()
    }

    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://packages.confluent.io/maven/")
}

intellijPlatform {
    pluginConfiguration {
        name = "Kafka"
    }
}

dependencies {
    intellijPlatform {
        jetbrainsRuntime()
        intellijIdea("2025.3"){useInstaller.set(true)}

        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("com.intellij.microservices.jvm")
        bundledPlugin("com.intellij.spring")
        bundledPlugin("com.intellij.spring.boot")
        bundledModule("intellij.charts")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.JUnit5)
    }
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kafka.clients)

    listOf(
        libs.kafka.avro.serializer,
        libs.kafka.json.schema.serializer,
        libs.kafka.protobuf.serializer,
        libs.kafka.schema.registry.client
    ).forEach {
        implementation(it) {
            exclude("org.apache.kafka", "kafka-clients")
        }
    }

    implementation(libs.aws.apache.client)
    implementation(libs.aws.sso)
    implementation(libs.aws.sts)
    implementation(libs.aws.ssooidc)
    implementation(libs.aws.mskiamauth)
    implementation("com.segment.analytics.java:analytics:3.5.2")

    implementation(libs.glue.schema.registry.serde)
    implementation(libs.generex)
    implementation("io.sentry:sentry:8.23.0")

    // tests
    testImplementation(libs.kotlin.metadata.jvm)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.wiremock)
    // JUnit 4 runtime required due to IJPL-159134: JUnit5 Test Framework refers to JUnit4 classes
    // See: https://youtrack.jetbrains.com/issue/IJPL-159134
    testRuntimeOnly(libs.junit4)
}

configurations.all { exclude(group = "org.slf4j", module = "slf4j-api") }

sourceSets {
    main {
        java.srcDirs(listOf("src", "gen"))
        kotlin.srcDirs(listOf(
            "src",
            "gen",
            layout.buildDirectory.dir("generated/sources/sentryconfig/kotlin"),
            layout.buildDirectory.dir("generated/sources/segmentconfig/kotlin")
        ))
        resources.srcDirs(listOf("resources"))
    }
    test {
        java.srcDirs(listOf("test"))
        kotlin.srcDirs(listOf("test"))
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateSentryConfig, generateSegmentConfig)
}

// Ensure all Sentry plugin tasks run after custom config generation
afterEvaluate {
    tasks.filter {
        (it.name.startsWith("sentry") || it.name.contains("Sentry")) &&
        it.name != "generateSentryConfig"
    }.forEach { sentryTask ->
        sentryTask.mustRunAfter(generateSentryConfig, generateSegmentConfig)
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(ext("java.sourceCompatibility"))
    targetCompatibility = JavaVersion.toVersion(ext("java.targetCompatibility"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(ext("kotlin.jvmTarget")))
        @Suppress("UNCHECKED_CAST")
        freeCompilerArgs.addAll(rootProject.extensions["kotlin.freeCompilerArgs"] as List<String>)
    }
}

tasks {
    wrapper {
        gradleVersion = ext("gradle.version")
    }

    test {
        useJUnitPlatform()
        systemProperty("ccloud.callback-port", "26639")
        System.getProperty("ccloud.env")?.let { systemProperty("ccloud.env", it) }
    }

    runIde {
        // Pass system properties from gradle.properties or use system property flag with -D flag @see CCloudOAuthConfig
        System.getProperty("ccloud.callback-port")?.let { systemProperty("ccloud.callback-port", it) }
        System.getProperty("ccloud.env")?.let { systemProperty("ccloud.env", it) }
        // Pass Segment write key for dev telemetry testing: ./gradlew runIde -Dconfluent.intellijplugin.segment.writeKey=your_key
        System.getProperty("confluent.intellijplugin.segment.writeKey")?.let { systemProperty("confluent.intellijplugin.segment.writeKey", it) }
    }

    patchPluginXml {
        val releaseName = System.getenv("RELEASE_NAME")
        // if RELEASE_NAME is set (e.g. from a release job in CI), patch it in plugin.xml
        // so the resulting plugin zip has the correct version number when installed
        if (!releaseName.isNullOrEmpty()) {
            version = releaseName.removePrefix("v")
        }
    }

    // Skip Sentry tasks when auth token is missing
    if (System.getenv("SENTRY_AUTH_TOKEN").isNullOrEmpty()) {
        // Disable all Sentry Gradle plugin tasks that require auth token
        listOf("sentryBundleSourcesJava", "generateSentryBundleIdJava").forEach { taskName ->
            try {
                named(taskName) {
                    enabled = false
                }
            } catch (e: UnknownTaskException) {
                // Task doesn't exist, ignore
            }
        }
    }
}

fun ext(name: String): String =
    rootProject.extensions[name] as? String ?: error("Property `$name` is not defined")
