import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("io.arrow-kt.arrow-gradle-config-publish") version "0.12.0-rc.3"
}

group = "com.xebia"
version = "0.0.1"
val autoService = "1.1.1"

sourceSets {
  test {
    java.srcDirs("src/test-gen")
  }
}

dependencies {
  compileOnly("com.google.auto.service:auto-service:$autoService")
  compileOnly("org.jetbrains.kotlin:kotlin-compiler:1.8.22")
  kapt("com.google.auto.service:auto-service:$autoService")
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.8.22")
  testImplementation("dev.zacsweers.kctfork:core:0.2.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("com.google.truth:truth:1.1.5")
  testImplementation(kotlin("reflect"))
  testRuntimeOnly("org.jetbrains.kotlin:kotlin-test:1.8.22")
  testRuntimeOnly("org.jetbrains.kotlin:kotlin-script-runtime:1.8.22")
  testRuntimeOnly("org.jetbrains.kotlin:kotlin-annotations-jvm:1.8.22")

  testCompileOnly("org.jetbrains.kotlin:kotlin-compiler:1.8.22")
  testImplementation("org.jetbrains.kotlin:kotlin-compiler:1.8.22")
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:1.8.22")
  testImplementation(platform("org.junit:junit-bom:5.9.1"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.platform:junit-platform-commons")
  testImplementation("org.junit.platform:junit-platform-launcher")
  testImplementation("org.junit.platform:junit-platform-runner")
  testImplementation("org.junit.platform:junit-platform-suite-api")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
  }
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "1.8"
}

tasks.create<JavaExec>("generateTests") {
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("com.xebia.GenerateKotlinCompilerTestKt")
}

tasks.test {
  testLogging { showStandardStreams = true }

  useJUnitPlatform()
  doFirst {
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
    setLibraryProperty("coroutines.lib", "kotlinx-coroutines-core-jvm")
  }

  dependsOn("generateTests")
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
  val path =
    project.configurations.testRuntimeClasspath
      .get()
      .files
      .find {
        val matches = """$jarName-\d.*jar""".toRegex().matches(it.name)
        matches
      }
      ?.absolutePath
      ?: return
  systemProperty(propName, path)
}

tasks.withType<AbstractPublishToMaven> {
  dependsOn(tasks.withType<Sign>())
}
