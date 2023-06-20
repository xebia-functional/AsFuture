plugins {
  kotlin("jvm") version "1.8.22"
  kotlin("kapt") version "1.8.22"
  `java-gradle-plugin`
  id("io.arrow-kt.arrow-gradle-config-publish") version "0.12.0-rc.3"
}

group = "com.xebia"
version = "1.0.0"

allprojects {
  repositories {
    mavenCentral()
    maven("https://maven.google.com")
    maven("https://plugins.gradle.org/m2/")
  }
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.8.22")
}

gradlePlugin {
  plugins {
    create("simplePlugin") {
      id = "compiler.gradleplugin.asfuture" // users will do `apply plugin: "compiler.plugin.asfuture"`
      implementationClass = "com.xebia.gradle.AsFutureGradleSubPlugin" // entry-point class
    }
  }
}

tasks.withType<AbstractPublishToMaven> {
  dependsOn(tasks.withType<Sign>())
}
