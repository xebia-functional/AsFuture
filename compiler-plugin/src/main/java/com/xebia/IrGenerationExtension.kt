package com.xebia

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.platform.jvm.isJvm

internal class IrGenerationExtension(
  private val messageCollector: MessageCollector,
  private val annotationClassId: ClassId,
  private val coroutineScope: ClassId
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val transformer = IrVisitor(pluginContext, annotationClassId, coroutineScope, messageCollector)
    moduleFragment.transform(transformer, null)
  }
}

private class IrVisitor(
  val pluginContext: IrPluginContext,
  val annotationClassId: ClassId,
  val coroutineScope: ClassId,
  val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

  override fun visitFunctionNew(declaration: IrFunction): IrStatement {
    if (!declaration.hasAnnotation(annotationClassId.asSingleFqName())) return super.visitFunctionNew(declaration)

    val parent: IrClass = if (declaration.parent is IrClass) declaration.parent as IrClass
    else {
      declaration.reportError("@AsFuture needs to be inside a class.")
      return super.visitFunctionNew(declaration)
    }

    if (!parent.allSuperInterfaces().any { it.classId?.asSingleFqName() == coroutineScope.asSingleFqName() }) {
      parent.reportError("Surrounding class of @AsFuture needs to extend CoroutineScope.")
      return super.visitFunctionNew(declaration)
    }

    if (pluginContext.platform?.isJvm() == true) {
      messageCollector.report(
        CompilerMessageSeverity.WARNING,
        "visitFunctionNew: ${declaration.name} - platform: ${pluginContext.platform}"
      )
    }

    return super.visitFunctionNew(declaration)
  }

  private fun IrDeclaration.reportError(message: String) {
    val location = file.locationOf(this)
    messageCollector.report(CompilerMessageSeverity.ERROR, "$LOG_PREFIX $message", location)
  }

  /** Finds the line and column of [irElement] within this file. */
  private fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
    val sourceRangeInfo =
      fileEntry.getSourceRangeInfo(
        beginOffset = irElement?.startOffset ?: SYNTHETIC_OFFSET,
        endOffset = irElement?.endOffset ?: SYNTHETIC_OFFSET
      )
    return CompilerMessageLocationWithRange.create(
      path = sourceRangeInfo.filePath,
      lineStart = sourceRangeInfo.startLineNumber + 1,
      columnStart = sourceRangeInfo.startColumnNumber + 1,
      lineEnd = sourceRangeInfo.endLineNumber + 1,
      columnEnd = sourceRangeInfo.endColumnNumber + 1,
      lineContent = null
    )!!
  }
}
