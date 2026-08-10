package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiCodeBlockIndentation
import site.addzero.lsi.model.LsiBracedExpressionCompletion
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodePart
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiInitializerBlock
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.LsiTypeReferenceStyle

/**
 * 在边界内使用 KotlinPoet 渲染 Kotlin 源码。
 */
class LsiKotlinPoetRenderer : LsiPoetRenderer {

    /** 将单个 LSI 类型引用渲染为可嵌入现有 KotlinPoet 声明的类型。 */
    fun renderTypeName(
        type: LsiType,
        typeNames: List<LsiTypeName>,
    ): TypeName {
        return type.toKotlinTypeName(typeNames)
    }

    /** 将任意 LSI Poet 代码块渲染为可嵌入现有 KotlinPoet 声明的代码块。 */
    fun renderCodeBlock(
        codeBlock: LsiCodeBlock,
        typeNames: List<LsiTypeName>,
    ): CodeBlock {
        return codeBlock.toKotlinCodeBlock(typeNames)
    }

    /** 将 LSI 代码块直接追加到现有 KotlinPoet builder，保留外围语句和缩进状态。 */
    fun appendCodeBlock(
        builder: CodeBlock.Builder,
        codeBlock: LsiCodeBlock,
        typeNames: List<LsiTypeName>,
    ) {
        codeBlock.appendToKotlinCodeBlock(builder, typeNames)
    }

    /** 将单个 LSI 类型渲染为可嵌入现有 KotlinPoet 声明的结构。 */
    fun renderType(
        type: LsiClass,
        typeNames: List<LsiTypeName>,
    ): TypeSpec {
        return type.toKotlinTypeSpec(typeNames)
    }

    /** 将单个 LSI 函数渲染为可嵌入现有 KotlinPoet 类型的结构。 */
    fun renderFunction(
        function: LsiFunction,
        typeNames: List<LsiTypeName>,
    ): FunSpec {
        return function.toKotlinFunction(typeNames)
    }

    /** 将单个 LSI 属性渲染为可嵌入现有 KotlinPoet 类型的结构。 */
    fun renderProperty(
        property: LsiProperty,
        typeNames: List<LsiTypeName>,
    ): PropertySpec {
        return property.toKotlinProperty(typeNames)
    }

    /** 将单个 LSI Poet 注解渲染为可嵌入现有 KotlinPoet 声明的结构。 */
    fun renderAnnotation(
        annotation: LsiAnnotation,
        typeNames: List<LsiTypeName>,
    ): AnnotationSpec {
        return annotation.toKotlinSourceAnnotationSpec(typeNames)
    }

    /** 按声明顺序将 LSI Poet 注解列表渲染为 KotlinPoet 结构。 */
    fun renderAnnotations(
        annotations: List<LsiAnnotation>,
        typeNames: List<LsiTypeName>,
    ): List<AnnotationSpec> {
        return annotations.map { annotation -> renderAnnotation(annotation, typeNames) }
    }

    override fun render(artifact: LsiSourceArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.KOTLIN) {
            "KotlinPoet renderer requires a Kotlin LSI Poet file: ${artifact.qualifiedFileName}"
        }
        val builder = FileSpec.builder(file.packageName, file.fileName)
            .indent("    ")
        file.headerComment?.let { comment -> builder.addFileComment("%L", comment) }
        file.annotations.forEach { annotation ->
            builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(artifact.typeNames))
        }
        file.imports.forEach { sourceImport ->
            builder.addImport(sourceImport.packageName, sourceImport.simpleName)
        }
        file.members.forEach { member -> builder.addKotlinTopLevelMember(member, artifact.typeNames) }
        return artifact.generatedArtifact(builder.build().toString())
    }
}

private fun FileSpec.Builder.addKotlinTopLevelMember(
    member: LsiMember,
    typeNames: List<LsiTypeName>,
) {
    when (member) {
        is LsiFunction -> addFunction(member.toKotlinFunction(typeNames))
        is LsiProperty -> addProperty(member.toKotlinProperty(typeNames))
        is LsiClass -> addType(member.toKotlinTypeSpec(typeNames))
        is LsiConstructor -> error("KotlinPoet renderer cannot emit a top-level constructor")
        is LsiField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiInitializerBlock -> error("KotlinPoet renderer cannot emit a top-level initializer block")
    }
}

private fun LsiClass.toKotlinTypeSpec(typeNames: List<LsiTypeName>): TypeSpec {
    val builder = when (kind) {
        LsiTypeDeclarationKind.CLASS -> TypeSpec.classBuilder(name)
        LsiTypeDeclarationKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiTypeDeclarationKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiTypeDeclarationKind.OBJECT -> if (LsiModifier.COMPANION in modifiers) {
            TypeSpec.companionObjectBuilder(name.takeUnless { candidate -> candidate == "Companion" })
        } else {
            TypeSpec.objectBuilder(name)
        }
        LsiTypeDeclarationKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
        LsiTypeDeclarationKind.RECORD -> error("KotlinPoet renderer cannot emit a Java record type: $name")
        LsiTypeDeclarationKind.TYPE_ALIAS -> error("KotlinPoet renderer cannot emit a type alias: $name")
    }
    builder.addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.TYPE))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter -> builder.addTypeVariable(parameter.toKotlinTypeVariableName(typeNames)) }
    primaryConstructor?.let { constructor ->
        builder.primaryConstructor(constructor.toKotlinConstructor(typeNames, primary = true))
    }
    superClass?.let { type -> builder.superclass(type.toKotlinTypeName(typeNames)) }
    superClassConstructorArguments.forEach { argument ->
        builder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock(typeNames))
    }
    superInterfaces.forEach { type -> builder.addSuperinterface(type.toKotlinTypeName(typeNames)) }
    enumEntries.forEach { constant -> builder.addKotlinEnumConstant(constant, typeNames) }
    members.forEach { member -> builder.addKotlinMember(member, typeNames) }
    return builder.build()
}

private fun TypeSpec.Builder.addKotlinEnumConstant(
    constant: LsiEnumEntry,
    typeNames: List<LsiTypeName>,
) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val anonymousBuilder = TypeSpec.anonymousClassBuilder()
    constant.constructorArguments.forEach { argument ->
        anonymousBuilder.addSuperclassConstructorParameter(argument.toKotlinCodeBlock(typeNames))
    }
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumEntries.isEmpty()) {
            "Kotlin enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames))
        }
        type.superInterfaces.forEach { superType ->
            anonymousBuilder.addSuperinterface(superType.toKotlinTypeName(typeNames))
        }
        type.members.forEach { member -> anonymousBuilder.addKotlinMember(member, typeNames) }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addKotlinMember(
    member: LsiMember,
    typeNames: List<LsiTypeName>,
) {
    when (member) {
        is LsiConstructor -> addFunction(member.toKotlinConstructor(typeNames, primary = false))
        is LsiField -> error("KotlinPoet renderer cannot emit a field: ${member.name}")
        is LsiFunction -> addFunction(member.toKotlinFunction(typeNames))
        is LsiInitializerBlock -> addKotlinInitializer(member, typeNames)
        is LsiProperty -> addProperty(member.toKotlinProperty(typeNames))
        is LsiClass -> addType(member.toKotlinTypeSpec(typeNames))
    }
}

private fun TypeSpec.Builder.addKotlinInitializer(
    initializer: LsiInitializerBlock,
    typeNames: List<LsiTypeName>,
) {
    require(!initializer.static) {
        "KotlinPoet renderer cannot emit a static initializer block"
    }
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Kotlin initializer block cannot declare annotations or documentation"
    }
    addInitializerBlock(initializer.body.toKotlinCodeBlock(typeNames))
}

private fun LsiConstructor.toKotlinConstructor(
    typeNames: List<LsiTypeName>,
    primary: Boolean,
): FunSpec {
    if (primary) {
        require(body.isEmpty && delegationCall == null) {
            "Kotlin primary constructor cannot declare a body or delegation call"
        }
    }
    require(typeParameters.isEmpty()) {
        "KotlinPoet renderer cannot emit constructor type parameters"
    }
    val builder = FunSpec.constructorBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.CONSTRUCTOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter(typeNames)) }
    builder.addThrownTypes(thrownTypes, typeNames)
    delegationCall?.let { delegation ->
        val arguments = delegation.arguments.map { argument -> argument.toKotlinCodeBlock(typeNames) }.toTypedArray()
        when (delegation.target) {
            LsiDelegationTarget.THIS -> builder.callThisConstructor(*arguments)
            LsiDelegationTarget.SUPER -> builder.callSuperConstructor(*arguments)
        }
    }
    builder.addCode(body.toKotlinCodeBlock(typeNames))
    return builder.build()
}

private fun LsiFunction.toKotlinFunction(typeNames: List<LsiTypeName>): FunSpec {
    val builder = FunSpec.builder(name)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.FUNCTION))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(
            parameter.toKotlinTypeVariableName(
                typeNames = typeNames,
                reified = parameter.id in reifiedTypeParameterIds,
            )
        )
    }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName(typeNames)) }
    parameters.forEach { parameter -> builder.addParameter(parameter.toKotlinParameter(typeNames)) }
    returnType?.let { type -> builder.returns(type.toKotlinTypeName(typeNames)) }
    builder.addThrownTypes(thrownTypes, typeNames)
    when (bodyStyle) {
        LsiBodyStyle.BLOCK -> builder.addCode(body.toKotlinCodeBlock(typeNames))
        LsiBodyStyle.EXPRESSION -> builder.addCode("return %L", body.toKotlinCodeBlock(typeNames))
    }
    return builder.build()
}

private fun LsiParameter.toKotlinParameter(typeNames: List<LsiTypeName>): ParameterSpec {
    val builder = ParameterSpec.builder(name, type.toKotlinTypeName(typeNames))
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PARAMETER))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    defaultValue?.let { value -> builder.defaultValue(value.toKotlinCodeBlock(typeNames)) }
    return builder.build()
}

private fun LsiProperty.toKotlinProperty(typeNames: List<LsiTypeName>): PropertySpec {
    val builder = PropertySpec.builder(name, type.toKotlinTypeName(typeNames))
        .mutable(mutable)
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.PROPERTY))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    documentation?.let { value -> builder.addKdoc("%L", value) }
    receiverType?.let { type -> builder.receiver(type.toKotlinTypeName(typeNames)) }
    initializer?.let { value -> builder.initializer(value.toKotlinCodeBlock(typeNames)) }
    getter?.let { accessor -> builder.getter(accessor.toKotlinGetter(typeNames)) }
    setter?.let { accessor -> builder.setter(accessor.toKotlinSetter(type, typeNames)) }
    return builder.build()
}

private fun LsiAccessor.toKotlinGetter(typeNames: List<LsiTypeName>): FunSpec {
    require(parameterAnnotations.isEmpty()) {
        "Kotlin getter cannot declare setter parameter annotations"
    }
    val builder = FunSpec.getterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    when (bodyStyle) {
        LsiBodyStyle.BLOCK -> builder.addCode(body.toKotlinCodeBlock(typeNames))
        LsiBodyStyle.EXPRESSION -> builder.addCode("return %L", body.toKotlinCodeBlock(typeNames))
    }
    return builder.build()
}

private fun LsiAccessor.toKotlinSetter(
    type: LsiType,
    typeNames: List<LsiTypeName>,
): FunSpec {
    require(bodyStyle == LsiBodyStyle.BLOCK) {
        "KotlinPoet renderer cannot emit an expression setter body"
    }
    val parameter = ParameterSpec.builder(setterParameterName, type.toKotlinTypeName(typeNames))
        .apply {
            parameterAnnotations.forEach { annotation ->
                addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames))
            }
        }
        .build()
    val builder = FunSpec.setterBuilder()
        .addModifiers(*modifiers.toKotlinModifiers(KotlinModifierContext.ACCESSOR))
        .addParameter(parameter)
    annotations.forEach { annotation -> builder.addAnnotation(annotation.toKotlinSourceAnnotationSpec(typeNames)) }
    builder.addCode(body.toKotlinCodeBlock(typeNames))
    return builder.build()
}

private fun LsiCodeBlock.toKotlinCodeBlock(typeNames: List<LsiTypeName>): CodeBlock {
    val builder = CodeBlock.builder()
    appendToKotlinCodeBlock(builder, typeNames)
    return builder.build()
}

private fun LsiCodeBlock.appendToKotlinCodeBlock(
    builder: CodeBlock.Builder,
    typeNames: List<LsiTypeName>,
) {
    if (indentation == LsiCodeBlockIndentation.EXPLICIT) {
        // 空语句标记只抑制外围声明的双倍续行缩进，不产生任何源码字符。
        builder.add("«»")
    }
    parts.forEach { part ->
        when (part) {
            is LsiCodePart.BeginControlFlow -> builder.beginControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(typeNames),
            )
            is LsiCodePart.BracedExpression -> builder.addKotlinBracedExpression(part, typeNames)
            is LsiCodePart.CharacterLiteral -> builder.add("%L", part.value.kotlinCharacterLiteral())
            LsiCodePart.EndControlFlow -> builder.endControlFlow()
            LsiCodePart.Indent -> builder.indent()
            is LsiCodePart.Literal -> builder.add("%L", part.value)
            is LsiCodePart.Name -> builder.add("%N", part.value)
            LsiCodePart.NewLine -> builder.add("\n")
            is LsiCodePart.NextControlFlow -> builder.nextControlFlow(
                "%L",
                part.header.toKotlinCodeBlock(typeNames),
            )
            is LsiCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return %L", value.toKotlinCodeBlock(typeNames))
            } ?: builder.addStatement("return")
            is LsiCodePart.Statement -> builder.addStatement("%L", part.value.toKotlinCodeBlock(typeNames))
            is LsiCodePart.StringLiteral -> builder.add("%S", part.value)
            is LsiCodePart.Text -> builder.add("%L", part.value)
            is LsiCodePart.TopLevelMember -> builder.add(
                "%M",
                MemberName(part.packageName, part.simpleName, part.extension),
            )
            is LsiCodePart.Type -> when (part.referenceStyle) {
                LsiTypeReferenceStyle.IMPORTED -> builder.add("%T", part.value.toKotlinTypeName(typeNames))
                LsiTypeReferenceStyle.FULLY_QUALIFIED -> builder.add(
                    "%L",
                    part.value.toKotlinTypeName(typeNames),
                )
                LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> error(
                    "Same-package outer-qualified type references are only valid for Java declarations"
                )
            }
            LsiCodePart.Unindent -> builder.unindent()
        }
    }
}

private fun CodeBlock.Builder.addKotlinBracedExpression(
    expression: LsiCodePart.BracedExpression,
    typeNames: List<LsiTypeName>,
) {
    if (expression.completion == LsiBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("%L", expression.prefix.toKotlinCodeBlock(typeNames))
    add(" {\n")
    indent()
    add("%L", expression.body.toKotlinCodeBlock(typeNames))
    unindent()
    add("}")
    add("%L", expression.suffix.toKotlinCodeBlock(typeNames))
    add("\n")
}

private fun FunSpec.Builder.addThrownTypes(
    thrownTypes: List<LsiType>,
    typeNames: List<LsiTypeName>,
) {
    if (thrownTypes.isEmpty()) {
        return
    }
    addAnnotation(
        AnnotationSpec.builder(Throws::class)
            .addMember(
                thrownTypes.joinToString(", ") { "%T::class" },
                *thrownTypes.map { type -> type.toKotlinTypeName(typeNames) }.toTypedArray(),
            )
            .build()
    )
}

private enum class KotlinModifierContext {
    TYPE,
    CONSTRUCTOR,
    FUNCTION,
    PROPERTY,
    PARAMETER,
    ACCESSOR,
}

private fun Set<LsiModifier>.toKotlinModifiers(
    context: KotlinModifierContext,
): Array<KModifier> {
    return sorted().mapNotNullTo(linkedSetOf()) { modifier ->
        modifier.toKotlinModifier(context)
    }.toTypedArray()
}

private fun LsiModifier.toKotlinModifier(context: KotlinModifierContext): KModifier? {
    val modifier = when (this) {
        LsiModifier.PUBLIC -> KModifier.PUBLIC
        LsiModifier.PROTECTED -> KModifier.PROTECTED
        LsiModifier.INTERNAL -> KModifier.INTERNAL
        LsiModifier.PRIVATE -> KModifier.PRIVATE
        LsiModifier.ABSTRACT -> KModifier.ABSTRACT
        LsiModifier.OPEN -> KModifier.OPEN
        LsiModifier.FINAL -> KModifier.FINAL
        LsiModifier.SEALED -> KModifier.SEALED
        LsiModifier.CONST -> KModifier.CONST
        LsiModifier.OVERRIDE -> KModifier.OVERRIDE
        LsiModifier.INLINE -> KModifier.INLINE
        LsiModifier.NOINLINE -> KModifier.NOINLINE
        LsiModifier.CROSSINLINE -> KModifier.CROSSINLINE
        LsiModifier.TAILREC -> KModifier.TAILREC
        LsiModifier.SUSPEND -> KModifier.SUSPEND
        LsiModifier.OPERATOR -> KModifier.OPERATOR
        LsiModifier.INFIX -> KModifier.INFIX
        LsiModifier.EXTERNAL -> KModifier.EXTERNAL
        LsiModifier.LATEINIT -> KModifier.LATEINIT
        LsiModifier.DATA -> KModifier.DATA
        LsiModifier.VALUE -> KModifier.VALUE
        LsiModifier.INNER -> KModifier.INNER
        LsiModifier.VARARG -> KModifier.VARARG
        LsiModifier.COMPANION,
        LsiModifier.DEFAULT,
        -> null
        LsiModifier.STATIC,
        LsiModifier.SYNCHRONIZED,
        LsiModifier.NATIVE,
        LsiModifier.TRANSIENT,
        LsiModifier.VOLATILE,
        -> error("KotlinPoet renderer cannot emit modifier $this for $context")
    }
    require(isAllowedInKotlin(context)) {
        "KotlinPoet renderer cannot emit modifier $this for $context"
    }
    return modifier
}

private fun LsiModifier.isAllowedInKotlin(context: KotlinModifierContext): Boolean {
    return when (this) {
        LsiModifier.PUBLIC,
        LsiModifier.PROTECTED,
        LsiModifier.INTERNAL,
        LsiModifier.PRIVATE,
        -> context != KotlinModifierContext.PARAMETER
        LsiModifier.ABSTRACT,
        LsiModifier.OPEN,
        LsiModifier.FINAL,
        -> context == KotlinModifierContext.TYPE ||
            context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY
        LsiModifier.SEALED,
        LsiModifier.DATA,
        LsiModifier.VALUE,
        LsiModifier.INNER,
        LsiModifier.COMPANION,
        -> context == KotlinModifierContext.TYPE
        LsiModifier.CONST,
        LsiModifier.LATEINIT,
        -> context == KotlinModifierContext.PROPERTY
        LsiModifier.OVERRIDE -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY
        LsiModifier.INLINE -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY ||
            context == KotlinModifierContext.ACCESSOR
        LsiModifier.NOINLINE,
        LsiModifier.CROSSINLINE,
        LsiModifier.VARARG,
        -> context == KotlinModifierContext.PARAMETER
        LsiModifier.TAILREC,
        LsiModifier.SUSPEND,
        LsiModifier.OPERATOR,
        LsiModifier.INFIX,
        -> context == KotlinModifierContext.FUNCTION
        LsiModifier.EXTERNAL -> context == KotlinModifierContext.FUNCTION ||
            context == KotlinModifierContext.PROPERTY ||
            context == KotlinModifierContext.ACCESSOR
        LsiModifier.DEFAULT -> context == KotlinModifierContext.FUNCTION
        else -> false
    }
}

private fun Char.kotlinCharacterLiteral(): String {
    val content = when (this) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\u000c' -> "\\u000c"
        '\r' -> "\\r"
        '\'' -> "\\'"
        '\\' -> "\\\\"
        else -> if (isISOControl()) "\\u${code.toString(16).padStart(4, '0')}" else toString()
    }
    return "'$content'"
}
