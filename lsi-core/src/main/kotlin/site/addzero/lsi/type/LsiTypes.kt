package site.addzero.lsi.type

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation

enum class LsiNullability {
    NON_NULL,
    NULLABLE,
    PLATFORM,
    UNKNOWN
}

enum class LsiVariance {
    INVARIANT,
    IN,
    OUT,
    STAR
}

data class LsiTypeArgument(
    val variance: LsiVariance = LsiVariance.INVARIANT,
    val type: LsiType? = null
) {

    init {
        if (variance == LsiVariance.STAR) {
            require(type == null) { "Star-projected LSI type argument cannot have a type" }
        } else {
            requireNotNull(type) { "Non-star LSI type argument requires a type" }
        }
    }

    companion object {
        val STAR: LsiTypeArgument = LsiTypeArgument(LsiVariance.STAR)

        fun invariant(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.INVARIANT, type)

        fun input(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.IN, type)

        fun output(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.OUT, type)
    }
}

data class LsiDeclaredType(
    val declarationId: LsiSymbolId,
    val arguments: List<LsiTypeArgument> = emptyList(),
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiType

data class LsiTypeParameterRef(
    val parameterId: LsiSymbolId,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiType

enum class LsiPrimitiveKind {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    CHAR,
    FLOAT,
    DOUBLE,
    UNIT,
    VOID
}

data class LsiPrimitiveType(
    val kind: LsiPrimitiveKind,
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
    val boxed: Boolean = false,
) : LsiType

data class LsiArrayType(
    val elementType: LsiType,
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiType

/**
 * 保留函数的返回值、接收者、参数和挂起语义，不提前折叠为平台声明类型。
 */
data class LsiFunctionType(
    val returnType: LsiType,
    val receiverType: LsiType? = null,
    val parameterTypes: List<LsiType> = emptyList(),
    val suspending: Boolean = false,
    override val nullability: LsiNullability = LsiNullability.NON_NULL,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiType

/**
 * 前端暂时无法闭合的类型，不允许渲染器把它当作合法类型消费。
 */
data class LsiUnresolvedType(
    val displayName: String,
    override val nullability: LsiNullability = LsiNullability.UNKNOWN,
    override val annotations: List<LsiAnnotation> = emptyList(),
) : LsiType {

    init {
        require(displayName.isNotBlank()) { "Unresolved LSI type display name cannot be blank" }
    }
}

data class LsiTypeParameter(
    val id: LsiSymbolId,
    val name: String,
    val variance: LsiVariance = LsiVariance.INVARIANT,
    val upperBounds: List<LsiType> = emptyList()
) {

    init {
        require(name.isNotBlank()) { "LSI type parameter name cannot be blank" }
        require(variance != LsiVariance.STAR) { "LSI type parameter declaration cannot use star variance" }
    }
}

/**
 * 注解成员类型在语言模型中不可空，递归消除前端投影产生的平台可空性差异。
 */
fun LsiType.toAnnotationMemberType(): LsiType {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = arguments.map { argument ->
                argument.copy(type = argument.type?.toAnnotationMemberType())
            },
            nullability = LsiNullability.NON_NULL,
        )
        is LsiTypeParameterRef -> copy(nullability = LsiNullability.NON_NULL)
        is LsiPrimitiveType -> copy(nullability = LsiNullability.NON_NULL)
        is LsiArrayType -> copy(
            elementType = elementType.toAnnotationMemberType(),
            nullability = LsiNullability.NON_NULL,
        )
        is LsiFunctionType -> copy(
            returnType = returnType.toAnnotationMemberType(),
            receiverType = receiverType?.toAnnotationMemberType(),
            parameterTypes = parameterTypes.map(LsiType::toAnnotationMemberType),
            nullability = LsiNullability.NON_NULL,
        )
        is LsiUnresolvedType -> copy(nullability = LsiNullability.NON_NULL)
    }
}
