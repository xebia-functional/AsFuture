package com.xebia

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.name.ClassId

internal const val LOG_PREFIX = "*** AsFuture (IR):"

@ExperimentalCompilerApi
@AutoService(CompilerPluginRegistrar::class)
class CommonComponentRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    registerExtensionsCommon(configuration)
  }
}

@OptIn(ExperimentalCompilerApi::class)
fun CompilerPluginRegistrar.ExtensionStorage.registerExtensionsCommon(configuration: CompilerConfiguration) {
  if (configuration[KEY_ENABLED] == false) return

  val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
  val asFutureAnnotation = ClassId.fromString("sample/AsFuture")
  val coroutineScope = ClassId.fromString("kotlinx/coroutines/CoroutineScope")

  // TODO implement Fir Checker for annotation
  //  FirExtensionRegistrarAdapter.registerExtension(FirRedactedExtensionRegistrar(messageCollector))
  IrGenerationExtension.registerExtension(
    IrGenerationExtension(
      messageCollector, asFutureAnnotation, coroutineScope
    )
  )
}