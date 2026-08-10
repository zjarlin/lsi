package site.addzero.lsi.type

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.core.LsiSymbolId

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

/** 语言无关的泛型实参接口。 */
interface LsiTypeArgument {
    val variance: LsiVariance
    val type: LsiType?

    companion object {
        val STAR: LsiTypeArgument = LsiTypeArgument(LsiVariance.STAR)

        fun invariant(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.INVARIANT, type)

        fun input(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.IN, type)

        fun output(type: LsiType): LsiTypeArgument = LsiTypeArgument(LsiVariance.OUT, type)
    }
}

internal data class FrozenLsiTypeArgument(
    override val variance: LsiVariance,
    override val type: LsiType?,
) : LsiTypeArgument {
    init {
        if (variance == LsiVariance.STAR) {
            require(type == null) { "Star-projected LSI type argument cannot have a type" }
        } else {
            requireNotNull(type) { "Non-star LSI type argument requires a type" }
        }
    }
}

fun LsiTypeArgument(
    variance: LsiVariance = LsiVariance.INVARIANT,
    type: LsiType? = null,
): LsiTypeArgument = FrozenLsiTypeArgument(variance, type)

fun LsiTypeArgument.copy(
    variance: LsiVariance = this.variance,
    type: LsiType? = this.type,
): LsiTypeArgument = LsiTypeArgument(variance, type)

/** 具有稳定声明身份的类型使用接口。 */
interface LsiDeclaredType : LsiType {
    val declarationId: LsiSymbolId
    val arguments: List<LsiTypeArgument>
}

internal data class FrozenLsiDeclaredType(
    override val declarationId: LsiSymbolId,
    override val arguments: List<LsiTypeArgument>,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
) : LsiDeclaredType

fun LsiDeclaredType(
    declarationId: LsiSymbolId,
    arguments: List<LsiTypeArgument> = emptyList(),
    nullability: LsiNullability = LsiNullability.NON_NULL,
    annotations: List<LsiAnnotation> = emptyList(),
): LsiDeclaredType = FrozenLsiDeclaredType(declarationId, arguments, nullability, annotations)

fun LsiDeclaredType.copy(
    declarationId: LsiSymbolId = this.declarationId,
    arguments: List<LsiTypeArgument> = this.arguments,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
): LsiDeclaredType = LsiDeclaredType(declarationId, arguments, nullability, annotations)

/** 指向声明处类型参数的类型使用接口。 */
interface LsiTypeParameterRef : LsiType {
    val parameterId: LsiSymbolId
}

internal data class FrozenLsiTypeParameterRef(
    override val parameterId: LsiSymbolId,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
) : LsiTypeParameterRef

fun LsiTypeParameterRef(
    parameterId: LsiSymbolId,
    nullability: LsiNullability = LsiNullability.UNKNOWN,
    annotations: List<LsiAnnotation> = emptyList(),
): LsiTypeParameterRef = FrozenLsiTypeParameterRef(parameterId, nullability, annotations)

fun LsiTypeParameterRef.copy(
    parameterId: LsiSymbolId = this.parameterId,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
): LsiTypeParameterRef = LsiTypeParameterRef(parameterId, nullability, annotations)

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

/** 原始类型使用接口。 */
interface LsiPrimitiveType : LsiType {
    val kind: LsiPrimitiveKind
    val boxed: Boolean
}

internal data class FrozenLsiPrimitiveType(
    override val kind: LsiPrimitiveKind,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
    override val boxed: Boolean,
) : LsiPrimitiveType

fun LsiPrimitiveType(
    kind: LsiPrimitiveKind,
    nullability: LsiNullability = LsiNullability.NON_NULL,
    annotations: List<LsiAnnotation> = emptyList(),
    boxed: Boolean = false,
): LsiPrimitiveType = FrozenLsiPrimitiveType(kind, nullability, annotations, boxed)

fun LsiPrimitiveType.copy(
    kind: LsiPrimitiveKind = this.kind,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
    boxed: Boolean = this.boxed,
): LsiPrimitiveType = LsiPrimitiveType(kind, nullability, annotations, boxed)

/** 数组类型使用接口。 */
interface LsiArrayType : LsiType {
    val elementType: LsiType
}

internal data class FrozenLsiArrayType(
    override val elementType: LsiType,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
) : LsiArrayType

fun LsiArrayType(
    elementType: LsiType,
    nullability: LsiNullability = LsiNullability.NON_NULL,
    annotations: List<LsiAnnotation> = emptyList(),
): LsiArrayType = FrozenLsiArrayType(elementType, nullability, annotations)

fun LsiArrayType.copy(
    elementType: LsiType = this.elementType,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
): LsiArrayType = LsiArrayType(elementType, nullability, annotations)

/**
 * 保留函数的返回值、接收者、参数和挂起语义，不提前折叠为平台声明类型。
 */
interface LsiFunctionType : LsiType {
    val returnType: LsiType
    val receiverType: LsiType?
    val parameterTypes: List<LsiType>
    val suspending: Boolean
}

internal data class FrozenLsiFunctionType(
    override val returnType: LsiType,
    override val receiverType: LsiType?,
    override val parameterTypes: List<LsiType>,
    override val suspending: Boolean,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
) : LsiFunctionType

fun LsiFunctionType(
    returnType: LsiType,
    receiverType: LsiType? = null,
    parameterTypes: List<LsiType> = emptyList(),
    suspending: Boolean = false,
    nullability: LsiNullability = LsiNullability.NON_NULL,
    annotations: List<LsiAnnotation> = emptyList(),
): LsiFunctionType = FrozenLsiFunctionType(
    returnType,
    receiverType,
    parameterTypes,
    suspending,
    nullability,
    annotations,
)

fun LsiFunctionType.copy(
    returnType: LsiType = this.returnType,
    receiverType: LsiType? = this.receiverType,
    parameterTypes: List<LsiType> = this.parameterTypes,
    suspending: Boolean = this.suspending,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
): LsiFunctionType = LsiFunctionType(
    returnType,
    receiverType,
    parameterTypes,
    suspending,
    nullability,
    annotations,
)

/**
 * 前端暂时无法闭合的类型，不允许渲染器把它当作合法类型消费。
 */
interface LsiUnresolvedType : LsiType {
    val displayName: String
}

internal data class FrozenLsiUnresolvedType(
    override val displayName: String,
    override val nullability: LsiNullability,
    override val annotations: List<LsiAnnotation>,
) : LsiUnresolvedType {
    init {
        require(displayName.isNotBlank()) { "Unresolved LSI type display name cannot be blank" }
    }
}

fun LsiUnresolvedType(
    displayName: String,
    nullability: LsiNullability = LsiNullability.UNKNOWN,
    annotations: List<LsiAnnotation> = emptyList(),
): LsiUnresolvedType = FrozenLsiUnresolvedType(displayName, nullability, annotations)

fun LsiUnresolvedType.copy(
    displayName: String = this.displayName,
    nullability: LsiNullability = this.nullability,
    annotations: List<LsiAnnotation> = this.annotations,
): LsiUnresolvedType = LsiUnresolvedType(displayName, nullability, annotations)

/** 语言无关的类型参数声明接口。 */
interface LsiTypeParameter {
    val id: LsiSymbolId
    val name: String
    val variance: LsiVariance
    val upperBounds: List<LsiType>
}

internal data class FrozenLsiTypeParameter(
    override val id: LsiSymbolId,
    override val name: String,
    override val variance: LsiVariance,
    override val upperBounds: List<LsiType>,
) : LsiTypeParameter {
    init {
        require(name.isNotBlank()) { "LSI type parameter name cannot be blank" }
        require(variance != LsiVariance.STAR) { "LSI type parameter declaration cannot use star variance" }
    }
}

fun LsiTypeParameter(
    id: LsiSymbolId,
    name: String,
    variance: LsiVariance = LsiVariance.INVARIANT,
    upperBounds: List<LsiType> = emptyList(),
): LsiTypeParameter = FrozenLsiTypeParameter(id, name, variance, upperBounds)

fun LsiTypeParameter.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    variance: LsiVariance = this.variance,
    upperBounds: List<LsiType> = this.upperBounds,
): LsiTypeParameter = LsiTypeParameter(id, name, variance, upperBounds)

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
