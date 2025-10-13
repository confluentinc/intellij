import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

rootProject.extensions.add("gradle.version", "8.14.3")
rootProject.extensions.add("kotlin.jvmTarget", "21")
rootProject.extensions.add("kotlin.freeCompilerArgs", listOf("-Xjvm-default=all"))
rootProject.extensions.add("java.sourceCompatibility", "21")
rootProject.extensions.add("java.targetCompatibility", "21")

buildscript {
  repositories {
    mavenCentral()
  }
}

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("io.sentry.jvm.gradle") version "5.12.1"
}

sentry {
    // Generates a JVM (Java, Kotlin, etc.) source bundle and uploads your source code to Sentry.
    // This enables source context, allowing you to see your source
    // code as part of your stack traces in Sentry.
    includeSourceContext = true

    org.set("confluent")
    projectName.set("intellij-plugin")
    authToken.set(System.getenv("SENTRY_AUTH_TOKEN"))
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
        kotlin.srcDirs(listOf("src", "gen"))
        resources.srcDirs(listOf("resources"))
    }
    test {
        java.srcDirs(listOf("test"))
        kotlin.srcDirs(listOf("test"))
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
}

fun ext(name: String): String =
    rootProject.extensions[name] as? String ?: error("Property `$name` is not defined")