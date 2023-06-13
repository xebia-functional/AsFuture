plugins {
  id("org.jetbrains.kotlin.multiplatform") version "1.8.20"
  `maven-publish`
}

apply(plugin = "compiler.gradleplugin.asfuture")

group = "com.xebia"
version = "0.0.1"

configure<com.xebia.gradle.TestCompilerExtension> {
  enabled = true
}

kotlin {
  jvm()
  linuxX64("linux")
  // js { nodejs() }
  // ...
  sourceSets {
    val commonMain by getting {
      dependencies {
        compileOnly(project(":annotation"))
        api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
      }
    }
    val jvmMain by getting {
      dependencies {
        api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1")
      }
    }
  }
}
