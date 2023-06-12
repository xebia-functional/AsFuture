package com.xebia

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.addExtensionReceiver
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyParameterDeclarationsFrom
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.types.Variance
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

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
      val futureFn: IrSimpleFunctionSymbol = pluginContext.referenceFunctions(futureCallableId).singleOrNull()
        ?: error("Internal error: Function $futureCallableId not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-jdk8.")

      val futureClass: IrClassSymbol =
        pluginContext.referenceClass(classId<CompletableFuture<*>>())
          ?: error("Internal error: Function \"java/util/concurrent/CompletableFuture\" not found. Please include the jdk8 module.")

      val futureSimpleType = IrSimpleTypeImpl(
        futureClass,
        SimpleTypeNullability.DEFINITELY_NOT_NULL,
        listOf(makeTypeProjection(declaration.returnType, Variance.INVARIANT)),
        emptyList()
      )

      val NotImplementedError: IrClassSymbol =
        pluginContext.referenceClass(classId<NotImplementedError>()) ?: error("Cannot find TODO")

      val newFunction = parent.addFunction("${declaration.name}Future", futureSimpleType.type).apply {
        origin = AsFutureOrigin
        copyParameterDeclarationsFrom(declaration)

        val lambda = irSuspendLambda(declaration, declaration.returnType) { +irCall(declaration) }

        messageCollector.report(WARNING, lambda.dump())

        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
          +lambda // TODO Fix this fails
//          val call = irCall(futureFn).apply {
//            putTypeArgument(0, declaration.returnType)
//            putValueArgument(2, irCall(lambda))
//          }
//          +irReturn(call)
          +irThrow(irCallConstructor(NotImplementedError.constructors.first(), emptyList()))
        }
      }

      messageCollector.report(WARNING, newFunction.dump())
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
  ): IrSimpleFunction /* IrFunctionExpression */ {
    val scope = IrSimpleTypeImpl(
      coroutineScopeType,
      SimpleTypeNullability.DEFINITELY_NOT_NULL,
      emptyList(),
      emptyList()
    )
    val lambda = pluginContext.irFactory.buildFun {
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
      name = Name.special("<anonymous>")
      visibility = DescriptorVisibilities.LOCAL
      isSuspend = true
      this.returnType = returnType
    }.apply {
      this.parent = parent
      addExtensionReceiver(scope)
      body = DeclarationIrBuilder(pluginContext, this.symbol).irBlockBody { content(this@apply) }
    }
    return lambda
//    return IrFunctionExpressionImpl(
//      startOffset = UNDEFINED_OFFSET,
//      endOffset = UNDEFINED_OFFSET,
//      type = pluginContext.irBuiltIns.suspendFunctionN(1).typeWith(scope, returnType),
//      origin = IrStatementOrigin.LAMBDA,
//      function = lambda
//    )
  }
}

private inline fun <reified T> classId(): ClassId {
  val fqName = FqName(T::class.java.canonicalName)
  return ClassId.topLevel(fqName)
}
