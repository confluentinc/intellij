import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

apply(from = "../../../contrib/contrib-configuration/common.gradle.kts")

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm")
  id("org.jetbrains.intellij.platform")
}

repositories {
  intellijPlatform {
    defaultRepositories()
    snapshots()
  }
  maven {
    url = uri("https://packages.confluent.io/maven/")
  }

  mavenCentral()
}

intellijPlatform {
  pluginConfiguration {
    name = "Kafka"
  }
}

sourceSets {
  main {
    java {
      setSrcDirs(listOf("src", "gen"))
    }
    resources {
      setSrcDirs(listOf("resources"))
    }
  }
  test {
    java {
      setSrcDirs(listOf("test"))
    }
  }
}

dependencies {
  intellijPlatform {
    jetbrainsRuntime()
    intellijIdeaUltimate("2025.1.2", useInstaller = true)

    bundledPlugin("com.intellij.modules.json")
    bundledPlugin("com.intellij.microservices.jvm")
    bundledPlugin("com.intellij.spring")
    bundledPlugin("com.intellij.spring.boot")
    bundledPlugin("intellij.charts")

    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Bundled)
    testFramework(TestFrameworkType.JUnit5)
  }
  implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

  implementation("org.apache.kafka:kafka-clients:4.0.0")

  implementation("io.confluent:kafka-avro-serializer:7.2.0")
  implementation("io.confluent:kafka-json-schema-serializer:7.2.0")
  implementation("io.confluent:kafka-protobuf-serializer:7.2.0")
  implementation("io.confluent:kafka-schema-registry-client:7.2.0")

  implementation("software.amazon.awssdk:apache-client:2.20.158")
  implementation("software.amazon.awssdk:sso:2.20.158")
  implementation("software.amazon.awssdk:sts:2.20.158")
  implementation("software.amazon.awssdk:ssooidc:2.20.158")

  implementation("software.amazon.glue:schema-registry-serde:1.1.15")

  implementation("com.github.mifmif:generex:1.0.2")

  // Tests
  testImplementation("org.jetbrains.kotlinx", "kotlinx-metadata-jvm", "0.9.0")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")

  testImplementation("junit:junit:4.13.2")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(ext("kotlin.jvmTarget")))
    @Suppress("UNCHECKED_CAST")
    freeCompilerArgs.addAll(rootProject.extensions["kotlin.freeCompilerArgs"] as List<String>)
  }
}

tasks {
  java {
    sourceCompatibility = JavaVersion.toVersion(ext("java.sourceCompatibility"))
    targetCompatibility = JavaVersion.toVersion(ext("java.targetCompatibility"))
  }
  wrapper {
    gradleVersion = ext("gradle.version")
  }
  test {
    useJUnitPlatform()
  }
}

fun ext(name: String): String =
  rootProject.extensions[name] as? String ?: error("Property `$name` is not defined")