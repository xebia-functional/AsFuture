package com.xebia.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

open class TestCompilerExtension {
  var enabled: Boolean = true
}

class AsFutureGradleSubPlugin : KotlinCompilerPluginSupportPlugin {

  companion object {
    const val SERIALIZATION_GROUP_NAME = "com.xebia"
    const val ARTIFACT_NAME = "compiler-plugin"
    const val VERSION_NUMBER = "0.0.1"
  }

  private var gradleExtension: TestCompilerExtension = TestCompilerExtension()
  override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
    gradleExtension = kotlinCompilation.target.project.extensions.findByType(TestCompilerExtension::class.java)
      ?: TestCompilerExtension()

    return kotlinCompilation.target.project.provider {
      val options = mutableListOf(SubpluginOption("enabled", gradleExtension.enabled.toString()))
      options
    }
  }

  override fun apply(target: Project) {
    target.extensions.create(
      "asFuture",
      TestCompilerExtension::class.java
    )
    super.apply(target)
  }

  override fun getCompilerPluginId(): String = "asFuturePlugin"

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    return true
  }

  override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
    groupId = SERIALIZATION_GROUP_NAME,
    artifactId = ARTIFACT_NAME,
    version = VERSION_NUMBER // remember to bump this version before any release!
  )
}
