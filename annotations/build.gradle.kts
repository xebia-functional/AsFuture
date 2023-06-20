plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.8.20"
    id("io.arrow-kt.arrow-gradle-config-publish") version "0.12.0-rc.3"
}

group = "com.xebia"
version = "1.0.0"

kotlin {
    jvm()
    linuxX64("linux")
    js { nodejs() }
}

tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.withType<Sign>())
}
