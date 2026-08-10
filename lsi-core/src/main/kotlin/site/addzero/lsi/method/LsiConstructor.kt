package site.addzero.lsi.method

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiConstructor
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiDelegationCall
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.LsiTypeParameter

/** 语言无关的构造器声明。 */
interface LsiConstructor : LsiDeclaration, LsiMember {
    override val modifiers: Set<LsiModifier>
    val ownerId: LsiSymbolId
    val primary: Boolean
    val parameters: List<LsiParameter>
    val typeParameters: List<LsiTypeParameter>
    val thrownTypes: List<LsiType>
    val body: LsiCodeBlock
    val delegationCall: LsiDelegationCall?

    override val name: String
        get() = "constructor"
}

fun LsiConstructor(
    id: LsiSymbolId,
    ownerId: LsiSymbolId,
    primary: Boolean = false,
    parameters: List<LsiParameter> = emptyList(),
    typeParameters: List<LsiTypeParameter> = emptyList(),
    thrownTypes: List<LsiType> = emptyList(),
    visibility: LsiVisibility = LsiVisibility.PUBLIC,
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    delegationCall: LsiDelegationCall? = null,
): LsiConstructor = FrozenLsiConstructor(
    id = id,
    ownerId = ownerId,
    primary = primary,
    parameters = parameters,
    typeParameters = typeParameters,
    thrownTypes = thrownTypes,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    body = body,
    delegationCall = delegationCall,
)
fun LsiConstructor(
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    documentation: String? = null,
    typeParameters: List<LsiTypeParameter> = emptyList(),
    parameters: List<LsiParameter> = emptyList(),
    thrownTypes: List<LsiType> = emptyList(),
    body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    delegationCall: LsiDelegationCall? = null,
): LsiConstructor = FrozenLsiConstructor(
    annotations = annotations,
    modifiers = modifiers,
    documentation = documentation,
    typeParameters = typeParameters,
    parameters = parameters,
    thrownTypes = thrownTypes,
    body = body,
    delegationCall = delegationCall,
)

fun LsiConstructor.copy(
    id: LsiSymbolId = this.id,
    ownerId: LsiSymbolId = this.ownerId,
    primary: Boolean = this.primary,
    parameters: List<LsiParameter> = this.parameters,
    typeParameters: List<LsiTypeParameter> = this.typeParameters,
    thrownTypes: List<LsiType> = this.thrownTypes,
    visibility: LsiVisibility = this.visibility,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    body: LsiCodeBlock = this.body,
    delegationCall: LsiDelegationCall? = this.delegationCall,
): LsiConstructor = LsiConstructor(
    id = id,
    ownerId = ownerId,
    primary = primary,
    parameters = parameters,
    typeParameters = typeParameters,
    thrownTypes = thrownTypes,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    body = body,
    delegationCall = delegationCall,
)
