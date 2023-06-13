package com.xebia.services

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import java.io.FilenameFilter
import java.io.File

class PluginAnnotationsConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {

  companion object {
    private const val ANNOTATIONS_JAR_DIR = "../annotation/build/libs/"
    private val ANNOTATIONS_JAR_FILTER = FilenameFilter { _, name ->
      name.startsWith("annotation-jvm") && name.endsWith(".jar")
    }
    private val failMessage = {
      "Jar with annotations does not exist. Please run :annotation:jar"
    }
    fun jar(testServices: TestServices) =
      File(ANNOTATIONS_JAR_DIR).listFiles(ANNOTATIONS_JAR_FILTER)?.firstOrNull()
        ?: testServices.assertions.fail(failMessage)
  }

  override fun configureCompilerConfiguration(
    configuration: CompilerConfiguration,
    module: TestModule
  ) {
    val libDir = File(ANNOTATIONS_JAR_DIR)
    testServices.assertions.assertTrue(libDir.exists() && libDir.isDirectory, failMessage)
    val jar = jar(testServices)
    configuration.addJvmClasspathRoot(jar)
  }

}
