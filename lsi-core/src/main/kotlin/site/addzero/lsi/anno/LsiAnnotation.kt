package site.addzero.lsi.anno

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiAnnotation
import site.addzero.lsi.type.LsiType

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
    val origin: LsiAnnotationArgumentOrigin,
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

/** 保留注解参数的精确类型，避免共享层依赖前端常量对象。 */
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
        val entryName: String,
    ) : LsiAnnotationValue {
        init {
            require(entryName.isNotBlank()) { "LSI enum annotation value entry name cannot be blank" }
        }
    }

    data class ClassValue(
        val type: LsiType,
        val sourceStyle: LsiClassLiteralStyle = LsiClassLiteralStyle.PLATFORM_TYPE,
    ) : LsiAnnotationValue

    data class NestedAnnotationValue(val annotation: LsiAnnotation) : LsiAnnotationValue

    data class ArrayValue(
        val elements: List<LsiAnnotationValue>,
        val sourceStyle: LsiAnnotationArrayStyle = LsiAnnotationArrayStyle.LITERAL,
    ) : LsiAnnotationValue
}

/** 语言无关、结构化的注解实例。 */
interface LsiAnnotation {
    val type: LsiSymbolId
    val arguments: Map<String, LsiAnnotationArgument>
    val useSiteTarget: LsiAnnotationUseSiteTarget?
    val explicitArgumentNamesInSourceOrder: List<String>
    val sourceArguments: List<LsiSourceAnnotationArgument>
    val argumentLayout: LsiAnnotationArgumentLayout
}

fun LsiAnnotation(
    type: LsiSymbolId,
    arguments: Map<String, LsiAnnotationArgument> = emptyMap(),
    useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    explicitArgumentNamesInSourceOrder: List<String> = emptyList(),
    sourceArguments: List<LsiSourceAnnotationArgument> = emptyList(),
    argumentLayout: LsiAnnotationArgumentLayout = LsiAnnotationArgumentLayout.PLATFORM_DEFAULT,
): LsiAnnotation = FrozenLsiAnnotation(
    type = type,
    arguments = arguments,
    useSiteTarget = useSiteTarget,
    explicitArgumentNamesInSourceOrder = explicitArgumentNamesInSourceOrder,
    sourceArguments = sourceArguments,
    argumentLayout = argumentLayout,
)

operator fun LsiAnnotation.get(name: String): LsiAnnotationArgument? = arguments[name]

fun LsiAnnotation.copy(
    type: LsiSymbolId = this.type,
    arguments: Map<String, LsiAnnotationArgument> = this.arguments,
    useSiteTarget: LsiAnnotationUseSiteTarget? = this.useSiteTarget,
    explicitArgumentNamesInSourceOrder: List<String> = this.explicitArgumentNamesInSourceOrder,
    sourceArguments: List<LsiSourceAnnotationArgument> = this.sourceArguments,
    argumentLayout: LsiAnnotationArgumentLayout = this.argumentLayout,
): LsiAnnotation = LsiAnnotation(
    type = type,
    arguments = arguments,
    useSiteTarget = useSiteTarget,
    explicitArgumentNamesInSourceOrder = explicitArgumentNamesInSourceOrder,
    sourceArguments = sourceArguments,
    argumentLayout = argumentLayout,
)

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
