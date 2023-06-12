plugins {
  kotlin("jvm") version ("1.8.20")
  kotlin("kapt") version ("1.8.20")
  id("java-gradle-plugin")
}

group = "com.xebia"
version = "1.0.0"

allprojects {
  repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.google.com")
    maven("https://plugins.gradle.org/m2/")
    google()
  }
}
dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.8.20")
}

gradlePlugin {
  plugins {
    create("simplePlugin") {
      id = "compiler.gradleplugin.asfuture" // users will do `apply plugin: "compiler.plugin.asfuture"`
      implementationClass = "com.xebia.gradle.AsFutureGradleSubPlugin" // entry-point class
    }
  }
}
