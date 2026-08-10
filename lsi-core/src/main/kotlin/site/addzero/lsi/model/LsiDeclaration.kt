package site.addzero.lsi.model

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

enum class LsiVisibility {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PACKAGE_PRIVATE,
    PRIVATE,
    LOCAL,
    UNKNOWN
}

enum class LsiModality {
    FINAL,
    OPEN,
    ABSTRACT,
    SEALED,
    UNKNOWN
}

enum class LsiTypeDeclarationKind {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    OBJECT,
    RECORD,
    TYPE_ALIAS
}

enum class LsiModifier {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PRIVATE,
    ABSTRACT,
    OPEN,
    FINAL,
    SEALED,
    STATIC,
    CONST,
    OVERRIDE,
    DEFAULT,
    SYNCHRONIZED,
    NATIVE,
    TRANSIENT,
    VOLATILE,
    INLINE,
    NOINLINE,
    CROSSINLINE,
    TAILREC,
    SUSPEND,
    OPERATOR,
    INFIX,
    EXTERNAL,
    LATEINIT,
    DATA,
    VALUE,
    INNER,
    COMPANION,
    VARARG,
}

enum class LsiNameStyle {
    IDENTIFIER,
    KOTLIN_ESCAPED,
}

enum class LsiBodyStyle {
    BLOCK,
    EXPRESSION,
}

enum class LsiDelegationTarget {
    THIS,
    SUPER,
}

data class LsiDelegationCall(
    val target: LsiDelegationTarget,
    val arguments: List<LsiCodeBlock>,
)

sealed interface LsiMember {
    val annotations: List<LsiAnnotation>

    val modifiers: Set<LsiModifier>
        get() = emptySet()

    val documentation: String?
}

/**
 * 指向被覆盖声明，距离一表示直接覆盖。
 */
data class LsiOverride(
    val declarationId: LsiSymbolId,
    val distance: Int = 1
) {

    init {
        require(distance >= 1) { "LSI override distance must be positive: $distance" }
    }
}

sealed interface LsiDeclaration {
    val id: LsiSymbolId
    val name: String
    val visibility: LsiVisibility
    val documentation: String?
    val sourceDocumentation: String?
    val annotations: List<LsiAnnotation>
    val modifiers: Set<LsiModifier>
        get() = emptySet()
    val location: LsiLocation?
    val origin: LsiOrigin
}

data class LsiTypeDeclaration(
    override val id: LsiSymbolId,
    override val name: String,
    val qualifiedName: String,
    val kind: LsiTypeDeclarationKind,
    val enclosingTypeId: LsiSymbolId? = null,
    val requiresEnclosingInstance: Boolean = false,
    val abstractDeclaration: Boolean = false,
    val dataClass: Boolean = false,
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    val modality: LsiModality = LsiModality.FINAL,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val superTypes: List<LsiTypeRef> = emptyList(),
    val memberIds: List<LsiSymbolId> = emptyList(),
    val enumEntries: List<LsiEnumEntry> = emptyList(),
    val annotationMembers: List<LsiAnnotationMember> = emptyList(),
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    val superClass: LsiTypeRef? = null,
    val superClassConstructorArguments: List<LsiCodeBlock> = emptyList(),
    val superInterfaces: List<LsiTypeRef> = emptyList(),
    val primaryConstructor: LsiConstructor? = null,
    val members: List<LsiMember> = emptyList(),
) : LsiDeclaration, LsiMember {

    constructor(
        name: String,
        kind: LsiTypeDeclarationKind,
        nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        documentation: String? = null,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        superClass: LsiTypeRef? = null,
        superClassConstructorArguments: List<LsiCodeBlock> = emptyList(),
        superInterfaces: List<LsiTypeRef> = emptyList(),
        primaryConstructor: LsiConstructor? = null,
        enumConstants: List<LsiEnumEntry> = emptyList(),
        members: List<LsiMember> = emptyList(),
    ) : this(
        id = generatedTypeId(name),
        name = name,
        qualifiedName = generatedTypeQualifiedName(name),
        kind = kind,
        abstractDeclaration = LsiModifier.ABSTRACT in modifiers,
        dataClass = LsiModifier.DATA in modifiers,
        visibility = modifiers.toVisibility(),
        modality = modifiers.toModality(),
        typeParameters = typeParameters,
        superTypes = listOfNotNull(superClass) + superInterfaces,
        enumEntries = enumConstants,
        documentation = documentation,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        nameStyle = nameStyle,
        superClass = superClass,
        superClassConstructorArguments = superClassConstructorArguments,
        superInterfaces = superInterfaces,
        primaryConstructor = primaryConstructor,
        members = members,
    )

    init {
        require(name.isNotBlank()) { "LSI type declaration name cannot be blank" }
        require(qualifiedName.isNotBlank()) { "LSI type declaration qualified name cannot be blank" }
        require(enclosingTypeId != id) { "LSI type declaration cannot enclose itself: ${id.value}" }
        require(!requiresEnclosingInstance || enclosingTypeId != null) {
            "LSI type requiring an enclosing instance must be nested: ${id.value}"
        }
        require(!dataClass || kind == LsiTypeDeclarationKind.CLASS) {
            "Only LSI class declarations can be data classes: ${id.value}"
        }
        require(superTypes.filterIsInstance<LsiDeclaredType>().none { superType -> superType.declarationId == id }) {
            "LSI type declaration cannot directly inherit itself: ${id.value}"
        }
        require(memberIds.distinct().size == memberIds.size) {
            "LSI type declaration cannot contain duplicate member ids: ${id.value}"
        }
        require(enumEntries.map(LsiEnumEntry::id).distinct().size == enumEntries.size) {
            "LSI type declaration cannot contain duplicate enum entry ids: ${id.value}"
        }
        require(annotationMembers.isEmpty() || kind == LsiTypeDeclarationKind.ANNOTATION) {
            "Only LSI annotation declarations can contain annotation members: ${id.value}"
        }
        require(annotationMembers == annotationMembers.sortedBy(LsiAnnotationMember::name)) {
            "LSI annotation members must use stable name order: ${id.value}"
        }
        require(annotationMembers.map(LsiAnnotationMember::name).distinct().size == annotationMembers.size) {
            "LSI annotation declaration cannot contain duplicate member names: ${id.value}"
        }
        if (origin.kind == LsiOriginKind.SYNTHETIC) {
            requireSourceDeclarationName(name, nameStyle, "type")
        }
        require(kind != LsiTypeDeclarationKind.TYPE_ALIAS || origin.kind != LsiOriginKind.SYNTHETIC) {
            "Generated LSI type alias declarations are not supported: $name"
        }
        require(kind == LsiTypeDeclarationKind.ENUM || enumEntries.isEmpty()) {
            "Only LSI enum type can declare enum constants: $name"
        }
        require(kind != LsiTypeDeclarationKind.INTERFACE || superClass == null) {
            "LSI interface cannot declare a superclass: $name"
        }
        require(superClass != null || superClassConstructorArguments.isEmpty()) {
            "LSI superclass constructor arguments require a superclass: $name"
        }
        require(kind != LsiTypeDeclarationKind.INTERFACE || primaryConstructor == null) {
            "LSI interface cannot declare a primary constructor: $name"
        }
        require(LsiModifier.COMPANION !in modifiers || kind == LsiTypeDeclarationKind.OBJECT) {
            "Only LSI object can be a companion: $name"
        }
    }
}

data class LsiAnnotationMember(
    val name: String,
    val type: LsiTypeRef,
    val vararg: Boolean = false,
    val hasDefault: Boolean = false,
    val declarationIndex: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "LSI annotation member name cannot be blank" }
        require(declarationIndex == null || declarationIndex >= 0) {
            "LSI annotation member declaration index cannot be negative: $name"
        }
        require(type == type.toAnnotationMemberType()) {
            "LSI annotation member type must use canonical non-null semantics: $name"
        }
        require(!vararg || type is LsiArrayType) {
            "LSI annotation vararg member must expose its array type: $name"
        }
    }
}

data class LsiEnumEntry(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val constructorArguments: List<LsiCodeBlock> = emptyList(),
    val anonymousType: LsiTypeDeclaration? = null,
) : LsiDeclaration {

    constructor(
        name: String,
        constructorArguments: List<LsiCodeBlock> = emptyList(),
        anonymousType: LsiTypeDeclaration? = null,
    ) : this(
        id = LsiSymbolId.enumEntry(GENERATED_DECLARATION_OWNER_ID, name),
        name = name,
        ownerId = GENERATED_DECLARATION_OWNER_ID,
        origin = GENERATED_DECLARATION_ORIGIN,
        constructorArguments = constructorArguments,
        anonymousType = anonymousType,
    )

    override val visibility: LsiVisibility = LsiVisibility.PUBLIC

    init {
        require(name.isNotBlank()) { "LSI enum entry name cannot be blank" }
        require(name.isJvmIdentifier()) { "LSI enum entry name must be a JVM identifier: '$name'" }
        require(anonymousType == null || anonymousType.kind == LsiTypeDeclarationKind.CLASS) {
            "LSI enum constant anonymous type must be a class: $name"
        }
    }
}

data class LsiField(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    val type: LsiTypeRef,
    val mutable: Boolean = false,
    val static: Boolean = false,
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val initializer: LsiCodeBlock? = null,
    val typeReferenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
) : LsiDeclaration, LsiMember {

    constructor(
        name: String,
        type: LsiTypeRef,
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        documentation: String? = null,
        initializer: LsiCodeBlock? = null,
        typeReferenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
    ) : this(
        id = LsiSymbolId.field(GENERATED_DECLARATION_OWNER_ID, name),
        name = name,
        ownerId = GENERATED_DECLARATION_OWNER_ID,
        type = type,
        mutable = LsiModifier.FINAL !in modifiers,
        static = LsiModifier.STATIC in modifiers,
        visibility = modifiers.toVisibility(),
        documentation = documentation,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        initializer = initializer,
        typeReferenceStyle = typeReferenceStyle,
    )

    init {
        require(name.isNotBlank()) { "LSI field name cannot be blank" }
    }
}

data class LsiProperty(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    val type: LsiTypeRef,
    val getterName: String = name,
    val mutable: Boolean = false,
    val static: Boolean = false,
    val modality: LsiModality = LsiModality.UNKNOWN,
    val overrides: List<LsiOverride> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    val initializer: LsiCodeBlock? = null,
    val receiverType: LsiTypeRef? = null,
    val getter: LsiAccessor? = null,
    val setter: LsiAccessor? = null,
) : LsiDeclaration, LsiMember {

    constructor(
        name: String,
        type: LsiTypeRef,
        mutable: Boolean,
        nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        documentation: String? = null,
        initializer: LsiCodeBlock? = null,
        receiverType: LsiTypeRef? = null,
        getter: LsiAccessor? = null,
        setter: LsiAccessor? = null,
    ) : this(
        id = LsiSymbolId.property(GENERATED_DECLARATION_OWNER_ID, name),
        name = name,
        ownerId = GENERATED_DECLARATION_OWNER_ID,
        type = type,
        mutable = mutable,
        static = LsiModifier.STATIC in modifiers,
        modality = modifiers.toModality(),
        visibility = modifiers.toVisibility(),
        documentation = documentation,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        nameStyle = nameStyle,
        initializer = initializer,
        receiverType = receiverType,
        getter = getter,
        setter = setter,
    )

    init {
        require(name.isNotBlank()) { "LSI property name cannot be blank" }
        require(getterName.isNotBlank()) { "LSI property getter name cannot be blank" }
        require(overrides.map(LsiOverride::declarationId).distinct().size == overrides.size) {
            "LSI property cannot override the same declaration more than once: ${id.value}"
        }
        if (origin.kind == LsiOriginKind.SYNTHETIC) {
            requireSourceDeclarationName(name, nameStyle, "property")
        }
        require(mutable || setter == null) {
            "Immutable LSI property cannot declare a setter: $name"
        }
    }
}

data class LsiFunction(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId?,
    val returnType: LsiTypeRef,
    val parameters: List<LsiParameter> = emptyList(),
    val receiverType: LsiTypeRef? = null,
    val suspending: Boolean = false,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    val static: Boolean = false,
    val modality: LsiModality = LsiModality.UNKNOWN,
    val overrides: List<LsiOverride> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    val reifiedTypeParameterIds: Set<LsiSymbolId> = emptySet(),
    val body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    val bodyStyle: LsiBodyStyle = LsiBodyStyle.BLOCK,
    val renderReturnType: Boolean = true,
) : LsiDeclaration, LsiMember {

    constructor(
        name: String,
        nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        documentation: String? = null,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        reifiedTypeParameterIds: Set<LsiSymbolId> = emptySet(),
        receiverType: LsiTypeRef? = null,
        parameters: List<LsiParameter> = emptyList(),
        returnType: LsiTypeRef? = null,
        thrownTypes: List<LsiTypeRef> = emptyList(),
        body: LsiCodeBlock = LsiCodeBlock.EMPTY,
        bodyStyle: LsiBodyStyle = LsiBodyStyle.BLOCK,
    ) : this(
        id = LsiSymbolId.function(GENERATED_DECLARATION_OWNER_ID, name),
        name = name,
        ownerId = GENERATED_DECLARATION_OWNER_ID,
        returnType = returnType ?: LsiPrimitiveType(LsiPrimitiveKind.UNIT),
        parameters = parameters,
        receiverType = receiverType,
        suspending = LsiModifier.SUSPEND in modifiers,
        typeParameters = typeParameters,
        thrownTypes = thrownTypes,
        static = LsiModifier.STATIC in modifiers,
        modality = modifiers.toModality(),
        visibility = modifiers.toVisibility(),
        documentation = documentation,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        nameStyle = nameStyle,
        reifiedTypeParameterIds = reifiedTypeParameterIds,
        body = body,
        bodyStyle = bodyStyle,
        renderReturnType = returnType != null,
    )

    init {
        require(name.isNotBlank()) { "LSI function name cannot be blank" }
        if (origin.kind != LsiOriginKind.SYNTHETIC) {
            require(parameters.map(LsiParameter::index) == parameters.indices.toList()) {
                "LSI function parameters must use contiguous zero-based indexes: ${id.value}"
            }
            require(parameters.map(LsiParameter::id).distinct().size == parameters.size) {
                "LSI function cannot contain duplicate parameter ids: ${id.value}"
            }
        }
        require(overrides.map(LsiOverride::declarationId).distinct().size == overrides.size) {
            "LSI function cannot override the same declaration more than once: ${id.value}"
        }
        if (origin.kind == LsiOriginKind.SYNTHETIC) {
            requireSourceDeclarationName(name, nameStyle, "function")
        }
        require(typeParameters.map(LsiTypeParameter::id).containsAll(reifiedTypeParameterIds)) {
            "LSI reified type parameters must be declared by the function: $name"
        }
        require(reifiedTypeParameterIds.isEmpty() || LsiModifier.INLINE in modifiers) {
            "LSI reified type parameters require an inline function: $name"
        }
        require(parameters.map(LsiParameter::name).distinct().size == parameters.size) {
            "LSI function parameters cannot have duplicate names: $name"
        }
        require(parameters.dropLast(1).none { parameter -> LsiModifier.VARARG in parameter.modifiers }) {
            "LSI vararg function parameter must be last: $name"
        }
        require(bodyStyle != LsiBodyStyle.EXPRESSION || !body.isEmpty) {
            "LSI expression function body cannot be empty: $name"
        }
    }
}

data class LsiConstructor(
    override val id: LsiSymbolId,
    val ownerId: LsiSymbolId,
    val primary: Boolean = false,
    val parameters: List<LsiParameter> = emptyList(),
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    val delegationCall: LsiDelegationCall? = null,
) : LsiDeclaration, LsiMember {

    constructor(
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        documentation: String? = null,
        typeParameters: List<LsiTypeParameter> = emptyList(),
        parameters: List<LsiParameter> = emptyList(),
        thrownTypes: List<LsiTypeRef> = emptyList(),
        body: LsiCodeBlock = LsiCodeBlock.EMPTY,
        delegationCall: LsiDelegationCall? = null,
    ) : this(
        id = LsiSymbolId.constructor(GENERATED_DECLARATION_OWNER_ID),
        ownerId = GENERATED_DECLARATION_OWNER_ID,
        parameters = parameters,
        typeParameters = typeParameters,
        thrownTypes = thrownTypes,
        visibility = modifiers.toVisibility(),
        documentation = documentation,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        body = body,
        delegationCall = delegationCall,
    )

    override val name: String = "constructor"

    init {
        if (origin.kind != LsiOriginKind.SYNTHETIC) {
            require(parameters.map(LsiParameter::index) == parameters.indices.toList()) {
                "LSI constructor parameters must use contiguous zero-based indexes: ${id.value}"
            }
            require(parameters.map(LsiParameter::id).distinct().size == parameters.size) {
                "LSI constructor cannot contain duplicate parameter ids: ${id.value}"
            }
            require(parameters.all { parameter -> parameter.callableId == id }) {
                "LSI constructor parameters must reference their constructor: ${id.value}"
            }
        }
        require(typeParameters.map(LsiTypeParameter::id).distinct().size == typeParameters.size) {
            "LSI constructor type parameters cannot have duplicate ids"
        }
        require(parameters.map(LsiParameter::name).distinct().size == parameters.size) {
            "LSI constructor parameters cannot have duplicate names"
        }
        require(parameters.dropLast(1).none { parameter -> LsiModifier.VARARG in parameter.modifiers }) {
            "LSI vararg constructor parameter must be last"
        }
    }
}

data class LsiParameter(
    override val id: LsiSymbolId,
    override val name: String,
    val callableId: LsiSymbolId,
    val index: Int,
    val type: LsiTypeRef,
    val vararg: Boolean = false,
    val hasDefault: Boolean = false,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
    override val modifiers: Set<LsiModifier> = emptySet(),
    val nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    val defaultValue: LsiCodeBlock? = null,
) : LsiDeclaration {

    constructor(
        name: String,
        type: LsiTypeRef,
        nameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
        annotations: List<LsiAnnotation> = emptyList(),
        modifiers: Set<LsiModifier> = emptySet(),
        defaultValue: LsiCodeBlock? = null,
    ) : this(
        id = LsiSymbolId.parameter(GENERATED_DECLARATION_CALLABLE_ID, 0, name),
        name = name,
        callableId = GENERATED_DECLARATION_CALLABLE_ID,
        index = 0,
        type = type,
        vararg = LsiModifier.VARARG in modifiers,
        hasDefault = defaultValue != null,
        annotations = annotations,
        origin = GENERATED_DECLARATION_ORIGIN,
        modifiers = modifiers,
        nameStyle = nameStyle,
        defaultValue = defaultValue,
    )

    override val visibility: LsiVisibility = LsiVisibility.LOCAL

    init {
        require(name.isNotBlank()) { "LSI parameter name cannot be blank" }
        require(index >= 0) { "LSI parameter index cannot be negative: $index" }
        if (origin.kind == LsiOriginKind.SYNTHETIC) {
            requireSourceDeclarationName(name, nameStyle, "parameter")
        }
        require(LsiModifier.VARARG !in modifiers || defaultValue == null) {
            "LSI vararg parameter cannot declare a default value: $name"
        }
    }
}

data class LsiAccessor(
    val annotations: List<LsiAnnotation> = emptyList(),
    val modifiers: Set<LsiModifier> = emptySet(),
    val setterParameterName: String = "value",
    val setterParameterNameStyle: LsiNameStyle = LsiNameStyle.IDENTIFIER,
    val parameterAnnotations: List<LsiAnnotation> = emptyList(),
    val body: LsiCodeBlock = LsiCodeBlock.EMPTY,
    val bodyStyle: LsiBodyStyle = LsiBodyStyle.BLOCK,
) {
    init {
        requireSourceDeclarationName(setterParameterName, setterParameterNameStyle, "setter parameter")
        require(bodyStyle != LsiBodyStyle.EXPRESSION || !body.isEmpty) {
            "LSI expression accessor body cannot be empty"
        }
    }
}

data class LsiInitializerBlock(
    val static: Boolean,
    val body: LsiCodeBlock,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val documentation: String? = null,
) : LsiMember {
    override val modifiers: Set<LsiModifier> = if (static) {
        setOf(LsiModifier.STATIC)
    } else {
        emptySet()
    }
}

private val GENERATED_DECLARATION_OWNER_ID = LsiSymbolId.type("site.addzero.lsi.generated.Owner")

private val GENERATED_DECLARATION_CALLABLE_ID = LsiSymbolId.function(
    GENERATED_DECLARATION_OWNER_ID,
    "callable",
)

private val GENERATED_DECLARATION_ORIGIN = LsiOrigin(LsiOriginKind.SYNTHETIC)

private fun generatedTypeId(name: String): LsiSymbolId = LsiSymbolId.type(generatedTypeQualifiedName(name))

private fun generatedTypeQualifiedName(name: String): String = "site.addzero.lsi.generated.$name"

private fun Set<LsiModifier>.toVisibility(): LsiVisibility {
    return when {
        LsiModifier.PRIVATE in this -> LsiVisibility.PRIVATE
        LsiModifier.PROTECTED in this -> LsiVisibility.PROTECTED
        LsiModifier.INTERNAL in this -> LsiVisibility.INTERNAL
        LsiModifier.PUBLIC in this -> LsiVisibility.PUBLIC
        else -> LsiVisibility.PUBLIC
    }
}

private fun Set<LsiModifier>.toModality(): LsiModality {
    return when {
        LsiModifier.SEALED in this -> LsiModality.SEALED
        LsiModifier.ABSTRACT in this -> LsiModality.ABSTRACT
        LsiModifier.OPEN in this -> LsiModality.OPEN
        LsiModifier.FINAL in this -> LsiModality.FINAL
        else -> LsiModality.UNKNOWN
    }
}

private fun requireSourceDeclarationName(
    name: String,
    nameStyle: LsiNameStyle,
    declarationKind: String,
) {
    when (nameStyle) {
        LsiNameStyle.IDENTIFIER -> require(name.isJvmIdentifier()) {
            "LSI $declarationKind name must be a JVM identifier: '$name'"
        }
        LsiNameStyle.KOTLIN_ESCAPED -> require(name.isKotlinEscapedIdentifier()) {
            "LSI escaped Kotlin $declarationKind name is invalid: '$name'"
        }
    }
}

internal fun String.isJvmIdentifier(): Boolean {
    if (isEmpty() || !Character.isJavaIdentifierStart(first())) {
        return false
    }
    return drop(1).all(Character::isJavaIdentifierPart)
}

private fun String.isKotlinEscapedIdentifier(): Boolean {
    return isNotBlank() && none { character ->
        character == '`' || character == '\n' || character == '\r'
    }
}
