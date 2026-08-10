package site.addzero.lsi.poet.javapoet

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiBracedExpressionCompletion
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiCodeBlockIndentation
import site.addzero.lsi.model.LsiCodePart
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDelegationTarget
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiInitializerBlock
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.poet.LsiPoetRenderer
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeReferenceStyle

/**
 * 在边界内使用 JavaPoet 渲染 Java 源码。
 */
class LsiJavaPoetRenderer : LsiPoetRenderer {

    /** 将单个 LSI 类型引用渲染为可嵌入现有 JavaPoet 声明的类型。 */
    fun renderTypeName(
        type: LsiType,
        typeNames: List<LsiClass>,
    ): TypeName {
        return type.toJavaTypeName(typeNames)
    }

    /** 将任意 LSI Poet 代码块渲染为可嵌入现有 JavaPoet 声明的代码块。 */
    fun renderCodeBlock(
        codeBlock: LsiCodeBlock,
        typeNames: List<LsiClass>,
    ): CodeBlock {
        return codeBlock.toJavaCodeBlock(typeNames)
    }

    /** 将单个 LSI 类型渲染为可嵌入现有 JavaPoet 声明的结构。 */
    fun renderType(
        type: LsiClass,
        typeNames: List<LsiClass>,
    ): TypeSpec {
        return type.toJavaTypeSpec(typeNames, currentPackageName = null)
    }

    /** 将单个 LSI 函数渲染为可嵌入现有 JavaPoet 类型的结构。 */
    fun renderFunction(
        function: LsiFunction,
        typeNames: List<LsiClass>,
    ): MethodSpec {
        return function.toJavaMethod(typeNames)
    }

    /** 将单个 LSI 字段渲染为可嵌入现有 JavaPoet 类型的结构。 */
    fun renderField(
        field: LsiField,
        typeNames: List<LsiClass>,
        currentPackageName: String? = null,
    ): FieldSpec {
        return field.toJavaField(typeNames, currentPackageName)
    }

    /** 将单个 LSI Poet 注解渲染为可嵌入现有 JavaPoet 声明的结构。 */
    fun renderAnnotation(
        annotation: LsiAnnotation,
        typeNames: List<LsiClass>,
    ): AnnotationSpec {
        return annotation.toJavaSourceAnnotationSpec(typeNames)
    }

    /** 按声明顺序将 LSI Poet 注解列表渲染为 JavaPoet 结构。 */
    fun renderAnnotations(
        annotations: List<LsiAnnotation>,
        typeNames: List<LsiClass>,
    ): List<AnnotationSpec> {
        return annotations.map { annotation -> renderAnnotation(annotation, typeNames) }
    }

    override fun render(artifact: LsiSourceArtifact): GeneratedArtifact {
        val file = artifact.file
        require(file.language == LsiLanguage.JAVA) {
            "JavaPoet renderer requires a Java LSI Poet file: ${artifact.qualifiedFileName}"
        }
        require(file.annotations.isEmpty()) {
            "JavaPoet renderer does not support file annotations: ${artifact.qualifiedFileName}"
        }
        require(file.imports.isEmpty()) {
            "JavaPoet renderer does not support explicit imports: ${artifact.qualifiedFileName}"
        }
        val type = file.members.singleOrNull() as? LsiClass
            ?: error("Java LSI Poet file must contain exactly one top-level type: ${artifact.qualifiedFileName}")
        require(type.name == file.fileName) {
            "Java LSI Poet file name must match its top-level type: ${artifact.qualifiedFileName}"
        }
        val javaFile = JavaFile.builder(
            file.packageName,
            type.toJavaTypeSpec(artifact.typeNames, file.packageName),
        )
            .indent("    ")
            .apply {
                file.headerComment?.let { comment -> addFileComment("\$L", comment) }
            }
            .build()
        return artifact.generatedArtifact(javaFile.toString())
    }
}

private fun LsiClass.toJavaTypeSpec(
    typeNames: List<LsiClass>,
    currentPackageName: String?,
): TypeSpec {
    require(nameStyle == LsiNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin type name: $name"
    }
    val builder = when (kind) {
        LsiTypeDeclarationKind.CLASS -> TypeSpec.classBuilder(name)
        LsiTypeDeclarationKind.INTERFACE -> TypeSpec.interfaceBuilder(name)
        LsiTypeDeclarationKind.ENUM -> TypeSpec.enumBuilder(name)
        LsiTypeDeclarationKind.ANNOTATION -> TypeSpec.annotationBuilder(name)
        LsiTypeDeclarationKind.OBJECT -> error("JavaPoet renderer cannot emit an object type: $name")
        LsiTypeDeclarationKind.RECORD -> error("JavaPoet 1.x renderer cannot emit a record type: $name")
        LsiTypeDeclarationKind.TYPE_ALIAS -> error("JavaPoet renderer cannot emit a type alias: $name")
    }
    builder.addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.TYPE))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    superClass?.let { type -> builder.superclass(type.toJavaTypeName(typeNames)) }
    superInterfaces.forEach { type ->
        builder.addSuperinterface(type.toJavaTypeName(typeNames))
    }
    require(superClassConstructorArguments.isEmpty()) {
        "Java superclass constructor arguments must be declared by a constructor delegation call: $name"
    }
    primaryConstructor?.let { constructor ->
        builder.addMethod(constructor.toJavaConstructor(typeNames))
    }
    enumEntries.forEach { constant ->
        builder.addJavaEnumConstant(constant, typeNames, currentPackageName)
    }
    members.forEach { member -> builder.addJavaMember(member, typeNames, currentPackageName) }
    return builder.build()
}

private fun TypeSpec.Builder.addJavaEnumConstant(
    constant: LsiEnumEntry,
    typeNames: List<LsiClass>,
    currentPackageName: String?,
) {
    if (constant.constructorArguments.isEmpty() && constant.anonymousType == null) {
        addEnumConstant(constant.name)
        return
    }
    val arguments = constant.constructorArguments.toJavaArgumentList(typeNames)
    val anonymousBuilder = TypeSpec.anonymousClassBuilder(arguments)
    constant.anonymousType?.let { type ->
        require(type.primaryConstructor == null && type.enumEntries.isEmpty()) {
            "Java enum constant anonymous type cannot declare constructors or enum constants: ${constant.name}"
        }
        type.annotations.forEach { annotation ->
            anonymousBuilder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
        }
        type.superInterfaces.forEach { superType ->
            anonymousBuilder.addSuperinterface(superType.toJavaTypeName(typeNames))
        }
        type.members.forEach { member ->
            anonymousBuilder.addJavaMember(member, typeNames, currentPackageName)
        }
    }
    addEnumConstant(constant.name, anonymousBuilder.build())
}

private fun TypeSpec.Builder.addJavaMember(
    member: LsiMember,
    typeNames: List<LsiClass>,
    currentPackageName: String?,
) {
    when (member) {
        is LsiConstructor -> addMethod(member.toJavaConstructor(typeNames))
        is LsiField -> addField(member.toJavaField(typeNames, currentPackageName))
        is LsiFunction -> addMethod(member.toJavaMethod(typeNames))
        is LsiInitializerBlock -> addJavaInitializer(member, typeNames)
        is LsiProperty -> error("JavaPoet renderer cannot emit a Kotlin property: ${member.name}")
        is LsiClass -> addType(member.toJavaTypeSpec(typeNames, currentPackageName))
    }
}

private fun TypeSpec.Builder.addJavaInitializer(
    initializer: LsiInitializerBlock,
    typeNames: List<LsiClass>,
) {
    require(initializer.annotations.isEmpty() && initializer.documentation == null) {
        "Java initializer block cannot declare annotations or documentation"
    }
    if (initializer.static) {
        addStaticBlock(initializer.body.toJavaCodeBlock(typeNames))
    } else {
        addInitializerBlock(initializer.body.toJavaCodeBlock(typeNames))
    }
}

private fun LsiConstructor.toJavaConstructor(
    typeNames: List<LsiClass>,
): MethodSpec {
    val builder = MethodSpec.constructorBuilder()
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.CONSTRUCTOR))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter(typeNames)) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiModifier.VARARG) == true) {
        builder.varargs(true)
    }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName(typeNames)) }
    delegationCall?.let { delegation ->
        val target = when (delegation.target) {
            LsiDelegationTarget.THIS -> "this"
            LsiDelegationTarget.SUPER -> "super"
        }
        builder.addStatement("\$L(\$L)", target, delegation.arguments.toJavaArgumentList(typeNames))
    }
    builder.addCode(body.toJavaCodeBlock(typeNames))
    return builder.build()
}

private fun LsiFunction.toJavaMethod(
    typeNames: List<LsiClass>,
): MethodSpec {
    require(nameStyle == LsiNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin function name: $name"
    }
    require(receiverType == null) {
        "JavaPoet renderer cannot emit an extension receiver: $name"
    }
    require(reifiedTypeParameterIds.isEmpty()) {
        "JavaPoet renderer cannot emit reified type parameters: $name"
    }
    require(bodyStyle == LsiBodyStyle.BLOCK) {
        "JavaPoet renderer cannot emit an expression function body: $name"
    }
    val builder = MethodSpec.methodBuilder(name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.FUNCTION))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    if (
        LsiModifier.OVERRIDE in modifiers &&
        annotations.none { annotation -> annotation.type == JAVA_LANG_OVERRIDE }
    ) {
        builder.addAnnotation(Override::class.java)
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    typeParameters.forEach { parameter ->
        builder.addTypeVariable(parameter.toJavaTypeVariableName(typeNames))
    }
    parameters.forEach { parameter -> builder.addParameter(parameter.toJavaParameter(typeNames)) }
    if (parameters.lastOrNull()?.modifiers?.contains(LsiModifier.VARARG) == true) {
        builder.varargs(true)
    }
    returnType?.let { type -> builder.returns(type.toJavaTypeName(typeNames)) }
    thrownTypes.forEach { type -> builder.addException(type.toJavaTypeName(typeNames)) }
    builder.addCode(body.toJavaCodeBlock(typeNames))
    return builder.build()
}

private fun LsiParameter.toJavaParameter(
    typeNames: List<LsiClass>,
): ParameterSpec {
    require(nameStyle == LsiNameStyle.IDENTIFIER) {
        "JavaPoet renderer cannot emit an escaped Kotlin parameter name: $name"
    }
    require(defaultValue == null) {
        "JavaPoet renderer cannot emit a default parameter value: $name"
    }
    val parameterType = type.toJavaTypeName(typeNames).let { typeName ->
        if (LsiModifier.VARARG in modifiers) ArrayTypeName.of(typeName) else typeName
    }
    val builder = ParameterSpec.builder(parameterType, name)
        .addModifiers(*modifiers.toJavaModifiers(JavaModifierContext.PARAMETER))
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    return builder.build()
}

private fun LsiField.toJavaField(
    typeNames: List<LsiClass>,
    currentPackageName: String?,
): FieldSpec {
    val javaModifiers = modifiers.toJavaModifiers(JavaModifierContext.FIELD).toMutableSet()
    if (LsiModifier.CONST in modifiers) {
        javaModifiers += Modifier.STATIC
        javaModifiers += Modifier.FINAL
    }
    val builder = FieldSpec.builder(
        type.toJavaTypeName(typeNames, typeReferenceStyle, currentPackageName),
        name,
        *javaModifiers.toTypedArray(),
    )
    annotations.forEach { annotation ->
        builder.addAnnotation(annotation.toJavaSourceAnnotationSpec(typeNames))
    }
    documentation?.let { value -> builder.addJavadoc("\$L", value) }
    initializer?.let { value -> builder.initializer(value.toJavaCodeBlock(typeNames)) }
    return builder.build()
}

private fun LsiCodeBlock.toJavaCodeBlock(
    typeNames: List<LsiClass>,
): CodeBlock {
    require(indentation == LsiCodeBlockIndentation.PLATFORM_DEFAULT) {
        "JavaPoet renderer cannot honor explicit Kotlin code indentation"
    }
    val builder = CodeBlock.builder()
    parts.forEach { part ->
        when (part) {
            is LsiCodePart.BeginControlFlow -> builder.beginControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(typeNames),
            )
            is LsiCodePart.BracedExpression -> builder.addJavaBracedExpression(part, typeNames)
            is LsiCodePart.CharacterLiteral -> builder.add("\$L", part.value.javaCharacterLiteral())
            LsiCodePart.EndControlFlow -> builder.endControlFlow()
            LsiCodePart.Indent -> builder.indent()
            is LsiCodePart.Literal -> builder.add("\$L", part.value)
            is LsiCodePart.Name -> builder.add("\$N", part.value)
            LsiCodePart.NewLine -> builder.add("\n")
            is LsiCodePart.NextControlFlow -> builder.nextControlFlow(
                "\$L",
                part.header.toJavaCodeBlock(typeNames),
            )
            is LsiCodePart.Return -> part.value?.let { value ->
                builder.addStatement("return \$L", value.toJavaCodeBlock(typeNames))
            } ?: builder.addStatement("return")
            is LsiCodePart.Statement -> builder.addStatement(
                "\$L",
                part.value.toJavaCodeBlock(typeNames),
            )
            is LsiCodePart.StringLiteral -> builder.add("\$S", part.value)
            is LsiCodePart.Text -> builder.add("\$L", part.value)
            is LsiCodePart.TopLevelMember -> error(
                "JavaPoet renderer cannot emit a Kotlin top-level member reference: " +
                    "${part.packageName}.${part.simpleName}"
            )
            is LsiCodePart.Type -> when (part.referenceStyle) {
                LsiTypeReferenceStyle.IMPORTED -> builder.add(
                    "\$T",
                    part.value.toJavaTypeName(typeNames),
                )
                LsiTypeReferenceStyle.FULLY_QUALIFIED -> builder.add(
                    "\$L",
                    part.value.toJavaTypeName(typeNames),
                )
                LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED -> error(
                    "Same-package outer-qualified type references require a declaration type position"
                )
            }
            LsiCodePart.Unindent -> builder.unindent()
        }
    }
    return builder.build()
}

private fun CodeBlock.Builder.addJavaBracedExpression(
    expression: LsiCodePart.BracedExpression,
    typeNames: List<LsiClass>,
) {
    if (expression.completion == LsiBracedExpressionCompletion.RETURN) {
        add("return ")
    }
    add("\$L", expression.prefix.toJavaCodeBlock(typeNames))
    add(" {\n")
    indent()
    add("\$L", expression.body.toJavaCodeBlock(typeNames))
    unindent()
    add("}")
    add("\$L", expression.suffix.toJavaCodeBlock(typeNames))
    add(";\n")
}

private fun List<LsiCodeBlock>.toJavaArgumentList(
    typeNames: List<LsiClass>,
): CodeBlock {
    val builder = CodeBlock.builder()
    forEachIndexed { index, argument ->
        if (index != 0) {
            builder.add(", ")
        }
        builder.add("\$L", argument.toJavaCodeBlock(typeNames))
    }
    return builder.build()
}

private enum class JavaModifierContext {
    TYPE,
    CONSTRUCTOR,
    FUNCTION,
    FIELD,
    PARAMETER,
}

private fun Set<LsiModifier>.toJavaModifiers(
    context: JavaModifierContext,
): Array<Modifier> {
    return sorted().mapNotNullTo(linkedSetOf()) { modifier ->
        modifier.toJavaModifier(context)
    }.toTypedArray()
}

private fun LsiModifier.toJavaModifier(context: JavaModifierContext): Modifier? {
    val modifier = when (this) {
        LsiModifier.PUBLIC -> Modifier.PUBLIC
        LsiModifier.PROTECTED -> Modifier.PROTECTED
        LsiModifier.PRIVATE -> Modifier.PRIVATE
        LsiModifier.ABSTRACT -> Modifier.ABSTRACT
        LsiModifier.FINAL -> Modifier.FINAL
        LsiModifier.STATIC -> Modifier.STATIC
        LsiModifier.DEFAULT -> Modifier.DEFAULT
        LsiModifier.SYNCHRONIZED -> Modifier.SYNCHRONIZED
        LsiModifier.NATIVE -> Modifier.NATIVE
        LsiModifier.TRANSIENT -> Modifier.TRANSIENT
        LsiModifier.VOLATILE -> Modifier.VOLATILE
        LsiModifier.CONST,
        LsiModifier.OVERRIDE,
        LsiModifier.VARARG,
        -> null
        LsiModifier.INTERNAL,
        LsiModifier.OPEN,
        LsiModifier.SEALED,
        LsiModifier.INLINE,
        LsiModifier.NOINLINE,
        LsiModifier.CROSSINLINE,
        LsiModifier.TAILREC,
        LsiModifier.SUSPEND,
        LsiModifier.OPERATOR,
        LsiModifier.INFIX,
        LsiModifier.EXTERNAL,
        LsiModifier.LATEINIT,
        LsiModifier.DATA,
        LsiModifier.VALUE,
        LsiModifier.INNER,
        LsiModifier.COMPANION,
        -> error("JavaPoet renderer cannot emit modifier $this for $context")
    }
    require(isAllowedInJava(context)) {
        "JavaPoet renderer cannot emit modifier $this for $context"
    }
    return modifier
}

private fun LsiModifier.isAllowedInJava(context: JavaModifierContext): Boolean {
    return when (this) {
        LsiModifier.PUBLIC,
        LsiModifier.PROTECTED,
        LsiModifier.PRIVATE,
        -> true
        LsiModifier.ABSTRACT -> context == JavaModifierContext.TYPE || context == JavaModifierContext.FUNCTION
        LsiModifier.FINAL -> context != JavaModifierContext.CONSTRUCTOR
        LsiModifier.STATIC -> context == JavaModifierContext.TYPE ||
            context == JavaModifierContext.FUNCTION ||
            context == JavaModifierContext.FIELD
        LsiModifier.DEFAULT,
        LsiModifier.SYNCHRONIZED,
        LsiModifier.NATIVE,
        LsiModifier.OVERRIDE,
        -> context == JavaModifierContext.FUNCTION
        LsiModifier.TRANSIENT,
        LsiModifier.VOLATILE,
        LsiModifier.CONST,
        -> context == JavaModifierContext.FIELD
        LsiModifier.VARARG -> context == JavaModifierContext.PARAMETER
        else -> false
    }
}

private fun Char.javaCharacterLiteral(): String {
    val content = when (this) {
        '\b' -> "\\b"
        '\t' -> "\\t"
        '\n' -> "\\n"
        '\u000c' -> "\\f"
        '\r' -> "\\r"
        '\'' -> "\\'"
        '\\' -> "\\\\"
        else -> if (isISOControl()) "\\u${code.toString(16).padStart(4, '0')}" else toString()
    }
    return "'$content'"
}

private val JAVA_LANG_OVERRIDE = LsiSymbolId.type("java.lang.Override")
