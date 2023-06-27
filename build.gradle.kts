buildscript {
  repositories {
    mavenCentral()
  }
}

plugins {
  id("org.jetbrains.kotlin.multiplatform") version "1.8.22" apply false
  id("io.arrow-kt.arrow-gradle-config-nexus") version "0.12.0-rc.3"
}

System.setProperty("kotlin.compiler.execution.strategy", "in-process") // For debugging

allprojects {
  repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
  }
}

