plugins {
    id("org.jetbrains.kotlin.multiplatform") version "1.8.20"
}

group = "com.xebia"
version = "1.0.0"

kotlin {
    jvm()
    linuxX64("linux")
    js { nodejs() }
}

