package site.addzero.lsi.model

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiTypeArgument
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.type.LsiVariance

/**
 * 提供不依赖平台类型对象的确定性类型签名，类型使用注解不参与符号标识。
 */
fun LsiType.stableSignature(): String {
    val base = when (this) {
        is LsiDeclaredType -> buildString {
            append(declarationId.value)
            if (arguments.isNotEmpty()) {
                append('<')
                append(arguments.joinToString(",") { argument -> argument.stableSignature() })
                append('>')
            }
        }
        is LsiTypeParameterRef -> "parameter:${parameterId.value}"
        is LsiPrimitiveType -> buildString {
            append("primitive:")
            append(kind.name.lowercase())
            if (boxed) {
                append(":boxed")
            }
        }
        is LsiArrayType -> "array:${elementType.stableSignature()}"
        is LsiFunctionType -> buildString {
            append("function:")
            append(if (suspending) "suspend" else "regular")
            receiverType?.let { receiver ->
                append(":receiver:")
                append(receiver.stableSignature())
            }
            append(":parameters:[")
            append(parameterTypes.joinToString(",") { parameter -> parameter.stableSignature() })
            append("]:return:")
            append(returnType.stableSignature())
        }
        is LsiUnresolvedType -> "unresolved:$displayName"
    }
    return base + nullability.stableSuffix()
}

private fun LsiTypeArgument.stableSignature(): String = when (variance) {
    LsiVariance.STAR -> "*"
    LsiVariance.INVARIANT -> requireNotNull(type).stableSignature()
    LsiVariance.IN -> "in:${requireNotNull(type).stableSignature()}"
    LsiVariance.OUT -> "out:${requireNotNull(type).stableSignature()}"
}

private fun LsiNullability.stableSuffix(): String = when (this) {
    LsiNullability.NON_NULL -> "!non-null"
    LsiNullability.NULLABLE -> "?nullable"
    LsiNullability.PLATFORM -> "!platform"
    LsiNullability.UNKNOWN -> "?unknown"
}

fun LsiTypeParameter.stableSignature(): String {
    return buildString {
        append("type-parameter(")
        appendToken(id.value)
        appendToken(name)
        appendToken(variance.name)
        upperBounds.forEach { bound -> appendToken(bound.stableSignature()) }
        append(')')
    }
}

fun LsiAnnotation.stableSignature(): String {
    return buildString {
        append("annotation(")
        appendToken(type.value)
        appendToken(useSiteTarget?.name.orEmpty())
        arguments.toSortedMap().forEach { (name, argument) ->
            appendToken(name)
            appendToken(argument.origin.name)
            appendToken(argument.value.stableSignature())
        }
        append(')')
    }
}

fun LsiAnnotationValue.stableSignature(): String {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> "boolean:$value"
        is LsiAnnotationValue.ByteValue -> "byte:$value"
        is LsiAnnotationValue.ShortValue -> "short:$value"
        is LsiAnnotationValue.IntValue -> "int:$value"
        is LsiAnnotationValue.LongValue -> "long:$value"
        is LsiAnnotationValue.FloatValue -> "float:${value.toRawBits()}"
        is LsiAnnotationValue.DoubleValue -> "double:${value.toRawBits()}"
        is LsiAnnotationValue.CharValue -> "char:${value.code}"
        is LsiAnnotationValue.StringValue -> buildString {
            append("string(")
            appendToken(value)
            append(')')
        }
        is LsiAnnotationValue.EnumValue -> buildString {
            append("enum(")
            appendToken(enumType.value)
            appendToken(entryName)
            append(')')
        }
        is LsiAnnotationValue.ClassValue -> buildString {
            append("class(")
            appendToken(type.stableSignature())
            append(')')
        }
        is LsiAnnotationValue.NestedAnnotationValue -> buildString {
            append("nested(")
            appendToken(annotation.stableSignature())
            append(')')
        }
        is LsiAnnotationValue.ArrayValue -> buildString {
            append("array(")
            elements.forEach { element -> appendToken(element.stableSignature()) }
            append(')')
        }
    }
}

private fun StringBuilder.appendToken(value: String) {
    append(value.length)
    append(':')
    append(value)
}
