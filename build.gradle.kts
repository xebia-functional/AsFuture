buildscript {
  repositories {
    mavenLocal()
    google()
    mavenCentral()
  }

  dependencies {
    classpath("com.xebia:gradle-plugin:1.0.0")
  }
}
plugins {
  id("org.jetbrains.kotlin.multiplatform") version "1.8.20" apply false
}

apply(plugin = "compiler.gradleplugin.asfuture")

System.setProperty("kotlin.compiler.execution.strategy", "in-process") // For debugging

allprojects {
  repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.google.com")
    maven("https://plugins.gradle.org/m2/")
    google()
  }
}
