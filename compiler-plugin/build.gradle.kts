import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version ("1.8.22")
  kotlin("kapt") version ("1.8.22")
}

allprojects {
  repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.google.com")
    maven("https://plugins.gradle.org/m2/")
    google()
  }
}

group = "com.xebia"
version = "0.0.1"
val autoService = "1.1.0"

dependencies {
  compileOnly("com.google.auto.service:auto-service:$autoService")
  kapt("com.google.auto.service:auto-service:$autoService")
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.20")
  testImplementation("dev.zacsweers.kctfork:core:0.2.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.3")
  testImplementation(kotlin("reflect"))
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

//./gradlew clean :lib:compileKotlinJvm --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy="in-process" -Dkotlin.daemon.jvm.options="-Xdebug,-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=n"
