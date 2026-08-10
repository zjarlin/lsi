@file:JvmSynthetic

package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.WildcardTypeName
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiArrayType
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiFunctionType
import site.addzero.lsi.model.LsiNullability
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiUnresolvedType
import site.addzero.lsi.model.LsiVariance
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentLayout
import site.addzero.lsi.model.LsiAnnotationArgumentNameStyle
import site.addzero.lsi.model.LsiAnnotationArrayStyle
import site.addzero.lsi.model.LsiClassLiteralStyle
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.toSourceAnnotation

internal fun LsiTypeRef.toKotlinTypeName(typeNames: List<LsiTypeName>): TypeName {
    return toKotlinTypeName(typeNames, referenceContext = false)
}

private fun LsiTypeRef.toKotlinTypeName(
    typeNames: List<LsiTypeName>,
    referenceContext: Boolean,
): TypeName {
    val typeName = when (this) {
        is LsiPrimitiveType -> toKotlinPrimitiveTypeName(referenceContext)
        is LsiDeclaredType -> {
            val exactTypeName = typeNames.requireTypeName(declarationId)
            val rawType = KOTLIN_TYPES[exactTypeName] ?: exactTypeName.toKotlinClassName()
            if (arguments.isEmpty()) {
                rawType
            } else {
                rawType.parameterizedBy(
                    arguments.map { argument ->
                        when (argument.variance) {
                            LsiVariance.STAR -> STAR
                            LsiVariance.INVARIANT -> requireNotNull(argument.type)
                                .toKotlinTypeName(typeNames, referenceContext = true)
                            LsiVariance.IN -> WildcardTypeName.consumerOf(
                                requireNotNull(argument.type).toKotlinTypeName(typeNames, referenceContext = true)
                            )
                            LsiVariance.OUT -> WildcardTypeName.producerOf(
                                requireNotNull(argument.type).toKotlinTypeName(typeNames, referenceContext = true)
                            )
                        }
                    }
                )
            }
        }
        is LsiArrayType -> elementType.toKotlinArrayTypeName(typeNames)
        is LsiFunctionType -> LambdaTypeName.get(
            receiver = receiverType?.toKotlinTypeName(typeNames, referenceContext = true),
            parameters = parameterTypes.map { parameter ->
                ParameterSpec.unnamed(parameter.toKotlinTypeName(typeNames, referenceContext = true))
            },
            returnType = returnType.toKotlinTypeName(typeNames, referenceContext = false),
        ).copy(suspending = suspending)
        is LsiTypeParameterRef -> TypeVariableName(parameterId.requireTypeParameterName())
        is LsiUnresolvedType -> error(
            "KotlinPoet renderer cannot emit unresolved LSI type: $displayName"
        )
    }
    val nullableTypeName = typeName.copy(nullable = nullability == LsiNullability.NULLABLE)
    val annotationSpecs = annotations.map { annotation ->
        annotation.toKotlinCoreAnnotationSpec(typeNames)
    }
    return if (annotationSpecs.isEmpty()) {
        nullableTypeName
    } else {
        nullableTypeName.copy(annotations = nullableTypeName.annotations + annotationSpecs)
    }
}

internal fun LsiTypeParameter.toKotlinTypeVariableName(
    typeNames: List<LsiTypeName>,
    reified: Boolean = false,
): TypeVariableName {
    val varianceModifier = when (variance) {
        LsiVariance.INVARIANT -> null
        LsiVariance.IN -> KModifier.IN
        LsiVariance.OUT -> KModifier.OUT
        LsiVariance.STAR -> error("Kotlin type parameter declaration cannot use star variance: $name")
    }
    val bounds = upperBounds.map { bound -> bound.toKotlinTypeName(typeNames) }.toTypedArray()
    return TypeVariableName(name, *bounds, variance = varianceModifier)
        .copy(reified = reified)
}

internal fun LsiAnnotation.toKotlinCoreAnnotationSpec(
    typeNames: List<LsiTypeName>,
): AnnotationSpec {
    return AnnotationSpec.builder(typeNames.requireKotlinClassName(type))
        .apply {
            useSiteTarget?.toPoetUseSiteTarget()?.let(::useSiteTarget)
            arguments.toSortedMap().forEach { (name, argument) ->
                if (argument.isExplicit) {
                    addMember(
                        "%N = %L",
                        name,
                        argument.value.toKotlinCoreAnnotationValue(typeNames),
                    )
                }
            }
        }
        .build()
}

internal fun LsiAnnotation.toKotlinSourceAnnotationSpec(
    typeNames: List<LsiTypeName>,
): AnnotationSpec {
    val sourceAnnotation = toSourceAnnotation()
    val sourceArguments = sourceAnnotation.sourceArguments
    return AnnotationSpec.builder(typeNames.requireKotlinClassName(type))
        .apply {
            useSiteTarget?.toPoetUseSiteTarget()?.let(::useSiteTarget)
            when (sourceAnnotation.argumentLayout) {
                LsiAnnotationArgumentLayout.PLATFORM_DEFAULT -> sourceArguments.forEach { argument ->
                    when (argument) {
                        is LsiSourceAnnotationArgument.Named -> addMember(
                            "%L = %L",
                            argument.toKotlinAnnotationArgumentName(),
                            argument.value.toKotlinSourceAnnotationValue(typeNames),
                        )
                        is LsiSourceAnnotationArgument.Positional -> addMember(
                            "%L",
                            argument.value.toKotlinSourceAnnotationValue(typeNames),
                        )
                    }
                }
                LsiAnnotationArgumentLayout.SINGLE_LINE -> if (sourceArguments.isNotEmpty()) {
                    addMember(sourceArguments.toKotlinSingleLineSourceAnnotationArguments(typeNames))
                }
                LsiAnnotationArgumentLayout.MULTI_LINE -> if (sourceArguments.isNotEmpty()) {
                    addMember(sourceArguments.toKotlinMultiLineSourceAnnotationArguments(typeNames))
                }
            }
        }
        .build()
}

private fun List<LsiSourceAnnotationArgument>.toKotlinSingleLineSourceAnnotationArguments(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    return CodeBlock.builder()
        .apply {
            forEachIndexed { index, argument ->
                if (index != 0) {
                    add(", ")
                }
                when (argument) {
                    is LsiSourceAnnotationArgument.Named -> add(
                        "%L = %L",
                        argument.toKotlinAnnotationArgumentName(),
                        argument.value.toKotlinSourceAnnotationValue(typeNames),
                    )
                    is LsiSourceAnnotationArgument.Positional -> add(
                        "%L",
                        argument.value.toKotlinSourceAnnotationValue(typeNames),
                    )
                }
            }
        }
        .build()
}

private fun List<LsiSourceAnnotationArgument>.toKotlinMultiLineSourceAnnotationArguments(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    return CodeBlock.builder()
        .add("\n")
        .indent()
        .apply {
            forEachIndexed { index, argument ->
                if (index != 0) {
                    add(", \n")
                }
                when (argument) {
                    is LsiSourceAnnotationArgument.Named -> add(
                        "%L = %L",
                        argument.toKotlinAnnotationArgumentName(),
                        argument.value.toKotlinSourceAnnotationValue(typeNames),
                    )
                    is LsiSourceAnnotationArgument.Positional -> add(
                        "%L",
                        argument.value.toKotlinSourceAnnotationValue(typeNames),
                    )
                }
            }
        }
        .unindent()
        .add("\n")
        .build()
}

private fun LsiPrimitiveKind.toKotlinTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> BOOLEAN
        LsiPrimitiveKind.BYTE -> BYTE
        LsiPrimitiveKind.SHORT -> SHORT
        LsiPrimitiveKind.INT -> INT
        LsiPrimitiveKind.LONG -> LONG
        LsiPrimitiveKind.CHAR -> CHAR
        LsiPrimitiveKind.FLOAT -> FLOAT
        LsiPrimitiveKind.DOUBLE -> DOUBLE
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> UNIT
    }
}

private fun LsiPrimitiveType.toKotlinPrimitiveTypeName(referenceContext: Boolean): TypeName {
    if (kind == LsiPrimitiveKind.VOID && (boxed || referenceContext)) {
        return JAVA_LANG_VOID
    }
    if (
        boxed &&
        !referenceContext &&
        nullability != LsiNullability.NULLABLE &&
        kind != LsiPrimitiveKind.UNIT
    ) {
        return kind.toKotlinBoxedTypeName()
    }
    return kind.toKotlinTypeName()
}

private fun LsiPrimitiveKind.toKotlinBoxedTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> ClassName("java.lang", "Boolean")
        LsiPrimitiveKind.BYTE -> ClassName("java.lang", "Byte")
        LsiPrimitiveKind.SHORT -> ClassName("java.lang", "Short")
        LsiPrimitiveKind.INT -> ClassName("java.lang", "Integer")
        LsiPrimitiveKind.LONG -> ClassName("java.lang", "Long")
        LsiPrimitiveKind.CHAR -> ClassName("java.lang", "Character")
        LsiPrimitiveKind.FLOAT -> ClassName("java.lang", "Float")
        LsiPrimitiveKind.DOUBLE -> ClassName("java.lang", "Double")
        LsiPrimitiveKind.UNIT -> UNIT
        LsiPrimitiveKind.VOID -> JAVA_LANG_VOID
    }
}

private fun LsiTypeRef.toKotlinArrayTypeName(typeNames: List<LsiTypeName>): TypeName {
    val primitiveType = this as? LsiPrimitiveType
    if (
        primitiveType != null &&
        !primitiveType.boxed &&
        primitiveType.nullability == LsiNullability.NON_NULL
    ) {
        primitiveType.kind.toKotlinPrimitiveArrayTypeName()?.let { return it }
    }
    return ClassName("kotlin", "Array").parameterizedBy(
        toKotlinTypeName(typeNames, referenceContext = true)
    )
}

private fun LsiPrimitiveKind.toKotlinPrimitiveArrayTypeName(): TypeName? {
    val simpleName = when (this) {
        LsiPrimitiveKind.BOOLEAN -> "BooleanArray"
        LsiPrimitiveKind.BYTE -> "ByteArray"
        LsiPrimitiveKind.SHORT -> "ShortArray"
        LsiPrimitiveKind.INT -> "IntArray"
        LsiPrimitiveKind.LONG -> "LongArray"
        LsiPrimitiveKind.CHAR -> "CharArray"
        LsiPrimitiveKind.FLOAT -> "FloatArray"
        LsiPrimitiveKind.DOUBLE -> "DoubleArray"
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> return null
    }
    return ClassName("kotlin", simpleName)
}

private fun LsiAnnotationValue.toKotlinCoreAnnotationValue(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ByteValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ShortValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.IntValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.LongValue -> CodeBlock.of("%LL", value)
        is LsiAnnotationValue.FloatValue -> CodeBlock.of("%LF", value)
        is LsiAnnotationValue.DoubleValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.CharValue -> CodeBlock.of("%L", value.toCharacterLiteral())
        is LsiAnnotationValue.StringValue -> CodeBlock.of("%S", value)
        is LsiAnnotationValue.EnumValue -> CodeBlock.of(
            "%T.%L",
            typeNames.requireKotlinClassName(enumType),
            entryName,
        )
        is LsiAnnotationValue.ClassValue -> type.toKotlinClassLiteral(typeNames)
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.toKotlinNestedCoreAnnotationValue(typeNames)
        is LsiAnnotationValue.ArrayValue -> CodeBlock.builder()
            .add("[")
            .apply {
                elements.forEachIndexed { index, element ->
                    if (index != 0) {
                        add(", ")
                    }
                    add("%L", element.toKotlinCoreAnnotationValue(typeNames))
                }
            }
            .add("]")
            .build()
    }
}

private fun LsiAnnotationValue.toKotlinSourceAnnotationValue(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ByteValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.ShortValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.IntValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.LongValue -> CodeBlock.of("%LL", value)
        is LsiAnnotationValue.FloatValue -> CodeBlock.of("%LF", value)
        is LsiAnnotationValue.DoubleValue -> CodeBlock.of("%L", value)
        is LsiAnnotationValue.CharValue -> CodeBlock.of("%L", value.toCharacterLiteral())
        is LsiAnnotationValue.StringValue -> CodeBlock.of("%S", value)
        is LsiAnnotationValue.EnumValue -> CodeBlock.of(
            "%T.%L",
            typeNames.requireKotlinClassName(enumType),
            entryName,
        )
        is LsiAnnotationValue.ClassValue -> type.toKotlinClassLiteral(typeNames, sourceStyle)
        is LsiAnnotationValue.NestedAnnotationValue -> annotation.toKotlinNestedSourceAnnotationValue(typeNames)
        is LsiAnnotationValue.ArrayValue -> when (sourceStyle) {
            LsiAnnotationArrayStyle.LITERAL -> toKotlinInlineSourceAnnotationArray(
                typeNames,
                opening = "[",
                closing = "]",
            )
            LsiAnnotationArrayStyle.LINE_SEPARATED_LITERAL -> CodeBlock.builder()
                .add("[")
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(",\n")
                        }
                        add("%L", element.toKotlinSourceAnnotationValue(typeNames))
                    }
                }
                .add("]")
                .build()
            LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF -> toKotlinInlineSourceAnnotationArray(
                typeNames,
                opening = "arrayOf(",
                closing = ")",
            )
            LsiAnnotationArrayStyle.MULTI_LINE_LITERAL -> CodeBlock.builder()
                .add("[\n")
                .indent()
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(", \n")
                        }
                        add("%L", element.toKotlinSourceAnnotationValue(typeNames))
                    }
                }
                .unindent()
                .add("\n]")
                .build()
            LsiAnnotationArrayStyle.COMPACT_MULTI_LINE_LITERAL -> CodeBlock.builder()
                .add("[\n")
                .indent()
                .apply {
                    elements.forEachIndexed { index, element ->
                        if (index != 0) {
                            add(",\n")
                        }
                        add("%L", element.toKotlinSourceAnnotationValue(typeNames))
                    }
                }
                .unindent()
                .add("\n]")
                .build()
        }
    }
}

private fun LsiAnnotationValue.ArrayValue.toKotlinInlineSourceAnnotationArray(
    typeNames: List<LsiTypeName>,
    opening: String,
    closing: String,
): CodeBlock {
    return CodeBlock.builder()
        .add(opening)
        .apply {
            elements.forEachIndexed { index, element ->
                if (index != 0) {
                    add(", ")
                }
                add("%L", element.toKotlinSourceAnnotationValue(typeNames))
            }
        }
        .add(closing)
        .build()
}

private fun LsiAnnotation.toKotlinNestedCoreAnnotationValue(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    require(useSiteTarget == null) {
        "Nested Kotlin annotation value cannot declare a use-site target: $type"
    }
    return CodeBlock.builder()
        .add("%T(", typeNames.requireKotlinClassName(type))
        .apply {
            arguments
                .asSequence()
                .filter { (_, argument) -> argument.isExplicit }
                .sortedBy { (name, _) -> name }
                .forEachIndexed { index, (name, argument) ->
                    if (index != 0) {
                        add(", ")
                    }
                    add(
                        "%N = %L",
                        name,
                        argument.value.toKotlinCoreAnnotationValue(typeNames),
                    )
                }
        }
        .add(")")
        .build()
}

private fun LsiAnnotation.toKotlinNestedSourceAnnotationValue(
    typeNames: List<LsiTypeName>,
): CodeBlock {
    require(useSiteTarget == null) {
        "Nested Kotlin annotation value cannot declare a use-site target: $type"
    }
    val sourceAnnotation = toSourceAnnotation()
    val sourceArguments = sourceAnnotation.sourceArguments
    return CodeBlock.builder()
        .add("%T(", typeNames.requireKotlinClassName(type))
        .apply {
            when (sourceAnnotation.argumentLayout) {
                LsiAnnotationArgumentLayout.PLATFORM_DEFAULT -> {
                    sourceArguments.forEachIndexed { index, argument ->
                        if (index != 0) {
                            add(", ")
                        }
                        when (argument) {
                            is LsiSourceAnnotationArgument.Named -> add(
                                "%L = %L",
                                argument.toKotlinAnnotationArgumentName(),
                                argument.value.toKotlinSourceAnnotationValue(typeNames),
                            )
                            is LsiSourceAnnotationArgument.Positional -> add(
                                "%L",
                                argument.value.toKotlinSourceAnnotationValue(typeNames),
                            )
                        }
                    }
                }
                LsiAnnotationArgumentLayout.SINGLE_LINE -> {
                    if (sourceArguments.isNotEmpty()) {
                        add(sourceArguments.toKotlinSingleLineSourceAnnotationArguments(typeNames))
                    }
                }
                LsiAnnotationArgumentLayout.MULTI_LINE -> {
                    if (sourceArguments.isNotEmpty()) {
                        add(sourceArguments.toKotlinMultiLineSourceAnnotationArguments(typeNames))
                    }
                }
            }
        }
        .add(")")
        .build()
}

private fun LsiSourceAnnotationArgument.Named.toKotlinAnnotationArgumentName(): CodeBlock {
    return when (nameStyle) {
        LsiAnnotationArgumentNameStyle.IDENTIFIER -> CodeBlock.of("%N", name)
        LsiAnnotationArgumentNameStyle.VERBATIM -> CodeBlock.of("%L", name)
    }
}

private fun LsiTypeRef.toKotlinClassLiteral(
    typeNames: List<LsiTypeName>,
    sourceStyle: LsiClassLiteralStyle = LsiClassLiteralStyle.PLATFORM_TYPE,
): CodeBlock {
    val primitive = this as? LsiPrimitiveType
    if (primitive?.kind == LsiPrimitiveKind.VOID && !primitive.boxed) {
        error("Kotlin annotation source cannot represent the primitive void class literal")
    }
    if (sourceStyle == LsiClassLiteralStyle.JAVA_BOXED_PRIMITIVE_QUALIFIED) {
        require(primitive?.boxed == true) {
            "Qualified Java boxed class literal requires a boxed primitive type: $this"
        }
        return CodeBlock.of("%L::class", primitive.kind.toJavaBoxedQualifiedName())
    }
    val typeName = if (primitive?.boxed == true) {
        primitive.kind.toKotlinBoxedTypeName()
    } else {
        toKotlinTypeName(typeNames)
    }
    return CodeBlock.of("%T::class", typeName.copy(nullable = false))
}

private fun LsiPrimitiveKind.toJavaBoxedQualifiedName(): String {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> "java.lang.Boolean"
        LsiPrimitiveKind.BYTE -> "java.lang.Byte"
        LsiPrimitiveKind.SHORT -> "java.lang.Short"
        LsiPrimitiveKind.INT -> "java.lang.Integer"
        LsiPrimitiveKind.LONG -> "java.lang.Long"
        LsiPrimitiveKind.CHAR -> "java.lang.Character"
        LsiPrimitiveKind.FLOAT -> "java.lang.Float"
        LsiPrimitiveKind.DOUBLE -> "java.lang.Double"
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> error("Kotlin has no Java boxed primitive class literal for $name")
    }
}

private fun LsiAnnotationUseSiteTarget.toPoetUseSiteTarget(): AnnotationSpec.UseSiteTarget? {
    return when (this) {
        LsiAnnotationUseSiteTarget.FILE -> AnnotationSpec.UseSiteTarget.FILE
        LsiAnnotationUseSiteTarget.PROPERTY -> AnnotationSpec.UseSiteTarget.PROPERTY
        LsiAnnotationUseSiteTarget.FIELD -> AnnotationSpec.UseSiteTarget.FIELD
        LsiAnnotationUseSiteTarget.GETTER -> AnnotationSpec.UseSiteTarget.GET
        LsiAnnotationUseSiteTarget.SETTER -> AnnotationSpec.UseSiteTarget.SET
        LsiAnnotationUseSiteTarget.RECEIVER -> AnnotationSpec.UseSiteTarget.RECEIVER
        LsiAnnotationUseSiteTarget.PARAMETER -> AnnotationSpec.UseSiteTarget.PARAM
        LsiAnnotationUseSiteTarget.SET_PARAMETER -> AnnotationSpec.UseSiteTarget.SETPARAM
        LsiAnnotationUseSiteTarget.DELEGATE -> AnnotationSpec.UseSiteTarget.DELEGATE
        LsiAnnotationUseSiteTarget.PACKAGE,
        LsiAnnotationUseSiteTarget.TYPE,
        LsiAnnotationUseSiteTarget.CONSTRUCTOR,
        LsiAnnotationUseSiteTarget.METHOD,
        LsiAnnotationUseSiteTarget.RETURN_TYPE,
        -> null
        LsiAnnotationUseSiteTarget.ALL -> error(
            "KotlinPoet renderer cannot emit the Kotlin ALL annotation use-site target"
        )
    }
}

private fun List<LsiTypeName>.requireKotlinClassName(typeId: LsiSymbolId): ClassName {
    return requireTypeName(typeId).toKotlinClassName()
}

private fun List<LsiTypeName>.requireTypeName(typeId: LsiSymbolId): LsiTypeName {
    val matches = filter { typeName -> typeName.typeId == typeId }
    require(matches.size == 1) {
        "KotlinPoet renderer requires exactly one source type name for $typeId, found ${matches.size}"
    }
    return matches.single()
}

private fun LsiTypeName.toKotlinClassName(): ClassName = ClassName(packageName, simpleNames)

private fun Char.toCharacterLiteral(): String {
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

private val KOTLIN_TYPES = mapOf(
    exactTypeName("java.lang", "Boolean") to BOOLEAN,
    exactTypeName("java.lang", "Byte") to BYTE,
    exactTypeName("java.lang", "Short") to SHORT,
    exactTypeName("java.lang", "Integer") to INT,
    exactTypeName("java.lang", "Long") to LONG,
    exactTypeName("java.lang", "Character") to CHAR,
    exactTypeName("java.lang", "Float") to FLOAT,
    exactTypeName("java.lang", "Double") to DOUBLE,
    exactTypeName("java.lang", "String") to STRING,
    exactTypeName("java.lang", "Object") to ANY,
    exactTypeName("java.lang", "Iterable") to ClassName("kotlin.collections", "Iterable"),
    exactTypeName("java.util", "Collection") to ClassName("kotlin.collections", "Collection"),
    exactTypeName("java.util", "Iterator") to ClassName("kotlin.collections", "Iterator"),
    exactTypeName("java.util", "List") to ClassName("kotlin.collections", "List"),
    exactTypeName("java.util", "ListIterator") to ClassName("kotlin.collections", "ListIterator"),
    exactTypeName("java.util", "Map") to ClassName("kotlin.collections", "Map"),
    exactTypeName("java.util", "Map", "Entry") to ClassName("kotlin.collections", "Map", "Entry"),
    exactTypeName("java.util", "Set") to ClassName("kotlin.collections", "Set"),
    exactTypeName("kotlin", "Boolean") to BOOLEAN,
    exactTypeName("kotlin", "Byte") to BYTE,
    exactTypeName("kotlin", "Short") to SHORT,
    exactTypeName("kotlin", "Int") to INT,
    exactTypeName("kotlin", "Long") to LONG,
    exactTypeName("kotlin", "Char") to CHAR,
    exactTypeName("kotlin", "Float") to FLOAT,
    exactTypeName("kotlin", "Double") to DOUBLE,
    exactTypeName("kotlin", "String") to STRING,
    exactTypeName("kotlin", "Any") to ANY,
    exactTypeName("kotlin", "Unit") to UNIT,
)

private fun exactTypeName(packageName: String, vararg simpleNames: String): LsiTypeName {
    val canonicalName = (listOf(packageName) + simpleNames).filter(String::isNotEmpty).joinToString(".")
    return LsiTypeName(
        typeId = LsiSymbolId.type(canonicalName),
        packageName = packageName,
        simpleNames = simpleNames.toList(),
    )
}

private val JAVA_LANG_VOID = ClassName("java.lang", "Void")
