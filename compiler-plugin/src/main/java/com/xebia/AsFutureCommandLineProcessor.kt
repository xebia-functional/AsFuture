package com.xebia

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@ExperimentalCompilerApi
@AutoService(CommandLineProcessor::class)
class AsFutureCommandLineProcessor : CommandLineProcessor {

  override val pluginId: String = "asFuturePlugin"

  override val pluginOptions: Collection<CliOption> = listOf(
    CliOption(
      optionName = "enabled",
      valueDescription = "<true|false>",
      description = "whether to enable the plugin or not"
    )
  )

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration
  ) = when (option.optionName) {
    "enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
    else -> configuration.put(KEY_ENABLED, true)
  }
}

val KEY_ENABLED = CompilerConfigurationKey<Boolean>("whether the plugin is enabled")
