package site.addzero.lsi.field

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.FrozenLsiField
import site.addzero.lsi.model.FrozenLsiProperty
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiDeclaration
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModality
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiOverride
import site.addzero.lsi.model.LsiTypeReferenceStyle
import site.addzero.lsi.model.LsiVisibility
import site.addzero.lsi.type.LsiType

/** 语言无关的字段声明。 */
interface LsiField : LsiDeclaration, LsiMember {
    override val modifiers: Set<LsiModifier>
    val ownerId: LsiSymbolId
    val type: LsiType
    val mutable: Boolean
    val static: Boolean
    val initializer: LsiCodeBlock?
    val typeReferenceStyle: LsiTypeReferenceStyle
}

/** Java getter 与 Kotlin property 统一后的属性声明。 */
interface LsiProperty : LsiDeclaration, LsiMember {
    override val modifiers: Set<LsiModifier>
    val ownerId: LsiSymbolId
    val type: LsiType
    val getterName: String
    val mutable: Boolean
    val static: Boolean
    val modality: LsiModality
    val overrides: List<LsiOverride>
    val nameStyle: LsiNameStyle
    val initializer: LsiCodeBlock?
    val receiverType: LsiType?
    val getter: LsiAccessor?
    val setter: LsiAccessor?
}

fun LsiField(
    id: LsiSymbolId,
    name: String,
    ownerId: LsiSymbolId,
    type: LsiType,
    mutable: Boolean = false,
    static: Boolean = false,
    visibility: LsiVisibility = LsiVisibility.PUBLIC,
    documentation: String? = null,
    sourceDocumentation: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
    modifiers: Set<LsiModifier> = emptySet(),
    initializer: LsiCodeBlock? = null,
    typeReferenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
): LsiField = FrozenLsiField(
    id = id,
    name = name,
    ownerId = ownerId,
    type = type,
    mutable = mutable,
    static = static,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    initializer = initializer,
    typeReferenceStyle = typeReferenceStyle,
)

fun LsiField(
    name: String,
    type: LsiType,
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    documentation: String? = null,
    initializer: LsiCodeBlock? = null,
    typeReferenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
): LsiField = FrozenLsiField(
    name = name,
    type = type,
    annotations = annotations,
    modifiers = modifiers,
    documentation = documentation,
    initializer = initializer,
    typeReferenceStyle = typeReferenceStyle,
)

fun LsiField.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    ownerId: LsiSymbolId = this.ownerId,
    type: LsiType = this.type,
    mutable: Boolean = this.mutable,
    static: Boolean = this.static,
    visibility: LsiVisibility = this.visibility,
    documentation: String? = this.documentation,
    sourceDocumentation: String? = this.sourceDocumentation,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
    modifiers: Set<LsiModifier> = this.modifiers,
    initializer: LsiCodeBlock? = this.initializer,
    typeReferenceStyle: LsiTypeReferenceStyle = this.typeReferenceStyle,
): LsiField = LsiField(
    id = id,
    name = name,
    ownerId = ownerId,
    type = type,
    mutable = mutable,
    static = static,
    visibility = visibility,
    documentation = documentation,
    sourceDocumentation = sourceDocumentation,
    annotations = annotations,
    location = location,
    origin = origin,
    modifiers = modifiers,
    initializer = initializer,
    typeReferenceStyle = typeReferenceStyle,
)

fun LsiProperty(
    id: LsiSymbolId,
    name: String,
    ownerId: LsiSymbolId,
    type: LsiType,
    getterName: String = name,
    mutable: Boolean = false,
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
    initializer: LsiCodeBlock? = null,
    receiverType: LsiType? = null,
    getter: LsiAccessor? = null,
    setter: LsiAccessor? = null,
): LsiProperty = FrozenLsiProperty(
    id = id,
    name = name,
    ownerId = ownerId,
    type = type,
    getterName = getterName,
    mutable = mutable,
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
    initializer = initializer,
    receiverType = receiverType,
    getter = getter,
    setter = setter,
)

fun LsiProperty(
    name: String,
    type: LsiType,
    mutable: Boolean,
    nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    annotations: List<LsiAnnotation> = emptyList(),
    modifiers: Set<LsiModifier> = emptySet(),
    documentation: String? = null,
    initializer: LsiCodeBlock? = null,
    receiverType: LsiType? = null,
    getter: LsiAccessor? = null,
    setter: LsiAccessor? = null,
): LsiProperty = FrozenLsiProperty(
    name = name,
    type = type,
    mutable = mutable,
    nameStyle = nameStyle,
    annotations = annotations,
    modifiers = modifiers,
    documentation = documentation,
    initializer = initializer,
    receiverType = receiverType,
    getter = getter,
    setter = setter,
)

fun LsiProperty.copy(
    id: LsiSymbolId = this.id,
    name: String = this.name,
    ownerId: LsiSymbolId = this.ownerId,
    type: LsiType = this.type,
    getterName: String = this.getterName,
    mutable: Boolean = this.mutable,
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
    initializer: LsiCodeBlock? = this.initializer,
    receiverType: LsiType? = this.receiverType,
    getter: LsiAccessor? = this.getter,
    setter: LsiAccessor? = this.setter,
): LsiProperty = LsiProperty(
    id = id,
    name = name,
    ownerId = ownerId,
    type = type,
    getterName = getterName,
    mutable = mutable,
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
    initializer = initializer,
    receiverType = receiverType,
    getter = getter,
    setter = setter,
)
