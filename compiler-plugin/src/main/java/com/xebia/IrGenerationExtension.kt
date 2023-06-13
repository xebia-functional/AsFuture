package com.xebia

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocationWithRange
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyParameterDeclarationsFrom
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.backend.common.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.IrTypeParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability.DEFINITELY_NOT_NULL
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.patchDeclarationParents

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

  val extensionFnConstructor: IrConstructorSymbol =
    pluginContext.referenceClass(ClassId.fromString("kotlin/ExtensionFunctionType"))?.constructors?.firstOrNull()
      ?: error("Internal error: Class ExtensionFunctionType not found.")

  val scopeSimpleType = IrSimpleTypeImpl(
    coroutineScopeType,
    DEFINITELY_NOT_NULL,
    emptyList(),
    emptyList()
  )

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
        pluginContext.referenceClass(ClassId.fromString("java.util.concurrent.CompletableFuture"))
          ?: error("Internal error: Function $coroutineScope not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-core.")

      val futureSimpleType = IrSimpleTypeImpl(
        futureClass, DEFINITELY_NOT_NULL,
        listOf(makeTypeProjection(declaration.returnType, Variance.INVARIANT)),
        emptyList()
      )

      val NotImplementedError: IrClassSymbol =
        pluginContext.referenceClass(ClassId.fromString("kotlin.NotImplementedError")) ?: error("Cannot find TODO")

      parent.addFunction("${declaration.name}Future", futureSimpleType.type).apply {
        val thisScope = this
        origin = AsFutureOrigin
        copyAttributes(declaration as IrAttributeContainer)
        copyParameterDeclarationsFrom(declaration)
        val `this` = this.dispatchReceiverParameter!!

        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
          val lambda = buildSuspendLambda(declaration.returnType) {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
              +irReturn(irCall(declaration).apply {
                this.dispatchReceiver = irGet(`this`)
                thisScope.typeParameters.withIndex().forEach { (index, type) ->
                  putTypeArgument(index, type.defaultType)
                }
                thisScope.valueParameters.withIndex().forEach { (index, parameter) ->
                  putValueArgument(index, irGet(parameter))
                }
              })
            }
          }

          val call = irCall(futureFn).apply {
            type = futureSimpleType.type
            this.extensionReceiver = irGet(`this`)

            putTypeArgument(0, declaration.returnType)
            // value argument 0, 1 have default values we want to maintain.
            putValueArgument(
              2,
              lambdaArgument(
                lambda,
                pluginContext.irBuiltIns.suspendFunctionN(1)
                  .typeWith(scopeSimpleType, declaration.returnType)
                  .addAnnotations(listOf(irCall(extensionFnConstructor)))
              )
            )
          }

          +irReturn(call)
//          +irThrow(irCallConstructor(NotImplementedError.constructors.first(), emptyList()))
        }
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

  fun IrBuilderWithScope.buildSuspendLambda(
    returnType: IrType,
    funApply: IrSimpleFunction.() -> Unit
  ): IrSimpleFunction = pluginContext.irFactory.buildFun {
    name = Name.special("<anonymous>")
    this.returnType = returnType
    this.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    isSuspend = true
  }.apply {
    IrValueParameterBuilder().run {
      this.type = scopeSimpleType
      this.origin = IrDeclarationOrigin.DEFINED
      this.index = -1
      this.name = "this\$future".synthesizedName
      factory.buildValueParameter(this, this@apply).also { receiver ->
        extensionReceiverParameter = receiver
      }
    }
    this.patchDeclarationParents(this@buildSuspendLambda.parent)
    funApply()
  }

  fun lambdaArgument(lambda: IrSimpleFunction, type: IrType): IrFunctionExpression = IrFunctionExpressionImpl(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    type,
    lambda,
    IrStatementOrigin.LAMBDA
  )
}
