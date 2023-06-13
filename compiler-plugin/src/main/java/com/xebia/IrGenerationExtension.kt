package com.xebia

import java.util.concurrent.CompletableFuture
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
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.parent
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.copyAttributes
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrEnumEntrySymbolImpl
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability.DEFINITELY_NOT_NULL
import org.jetbrains.kotlin.ir.types.addAnnotations
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
    pluginContext.referenceClass(classId<ExtensionFunctionType>())?.constructors?.firstOrNull()
      ?: error("Internal error: Class ExtensionFunctionType not found.")

  val jvmNameConstructor: IrConstructorSymbol =
    pluginContext.referenceClass(ClassId.fromString("kotlin/jvm/JvmName"))?.constructors?.firstOrNull()
      ?: error("Internal error: Class ExtensionFunctionType not found.")

  val deprecatedConstructor: IrConstructorSymbol =
    pluginContext.referenceClass(ClassId.fromString("kotlin/Deprecated"))?.constructors?.firstOrNull()
      ?: error("Internal error: Class ExtensionFunctionType not found.")

  val scopeSimpleType = IrSimpleTypeImpl(
    coroutineScopeType, DEFINITELY_NOT_NULL, emptyList(), emptyList()
  )

  val deprecationLevelType: IrClassSymbol = pluginContext.referenceClass(classId<DeprecationLevel>())
    ?: error("Internal error:DeprecationLevel not found.")

  val deprecationLevelIrType = IrSimpleTypeImpl(
    deprecationLevelType,
    DEFINITELY_NOT_NULL,
    emptyList(),
    emptyList()
  )

  val HIDDEN: IrEnumEntry = pluginContext.irFactory.createEnumEntry(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB,
    IrEnumEntrySymbolImpl(),
    Name.identifier("HIDDEN")
  ).apply {
    parent = deprecationLevelType.owner
  }

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

    declaration.annotations += listOf(jvmNameAnnotation("${declaration.name}Suspend"))

    if (pluginContext.platform?.isJvm() == true) {
      // This declaration is only available on JVM
      val futureFn: IrSimpleFunctionSymbol = pluginContext.referenceFunctions(futureCallableId).singleOrNull() ?: error(
        "Internal error: Function $futureCallableId not found. Please include org.jetbrains.kotlinx:kotlinx-coroutines-jdk8."
      )

      val futureClass: IrClassSymbol = pluginContext.referenceClass(classId<CompletableFuture<*>>())
        ?: error("Internal error: Function \"java/util/concurrent/CompletableFuture\" not found. Please include the jdk8 module.")

      val futureSimpleType = IrSimpleTypeImpl(
        futureClass,
        DEFINITELY_NOT_NULL,
        listOf(makeTypeProjection(declaration.returnType, Variance.INVARIANT)),
        emptyList()
      )

      parent.addFunction("${declaration.name}Future", futureSimpleType.type).apply {
        val thisScope = this
        origin = AsFutureOrigin
        copyAttributes(declaration as IrAttributeContainer)
        copyParameterDeclarationsFrom(declaration)
        val `this` = this.dispatchReceiverParameter!!

        annotations += listOf(
          jvmNameAnnotation("${declaration.name}"),
          hiddenDeprecationAnnotation("This function should not be called from Kotlin sources, only Java APIs.")
        )

        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
          val lambda = buildSuspendLambda(declaration.returnType) {
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
              +irReturn(irCall(declaration).apply {
                this.dispatchReceiver = irGet(`this`)

                thisScope.typeParameters.withIndex().forEach { (index, type) ->
                  putTypeArgument(index, type.defaultType)
                }
                thisScope.allParameters().withIndex().forEach { (index, parameter) ->
                  putValueArgument(index, irGet(parameter))
                }
              })
            }
          }

          val call = irCall(futureFn).apply {
            type = futureSimpleType.type
            this.extensionReceiver = irGet(`this`)
            putTypeArgument(0, declaration.returnType)
            putValueArgument(2, lambdaArgument(lambda, returnType))
          }

          +irReturn(call)
        }
      }
    }
    return super.visitFunctionNew(declaration)
  }

  private fun IrSimpleFunction.allParameters(): List<IrValueParameter> =
    listOfNotNull(extensionReceiverParameter) + valueParameters

  private fun jvmNameAnnotation(name: String): IrConstructorCall =
    IrConstructorCallImpl.fromSymbolOwner(
      jvmNameConstructor.owner.returnType,
      jvmNameConstructor
    ).apply {
      putValueArgument(
        0,
        IrConstImpl.string(
          UNDEFINED_OFFSET,
          UNDEFINED_OFFSET,
          pluginContext.irBuiltIns.stringType,
          name
        )
      )
    }

  private fun hiddenDeprecationAnnotation(message: String): IrConstructorCall =
    IrConstructorCallImpl.fromSymbolOwner(
      deprecatedConstructor.owner.returnType,
      deprecatedConstructor
    ).apply {
      putValueArgument(
        0,
        IrConstImpl.string(
          UNDEFINED_OFFSET,
          UNDEFINED_OFFSET,
          pluginContext.irBuiltIns.stringType,
          message
        )
      )
      putValueArgument(
        2,
        IrGetEnumValueImpl(
          UNDEFINED_OFFSET, UNDEFINED_OFFSET, deprecationLevelIrType, HIDDEN.symbol
        )
      )
    }

  private fun IrDeclaration.reportError(message: String) {
    val location = file.locationOf(this)
    messageCollector.report(CompilerMessageSeverity.ERROR, "$LOG_PREFIX $message", location)
  }

  /** Finds the line and column of [irElement] within this file. */
  private fun IrFile.locationOf(irElement: IrElement?): CompilerMessageSourceLocation {
    val sourceRangeInfo = fileEntry.getSourceRangeInfo(
      beginOffset = irElement?.startOffset ?: SYNTHETIC_OFFSET, endOffset = irElement?.endOffset ?: SYNTHETIC_OFFSET
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

  private fun IrBuilderWithScope.buildSuspendLambda(
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

  private fun IrBuilderWithScope.lambdaArgument(lambda: IrSimpleFunction, returnType: IrType): IrFunctionExpression =
    IrFunctionExpressionImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      pluginContext.irBuiltIns.suspendFunctionN(1).typeWith(scopeSimpleType, returnType)
        .addAnnotations(listOf(irCall(extensionFnConstructor))),
      lambda,
      IrStatementOrigin.LAMBDA
    )
}

private inline fun <reified T> classId(): ClassId {
  val fqName = FqName(T::class.java.canonicalName)
  return ClassId.topLevel(fqName)
}
