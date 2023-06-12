package com.xebia.runners

import com.xebia.services.ExtensionRegistrarConfigurator
import com.xebia.services.PluginAnnotationsConfigurator
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmSdkRoots
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.impl.testConfiguration
import org.jetbrains.kotlin.test.initIdeaConfiguration
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.baseFirDiagnosticTestConfiguration
import org.jetbrains.kotlin.test.services.*
import org.jetbrains.kotlin.utils.PathUtil
import org.junit.jupiter.api.BeforeAll
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

abstract class BaseTestRunner : AbstractKotlinCompilerTest() {
  companion object {
    @BeforeAll
    @JvmStatic
    fun setUp() {
      initIdeaConfiguration()
    }
  }

  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
    return EnvironmentBasedStandardLibrariesPathProvider
  }
}

fun TestConfigurationBuilder.commonFirWithPluginFrontendConfiguration() {
  baseFirDiagnosticTestConfiguration()

  useCustomRuntimeClasspathProviders(
    ::CustomClassPathProvider
  )

  defaultDirectives {
    +FirDiagnosticsDirectives.ENABLE_PLUGIN_PHASES
    +FirDiagnosticsDirectives.FIR_DUMP
//    +PreludeAdditionalFilesDirectives.ANNOTATION_DIRECTIVE
//    +PreludeAdditionalFilesDirectives.IDENTITY_DIRECTIVE
  }

  globalDefaults {
    targetBackend = TargetBackend.JVM_IR
    targetPlatform = JvmPlatforms.defaultJvmPlatform
    dependencyKind = DependencyKind.Binary
  }

  useConfigurators(
    ::PluginAnnotationsConfigurator,
    ::ExtensionRegistrarConfigurator,
    ::CustomClassPathConfigurator
  )


  //useAdditionalSourceProviders(::PreludeProvider)
}

fun CustomClassPathConfigurator(testServices: TestServices): EnvironmentConfigurator =
  object : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
      configuration.put(JVMConfigurationKeys.NO_JDK, false)
      configuration.put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home")))
      configuration.addJvmSdkRoots(PathUtil.getJdkClassesRootsFromCurrentJre())
      configuration.addJvmClasspathRoot(File(System.getProperty("coroutines.lib")))
      super.configureCompilerConfiguration(configuration, module)
    }
  }

fun CustomClassPathProvider(testServices: TestServices): RuntimeClasspathProvider =
  object : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> {
      val jrt = PathUtil.getJdkClassesRootsFromCurrentJre()
      println("Adding JRE classpath roots: $jrt")
      return listOf(PluginAnnotationsConfigurator.jar(testServices)) +
       listOfNotNull(File(System.getProperty("coroutines.lib"))) +
        jrt
    }
  }
