package site.addzero.lsi.jimmer

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiFunctionType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.type.copy

/**
 * 不可变属性注册到 Draft 运行时所需的语言无关语义。
 *
 * 该模型只描述属性角色、值形态和擦除后的运行时类型，不包含 Java 或 Kotlin 生成细节。
 */
data class ImmutableDraftRuntimeProp(
    val kind: ImmutableDraftRuntimePropKind,
    val valueCategory: ImmutablePropValueCategory,
    val associationAnnotationTypeId: LsiSymbolId?,
    val metadataElementType: LsiType,
) {
    init {
        require(
            (kind == ImmutableDraftRuntimePropKind.KEY_REFERENCE ||
                kind == ImmutableDraftRuntimePropKind.ASSOCIATION) ==
                (associationAnnotationTypeId != null)
        ) {
            "Immutable draft runtime association metadata must match its property kind"
        }
        require(metadataElementType.isErasedMetadataType()) {
            "Immutable draft runtime metadata element type must be erased"
        }
    }
}

/** Draft 运行时中的属性角色。 */
enum class ImmutableDraftRuntimePropKind {
    ID,
    VERSION,
    LOGICAL_DELETED,
    KEY_SCALAR,
    KEY_REFERENCE,
    ASSOCIATION,
    VALUE,
}

/** 不可变属性在语义与生成阶段共享的值形态。 */
enum class ImmutablePropValueCategory {
    SCALAR,
    SCALAR_LIST,
    REFERENCE,
    REFERENCE_LIST,
}

/**
 * 将 schema 中的不可变属性冻结为 Draft 运行时语义。
 */
fun ImmutableSchema.toDraftRuntimeProp(prop: ImmutableProp): ImmutableDraftRuntimeProp {
    require(propsById[prop.id] == prop) {
        "Immutable draft runtime property must belong to its schema: ${prop.id.value}"
    }
    return prop.freezeDraftRuntimeProp(
        elementType = prop.elementTypeOrSelf(),
        immutableReference = isImmutableReference(prop),
    )
}

private fun ImmutableProp.freezeDraftRuntimeProp(
    elementType: LsiType,
    immutableReference: Boolean,
): ImmutableDraftRuntimeProp {
    val key = annotations.any { annotation ->
        annotation.type == KEY_ANNOTATION_TYPE_ID || annotation.type == KEYS_ANNOTATION_TYPE_ID
    }
    val kind = when {
        primaryMapping == PrimaryMapping.ID -> ImmutableDraftRuntimePropKind.ID
        primaryMapping == PrimaryMapping.VERSION -> ImmutableDraftRuntimePropKind.VERSION
        primaryMapping == PrimaryMapping.LOGICAL_DELETED -> {
            ImmutableDraftRuntimePropKind.LOGICAL_DELETED
        }
        key && immutableReference -> ImmutableDraftRuntimePropKind.KEY_REFERENCE
        key -> ImmutableDraftRuntimePropKind.KEY_SCALAR
        associationKind.hasRuntimeAnnotation -> ImmutableDraftRuntimePropKind.ASSOCIATION
        else -> ImmutableDraftRuntimePropKind.VALUE
    }
    val valueCategory = when {
        list && immutableReference -> ImmutablePropValueCategory.REFERENCE_LIST
        list -> ImmutablePropValueCategory.SCALAR_LIST
        immutableReference -> ImmutablePropValueCategory.REFERENCE
        else -> ImmutablePropValueCategory.SCALAR
    }
    val associationAnnotationTypeId = when (kind) {
        ImmutableDraftRuntimePropKind.KEY_REFERENCE -> {
            if (associationKind == AssociationKind.ONE_TO_ONE) {
                ONE_TO_ONE_ANNOTATION_TYPE_ID
            } else {
                MANY_TO_ONE_ANNOTATION_TYPE_ID
            }
        }
        ImmutableDraftRuntimePropKind.ASSOCIATION -> associationKind.runtimeAnnotationTypeId()
        ImmutableDraftRuntimePropKind.ID,
        ImmutableDraftRuntimePropKind.VERSION,
        ImmutableDraftRuntimePropKind.LOGICAL_DELETED,
        ImmutableDraftRuntimePropKind.KEY_SCALAR,
        ImmutableDraftRuntimePropKind.VALUE,
        -> null
    }
    return ImmutableDraftRuntimeProp(
        kind = kind,
        valueCategory = valueCategory,
        associationAnnotationTypeId = associationAnnotationTypeId,
        metadataElementType = elementType.toErasedMetadataType(),
    )
}

private val AssociationKind.hasRuntimeAnnotation: Boolean
    get() = this != AssociationKind.NONE && this != AssociationKind.IMPLICIT

private fun AssociationKind.runtimeAnnotationTypeId(): LsiSymbolId {
    return when (this) {
        AssociationKind.ONE_TO_ONE -> ONE_TO_ONE_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_ONE -> MANY_TO_ONE_ANNOTATION_TYPE_ID
        AssociationKind.ONE_TO_MANY -> ONE_TO_MANY_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_MANY -> MANY_TO_MANY_ANNOTATION_TYPE_ID
        AssociationKind.MANY_TO_MANY_VIEW -> MANY_TO_MANY_VIEW_ANNOTATION_TYPE_ID
        AssociationKind.NONE,
        AssociationKind.IMPLICIT,
        -> error("Immutable association kind '$this' has no runtime annotation")
    }
}

private fun LsiType.toErasedMetadataType(): LsiType {
    return when (this) {
        is LsiDeclaredType -> copy(
            arguments = emptyList(),
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiTypeParameterRef -> OBJECT_TYPE
        is LsiPrimitiveType -> copy(
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiArrayType -> copy(
            elementType = elementType.toErasedMetadataType(),
            nullability = LsiNullability.NON_NULL,
            annotations = emptyList(),
        )
        is LsiFunctionType -> error(
            "Cannot compile function type as immutable draft runtime metadata",
        )
        is LsiUnresolvedType -> error(
            "Cannot compile unresolved immutable draft metadata element type '$displayName'"
        )
    }
}

private fun LsiType.isErasedMetadataType(): Boolean {
    return when (this) {
        is LsiDeclaredType -> arguments.isEmpty() &&
            nullability == LsiNullability.NON_NULL &&
            annotations.isEmpty()
        is LsiTypeParameterRef -> false
        is LsiPrimitiveType -> nullability == LsiNullability.NON_NULL && annotations.isEmpty()
        is LsiArrayType -> nullability == LsiNullability.NON_NULL &&
            annotations.isEmpty() &&
            elementType.isErasedMetadataType()
        is LsiFunctionType -> false
        is LsiUnresolvedType -> false
    }
}

private val OBJECT_TYPE = LsiDeclaredType(LsiSymbolId.type("java.lang.Object"))

private val KEY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Key")

private val KEYS_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.Keys")

private val ONE_TO_ONE_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToOne")

private val MANY_TO_ONE_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToOne")

private val ONE_TO_MANY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.OneToMany")

private val MANY_TO_MANY_ANNOTATION_TYPE_ID = LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToMany")

private val MANY_TO_MANY_VIEW_ANNOTATION_TYPE_ID =
    LsiSymbolId.type("org.babyfish.jimmer.sql.ManyToManyView")
