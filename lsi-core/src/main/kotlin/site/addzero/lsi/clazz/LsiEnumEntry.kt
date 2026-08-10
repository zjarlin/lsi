package site.addzero.lsi.clazz

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiEnumEntry
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiVisibility

/** 语言无关的枚举项声明。 */
interface LsiEnumEntry : LsiDeclaration {
    val ownerId: LsiSymbolId
    val constructorArguments: List<LsiCodeBlock>
    val anonymousType: LsiClass?

    override val visibility: LsiVisibility
        get() = LsiVisibility.PUBLIC
}

fun LsiEnumEntry(
    id: LsiSymbolId,
    name: String,
    ownerId: LsiSymbolId,
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    constructorArguments: List<LsiCodeBlock> = emptyList(),
    anonymousType: LsiClass? = null,
): LsiEnumEntry = FrozenLsiEnumEntry(
    id = id,
    name = name,
    ownerId = ownerId,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    constructorArguments = constructorArguments,
    anonymousType = anonymousType,
)
fun LsiEnumEntry(
    name: String,
    constructorArguments: List<LsiCodeBlock> = emptyList(),
    anonymousType: LsiClass? = null,
): LsiEnumEntry = FrozenLsiEnumEntry(
    name = name,
    constructorArguments = constructorArguments,
    anonymousType = anonymousType,
)

fun LsiEnumEntry.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    ownerId: LsiSymbolId = this.ownerId,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    constructorArguments: List<LsiCodeBlock> = this.constructorArguments,
    anonymousType: LsiClass? = this.anonymousType,
): LsiEnumEntry = LsiEnumEntry(
    id = id,
    name = name,
    ownerId = ownerId,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    constructorArguments = constructorArguments,
    anonymousType = anonymousType,
)
