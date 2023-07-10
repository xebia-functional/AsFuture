plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("kapt") version "1.8.22"
    `java-gradle-plugin`
    id("io.arrow-kt.arrow-gradle-config-publish") version "0.12.0-rc.3"
}

group = "com.xebia"
version = "0.0.1"

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
        create("asFuturePlugin") {
            id = "compiler.gradleplugin.asfuture" // users will do `apply plugin: "compiler.plugin.asfuture"`
            implementationClass = "com.xebia.gradle.AsFutureGradleSubPlugin" // entry-point class
            displayName = "AsFuture Gradle Plugin"
            description = "Generates Future based APIs for suspend common definitions through a Kotlin compiler plugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/xebia-functional/AsFuture"
    vcsUrl = "https://github.com/xebia-functional/AsFuture"

    description = "Generates Future based APIs for suspend common definitions through a Kotlin compiler plugin"

    (plugins) {
        "asFuturePlugin" {
            description = "Generates Future based APIs for suspend common definitions through a Kotlin compiler plugin"
            tags = setOf("kotlin", "java", "kmp")
            version = project.version.toString()
        }
    }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.withType<Sign>())
}
