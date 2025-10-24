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
        intellijIdeaUltimate("2025.2", useInstaller = true)

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

    implementation(libs.glue.schema.registry.serde)
    implementation(libs.generex)
    implementation("io.sentry:sentry:8.23.0")

    // tests
    testImplementation(libs.kotlinx.metadata.jvm)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit4)
}

configurations.all { exclude(group = "org.slf4j", module = "slf4j-api") }

sourceSets {
    main {
        java.srcDirs(listOf("src", "gen"))
        kotlin.srcDirs(listOf("src", "gen", layout.buildDirectory.dir("generated/sources/sentryconfig/kotlin")))
        resources.srcDirs(listOf("resources"))
    }
    test {
        java.srcDirs(listOf("test"))
        kotlin.srcDirs(listOf("test"))
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateSentryConfig)
}

// Ensure all Sentry plugin tasks run after custom config generation
afterEvaluate {
    tasks.filter { 
        (it.name.startsWith("sentry") || it.name.contains("Sentry")) && 
        it.name != "generateSentryConfig" 
    }.forEach { sentryTask ->
        sentryTask.mustRunAfter(generateSentryConfig)
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
        useJUnit()
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