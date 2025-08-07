buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://packages.confluent.io/maven/")
  }

  pluginManagement {
    repositories {
      maven("https://oss.sonatype.org/content/repositories/snapshots/")
      gradlePluginPortal()
    }
    plugins {
      id("java")
      id("org.jetbrains.kotlin.jvm") version "2.1.0"
      id("org.jetbrains.intellij.platform") version "2.6.0"
      id("org.jetbrains.intellij.platform.settings") version "2.6.0"
    }
  }
}