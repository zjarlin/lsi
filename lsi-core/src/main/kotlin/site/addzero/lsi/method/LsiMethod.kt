package site.addzero.lsi.method

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiMethod
import site.addzero.lsi.model.FrozenLsiParameter
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiTypeParameter

/** 语言无关的方法或函数声明。 */
interface LsiMethod : LsiDeclaration, LsiMember {
    override val modifiers: Set<LsiModifier>
    val ownerId: LsiSymbolId?
    val returnType: LsiType
    val parameters: List<LsiParameter>
    val receiverType: LsiType?
    val suspending: Boolean
    val typeParameters: List<LsiTypeParameter>
    val thrownTypes: List<LsiType>
    val static: Boolean
    val modality: LsiModality
    val overrides: List<LsiOverride>
    val nameStyle: LsiNameStyle
    val reifiedTypeParameterIds: Set<LsiSymbolId>
    val body: LsiCodeBlock
    val bodyStyle: LsiBodyStyle
    val renderReturnType: Boolean
}

/** 语言无关的方法或构造器参数声明。 */
interface LsiParameter : LsiDeclaration {
    override val modifiers: Set<LsiModifier>
    val callableId: LsiSymbolId
    val index: Int
    val type: LsiType
    val vararg: Boolean
    val hasDefault: Boolean
    val nameStyle: LsiNameStyle
    val defaultValue: LsiCodeBlock?
}

fun LsiMethod(
    id: LsiSymbolId,
    name: String,
    ownerId: LsiSymbolId?,
    returnType: LsiType,
    parameters: List<LsiParameter> = emptyList(),
    receiverType: LsiType? = null,
    suspending: Boolean = false,
    typeParameters: List<LsiTypeParameter> = emptyList(),
    thrownTypes: List<LsiType> = emptyList(),
    static: Boolean = false,
    modality: LsiModality = LsiModality.UNKNOWN,
    overrides: List<LsiOverride> = emptyList(),
    visibility: LsiVisibility = LsiVisibility.PUBLIC,
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    reifiedTypeParameterIds: Set<LsiSymbolId> = emptySet(),
    body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    bodyStyle: LsiBodyStyle = LsiBodyStyle.BLOCK,
    renderReturnType: Boolean = true,
): LsiMethod = FrozenLsiMethod(
    id = id,
    name = name,
    ownerId = ownerId,
    returnType = returnType,
    parameters = parameters,
    receiverType = receiverType,
    suspending = suspending,
    typeParameters = typeParameters,
    thrownTypes = thrownTypes,
    static = static,
    modality = modality,
    overrides = overrides,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    nameStyle = nameStyle,
    reifiedTypeParameterIds = reifiedTypeParameterIds,
    body = body,
    bodyStyle = bodyStyle,
    renderReturnType = renderReturnType,
)

fun LsiMethod(
    name: String,
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    documentation: String? = null,
    typeParameters: List<LsiTypeParameter> = emptyList(),
    reifiedTypeParameterIds: Set<LsiSymbolId> = emptySet(),
    receiverType: LsiType? = null,
    parameters: List<LsiParameter> = emptyList(),
    returnType: LsiType? = null,
    thrownTypes: List<LsiType> = emptyList(),
    body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    bodyStyle: LsiBodyStyle = LsiBodyStyle.BLOCK,
): LsiMethod = FrozenLsiMethod(
    name = name,
    nameStyle = nameStyle,
    annotations = annotations,
    modifiers = modifiers,
    documentation = documentation,
    typeParameters = typeParameters,
    reifiedTypeParameterIds = reifiedTypeParameterIds,
    receiverType = receiverType,
    parameters = parameters,
    returnType = returnType,
    thrownTypes = thrownTypes,
    body = body,
    bodyStyle = bodyStyle,
)

fun LsiMethod.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    ownerId: LsiSymbolId? = this.ownerId,
    returnType: LsiType = this.returnType,
    parameters: List<LsiParameter> = this.parameters,
    receiverType: LsiType? = this.receiverType,
    suspending: Boolean = this.suspending,
    typeParameters: List<LsiTypeParameter> = this.typeParameters,
    thrownTypes: List<LsiType> = this.thrownTypes,
    static: Boolean = this.static,
    modality: LsiModality = this.modality,
    overrides: List<LsiOverride> = this.overrides,
    visibility: LsiVisibility = this.visibility,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    nameStyle: LsiNameStyle = this.nameStyle,
    reifiedTypeParameterIds: Set<LsiSymbolId> = this.reifiedTypeParameterIds,
    body: LsiCodeBlock = this.body,
    bodyStyle: LsiBodyStyle = this.bodyStyle,
    renderReturnType: Boolean = this.renderReturnType,
): LsiMethod = LsiMethod(
    id = id,
    name = name,
    ownerId = ownerId,
    returnType = returnType,
    parameters = parameters,
    receiverType = receiverType,
    suspending = suspending,
    typeParameters = typeParameters,
    thrownTypes = thrownTypes,
    static = static,
    modality = modality,
    overrides = overrides,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    nameStyle = nameStyle,
    reifiedTypeParameterIds = reifiedTypeParameterIds,
    body = body,
    bodyStyle = bodyStyle,
    renderReturnType = renderReturnType,
)

fun LsiParameter(
    id: LsiSymbolId,
    name: String,
    callableId: LsiSymbolId,
    index: Int,
    type: LsiType,
    vararg: Boolean = false,
    hasDefault: Boolean = false,
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    defaultValue: LsiCodeBlock? = null,
): LsiParameter = FrozenLsiParameter(
    id = id,
    name = name,
    callableId = callableId,
    index = index,
    type = type,
    vararg = vararg,
    hasDefault = hasDefault,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    nameStyle = nameStyle,
    defaultValue = defaultValue,
)

fun LsiParameter(
    name: String,
    type: LsiType,
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    defaultValue: LsiCodeBlock? = null,
): LsiParameter = FrozenLsiParameter(
    name = name,
    type = type,
    nameStyle = nameStyle,
    annotations = annotations,
    modifiers = modifiers,
    defaultValue = defaultValue,
)

fun LsiParameter.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    callableId: LsiSymbolId = this.callableId,
    index: Int = this.index,
    type: LsiType = this.type,
    vararg: Boolean = this.vararg,
    hasDefault: Boolean = this.hasDefault,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    nameStyle: LsiNameStyle = this.nameStyle,
    defaultValue: LsiCodeBlock? = this.defaultValue,
): LsiParameter = LsiParameter(
    id = id,
    name = name,
    callableId = callableId,
    index = index,
    type = type,
    vararg = vararg,
    hasDefault = hasDefault,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    nameStyle = nameStyle,
    defaultValue = defaultValue,
)

val LsiMethod.hasNoRequiredParameters: Boolean
    get() = parameters.isEmpty() || parameters.all { parameter -> parameter.hasDefault }
