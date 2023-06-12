package com.xebia

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addExtensionReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.types.Variance

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

  val futureCallableId = CallableId(FqName("kotlinx.coroutines.future"), Name.identifier("future"))
  val coroutineScopeType: IrClassSymbol = pluginContext.referenceClass(coroutineScope)
    ?: error("Internal error: Function $coroutineScope not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-core.")

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
      // This declaration is only available on JVM
      val futureFn = pluginContext.referenceFunctions(futureCallableId).singleOrNull()
        ?: error("Internal error: Function $futureCallableId not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-jdk8.")

      val futureClass: IrClassSymbol =
        pluginContext.referenceClass(ClassId.fromString("java.util.concurrent.CompletableFuture"))
          ?: error("Internal error: Function $coroutineScope not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-core.")

      val futureSimpleType = IrSimpleTypeImpl(
        futureClass,
        SimpleTypeNullability.DEFINITELY_NOT_NULL,
        listOf(makeTypeProjection(declaration.returnType, Variance.INVARIANT)),
        emptyList()
      )

      messageCollector.report(
        CompilerMessageSeverity.WARNING,
        "(${pluginContext.platform}) ~> ${declaration.name}."
      )

      val f: IrSimpleFunction = parent.addFunction {
        name = Name.identifier("${declaration.name}Future")
        origin = AsFutureOrigin
      }.apply {
//        copyAttributes(declaration as IrAttributeContainer)
//        copyParameterDeclarationsFrom(declaration)

        annotations += listOf(/* Add JvmName & Deprecated annotation */)
//        extensionReceiverParameter = declaration.extensionReceiverParameter?.copyTo(this)
//        dispatchReceiverParameter = declaration.dispatchReceiverParameter?.copyTo(this)

        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
          val lambda = irSuspendLambda(declaration, declaration.returnType) { +irCall(declaration) }
          +irReturn(
            irCall(futureFn).apply {
              putTypeArgument(0, declaration.returnType)
//                putValueArgument(0, irGet(parent.thisReceiver!!))
              putValueArgument(2, lambda)
            }
          )
        }

        returnType = makeTypeProjection(futureSimpleType.type, Variance.INVARIANT).type
      }
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

  fun irSuspendLambda(
    parent: IrDeclarationParent,
    returnType: IrType,
    content: IrBlockBodyBuilder.(IrSimpleFunction) -> Unit
  ): IrFunctionExpression {
    val scope = IrSimpleTypeImpl(
      coroutineScopeType,
      SimpleTypeNullability.DEFINITELY_NOT_NULL,
      emptyList(),
      emptyList()
    )
    val lambda = pluginContext.irFactory.buildFun {
      startOffset = SYNTHETIC_OFFSET
      endOffset = SYNTHETIC_OFFSET
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
      name = Name.special("<anonymous>")
      visibility = DescriptorVisibilities.LOCAL
      isSuspend = true
      this.returnType = returnType
    }.apply {
      this.parent = parent
      addExtensionReceiver(scope)

      body = DeclarationIrBuilder(pluginContext, this.symbol, SYNTHETIC_OFFSET, SYNTHETIC_OFFSET)
        .irBlockBody { content(this@apply) }
    }
    return IrFunctionExpressionImpl(
      startOffset = SYNTHETIC_OFFSET,
      endOffset = SYNTHETIC_OFFSET,
      type = pluginContext.irBuiltIns.suspendFunctionN(0).typeWith(returnType),
      origin = IrStatementOrigin.LAMBDA,
      function = lambda
    )
  }
}