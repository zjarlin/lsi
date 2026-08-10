package site.addzero.lsi.clazz

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiClass
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationMember
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiTypeParameter

/**
 * 语言无关的类声明接口。
 *
 * APT、KSP、生成源码和其他前端都必须通过该接口暴露类结构，公共消费者不依赖
 * 具体冻结实现。
 */
interface LsiClass : LsiDeclaration, LsiMember {

    override val modifiers: Set<LsiModifier>

    val qualifiedName: String

    val kind: LsiTypeDeclarationKind

    val enclosingTypeId: LsiSymbolId?

    val requiresEnclosingInstance: Boolean

    val abstractDeclaration: Boolean

    val dataClass: Boolean

    val modality: LsiModality

    val typeParameters: List<LsiTypeParameter>

    val superTypes: List<LsiType>

    val memberIds: List<LsiSymbolId>

    val enumEntries: List<LsiEnumEntry>

    val annotationMembers: List<LsiAnnotationMember>

    val nameStyle: LsiNameStyle

    val superClass: LsiType?

    val superClassConstructorArguments: List<LsiCodeBlock>

    val superInterfaces: List<LsiType>

    val primaryConstructor: LsiConstructor?

    val members: List<LsiMember>
}

/** 创建完整的冻结类声明。 */
fun LsiClass(
    id: LsiSymbolId,
    name: String,
    qualifiedName: String,
    kind: LsiTypeDeclarationKind,
    enclosingTypeId: LsiSymbolId? = null,
    requiresEnclosingInstance: Boolean = false,
    abstractDeclaration: Boolean = false,
    dataClass: Boolean = false,
    visibility: LsiVisibility = LsiVisibility.PUBLIC,
    modality: LsiModality = LsiModality.FINAL,
    typeParameters: List<LsiTypeParameter> = emptyList(),
    superTypes: List<LsiType> = emptyList(),
    memberIds: List<LsiSymbolId> = emptyList(),
    enumEntries: List<LsiEnumEntry> = emptyList(),
    annotationMembers: List<LsiAnnotationMember> = emptyList(),
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    superClass: LsiType? = null,
    superClassConstructorArguments: List<LsiCodeBlock> = emptyList(),
    superInterfaces: List<LsiType> = emptyList(),
    primaryConstructor: LsiConstructor? = null,
    members: List<LsiMember> = emptyList(),
): LsiClass {
    return FrozenLsiClass(
        id = id,
        name = name,
        qualifiedName = qualifiedName,
        kind = kind,
        enclosingTypeId = enclosingTypeId,
        requiresEnclosingInstance = requiresEnclosingInstance,
        abstractDeclaration = abstractDeclaration,
        dataClass = dataClass,
        visibility = visibility,
        modality = modality,
        typeParameters = typeParameters,
        superTypes = superTypes,
        memberIds = memberIds,
        enumEntries = enumEntries,
        annotationMembers = annotationMembers,
        documentation = documentation,
        sourceDocumentation = sourceDocumentation,
        annotations = annotations,
        location = location,
        origin = origin,
        modifiers = modifiers,
        nameStyle = nameStyle,
        superClass = superClass,
        superClassConstructorArguments = superClassConstructorArguments,
        superInterfaces = superInterfaces,
        primaryConstructor = primaryConstructor,
        members = members,
    )
}

/** 创建用于源码生成的类声明。 */
fun LsiClass(
    name: String,
    kind: LsiTypeDeclarationKind,
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    documentation: String? = null,
    typeParameters: List<LsiTypeParameter> = emptyList(),
    superClass: LsiType? = null,
    superClassConstructorArguments: List<LsiCodeBlock> = emptyList(),
    superInterfaces: List<LsiType> = emptyList(),
    primaryConstructor: LsiConstructor? = null,
    enumConstants: List<LsiEnumEntry> = emptyList(),
    members: List<LsiMember> = emptyList(),
): LsiClass {
    return FrozenLsiClass(
        name = name,
        kind = kind,
        nameStyle = nameStyle,
        annotations = annotations,
        modifiers = modifiers,
        documentation = documentation,
        typeParameters = typeParameters,
        superClass = superClass,
        superClassConstructorArguments = superClassConstructorArguments,
        superInterfaces = superInterfaces,
        primaryConstructor = primaryConstructor,
        enumConstants = enumConstants,
        members = members,
    )
}

/** 复制类声明并替换指定结构。 */
fun LsiClass.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    qualifiedName: String = this.qualifiedName,
    kind: LsiTypeDeclarationKind = this.kind,
    enclosingTypeId: LsiSymbolId? = this.enclosingTypeId,
    requiresEnclosingInstance: Boolean = this.requiresEnclosingInstance,
    abstractDeclaration: Boolean = this.abstractDeclaration,
    dataClass: Boolean = this.dataClass,
    visibility: LsiVisibility = this.visibility,
    modality: LsiModality = this.modality,
    typeParameters: List<LsiTypeParameter> = this.typeParameters,
    superTypes: List<LsiType> = this.superTypes,
    memberIds: List<LsiSymbolId> = this.memberIds,
    enumEntries: List<LsiEnumEntry> = this.enumEntries,
    annotationMembers: List<LsiAnnotationMember> = this.annotationMembers,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    nameStyle: LsiNameStyle = this.nameStyle,
    superClass: LsiType? = this.superClass,
    superClassConstructorArguments: List<LsiCodeBlock> = this.superClassConstructorArguments,
    superInterfaces: List<LsiType> = this.superInterfaces,
    primaryConstructor: LsiConstructor? = this.primaryConstructor,
    members: List<LsiMember> = this.members,
): LsiClass {
    return LsiClass(
        id = id,
        name = name,
        qualifiedName = qualifiedName,
        kind = kind,
        enclosingTypeId = enclosingTypeId,
        requiresEnclosingInstance = requiresEnclosingInstance,
        abstractDeclaration = abstractDeclaration,
        dataClass = dataClass,
        visibility = visibility,
        modality = modality,
        typeParameters = typeParameters,
        superTypes = superTypes,
        memberIds = memberIds,
        enumEntries = enumEntries,
        annotationMembers = annotationMembers,
        documentation = documentation,
        sourceDocumentation = sourceDocumentation,
        annotations = annotations,
        location = location,
        origin = origin,
        modifiers = modifiers,
        nameStyle = nameStyle,
        superClass = superClass,
        superClassConstructorArguments = superClassConstructorArguments,
        superInterfaces = superInterfaces,
        primaryConstructor = primaryConstructor,
        members = members,
    )
}
