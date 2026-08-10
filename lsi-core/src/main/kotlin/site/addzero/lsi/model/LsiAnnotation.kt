package site.addzero.lsi.model

import site.addzero.lsi.core.LsiSymbolId

enum class LsiAnnotationUseSiteTarget {
    PACKAGE,
    FILE,
    TYPE,
    CONSTRUCTOR,
    METHOD,
    RETURN_TYPE,
    PROPERTY,
    FIELD,
    GETTER,
    SETTER,
    RECEIVER,
    PARAMETER,
    SET_PARAMETER,
    DELEGATE,
    ALL
}

enum class LsiAnnotationArgumentOrigin {
    EXPLICIT,
    DEFAULT
}

enum class LsiAnnotationArgumentLayout {
    PLATFORM_DEFAULT,
    SINGLE_LINE,
    MULTI_LINE,
}

enum class LsiAnnotationArgumentNameStyle {
    IDENTIFIER,
    VERBATIM,
}

enum class LsiClassLiteralStyle {
    PLATFORM_TYPE,
    JAVA_BOXED_PRIMITIVE_QUALIFIED,
}

enum class LsiAnnotationArrayStyle {
    LITERAL,
    LINE_SEPARATED_LITERAL,
    MULTI_LINE_LITERAL,
    COMPACT_MULTI_LINE_LITERAL,
    KOTLIN_ARRAY_OF,
}

data class LsiAnnotationArgument(
    val value: LsiAnnotationValue,
    val origin: LsiAnnotationArgumentOrigin
) {
    val isExplicit: Boolean
        get() = origin == LsiAnnotationArgumentOrigin.EXPLICIT
}

sealed interface LsiSourceAnnotationArgument {
    val value: LsiAnnotationValue

    data class Positional(
        override val value: LsiAnnotationValue,
    ) : LsiSourceAnnotationArgument

    data class Named(
        val name: String,
        override val value: LsiAnnotationValue,
        val nameStyle: LsiAnnotationArgumentNameStyle = LsiAnnotationArgumentNameStyle.IDENTIFIER,
    ) : LsiSourceAnnotationArgument {
        init {
            require(name.isNotBlank()) { "LSI annotation argument name cannot be blank" }
        }
    }
}

/**
 * 保留注解参数的精确类型，避免共享层依赖前端常量对象。
 */
sealed interface LsiAnnotationValue {

    data class BooleanValue(val value: Boolean) : LsiAnnotationValue

    data class ByteValue(val value: Byte) : LsiAnnotationValue

    data class ShortValue(val value: Short) : LsiAnnotationValue

    data class IntValue(val value: Int) : LsiAnnotationValue

    data class LongValue(val value: Long) : LsiAnnotationValue

    data class FloatValue(val value: Float) : LsiAnnotationValue

    data class DoubleValue(val value: Double) : LsiAnnotationValue

    data class CharValue(val value: Char) : LsiAnnotationValue

    data class StringValue(val value: String) : LsiAnnotationValue

    data class EnumValue(
        val enumType: LsiSymbolId,
        val entryName: String
    ) : LsiAnnotationValue {

        init {
            require(entryName.isNotBlank()) { "LSI enum annotation value entry name cannot be blank" }
        }
    }

    data class ClassValue(
        val type: LsiTypeRef,
        val sourceStyle: LsiClassLiteralStyle = LsiClassLiteralStyle.PLATFORM_TYPE,
    ) : LsiAnnotationValue

    data class NestedAnnotationValue(val annotation: LsiAnnotation) : LsiAnnotationValue

    data class ArrayValue(
        val elements: List<LsiAnnotationValue>,
        val sourceStyle: LsiAnnotationArrayStyle = LsiAnnotationArrayStyle.LITERAL,
    ) : LsiAnnotationValue
}

data class LsiAnnotation(
    val type: LsiSymbolId,
    val arguments: Map<String, LsiAnnotationArgument> = emptyMap(),
    val useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    val explicitArgumentNamesInSourceOrder: List<String> = emptyList(),
    val sourceArguments: List<LsiSourceAnnotationArgument> = emptyList(),
    val argumentLayout: LsiAnnotationArgumentLayout = LsiAnnotationArgumentLayout.PLATFORM_DEFAULT,
) {

    init {
        require(arguments.keys.none(String::isBlank)) { "LSI annotation argument name cannot be blank" }
        require(explicitArgumentNamesInSourceOrder.distinct().size == explicitArgumentNamesInSourceOrder.size) {
            "LSI annotation explicit argument order cannot contain duplicate names: ${type.value}"
        }
        require(
            explicitArgumentNamesInSourceOrder.isEmpty() ||
                explicitArgumentNamesInSourceOrder.toSet() == arguments
                    .filterValues(LsiAnnotationArgument::isExplicit)
                    .keys
        ) {
            "LSI annotation explicit argument order must contain every explicit argument: ${type.value}"
        }
        val namedArguments = sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Named>()
        require(namedArguments.map(LsiSourceAnnotationArgument.Named::name).distinct().size == namedArguments.size) {
            "LSI annotation cannot declare duplicate named source arguments: $type"
        }
        var namedArgumentObserved = false
        sourceArguments.forEach { argument ->
            when (argument) {
                is LsiSourceAnnotationArgument.Named -> namedArgumentObserved = true
                is LsiSourceAnnotationArgument.Positional -> require(!namedArgumentObserved) {
                    "LSI positional annotation arguments must precede named arguments: $type"
                }
            }
        }
    }

    operator fun get(name: String): LsiAnnotationArgument? = arguments[name]
}

fun sourceLsiAnnotation(
    type: LsiSymbolId,
    arguments: List<LsiSourceAnnotationArgument> = emptyList(),
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    argumentLayout: LsiAnnotationArgumentLayout = LsiAnnotationArgumentLayout.PLATFORM_DEFAULT,
): LsiAnnotation {
    return LsiAnnotation(
        type = type,
        useSiteTarget = useSiteTarget,
        sourceArguments = arguments,
        argumentLayout = argumentLayout,
    )
}

fun LsiAnnotation.toSourceAnnotation(): LsiAnnotation {
    val loweredArguments = if (sourceArguments.isNotEmpty()) {
        sourceArguments.map(LsiSourceAnnotationArgument::toSourceAnnotationArgument)
    } else {
        val orderedNames = explicitArgumentNamesInSourceOrder.takeIf(List<String>::isNotEmpty)
            ?: arguments.filterValues(LsiAnnotationArgument::isExplicit).keys.sorted()
        orderedNames.map { name ->
            val argument = requireNotNull(arguments[name])
            LsiSourceAnnotationArgument.Named(name, argument.value.toSourceAnnotationValue())
        }
    }
    return if (loweredArguments == sourceArguments) this else copy(sourceArguments = loweredArguments)
}

private fun LsiSourceAnnotationArgument.toSourceAnnotationArgument(): LsiSourceAnnotationArgument = when (this) {
    is LsiSourceAnnotationArgument.Named -> copy(value = value.toSourceAnnotationValue())
    is LsiSourceAnnotationArgument.Positional -> copy(value = value.toSourceAnnotationValue())
}

private fun LsiAnnotationValue.toSourceAnnotationValue(): LsiAnnotationValue = when (this) {
    is LsiAnnotationValue.NestedAnnotationValue -> copy(annotation = annotation.toSourceAnnotation())
    is LsiAnnotationValue.ArrayValue -> copy(elements = elements.map(LsiAnnotationValue::toSourceAnnotationValue))
    else -> this
}
