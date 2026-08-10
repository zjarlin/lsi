package site.addzero.lsi.clazz

import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

/** 使用显式包名和简单名创建顶层生成类型声明。 */
fun generatedTopLevelClass(
    packageName: String,
    simpleName: String,
): LsiClass {
    val qualifiedName = if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
    return LsiClass(
        typeId = LsiSymbolId.type(qualifiedName),
        packageName = packageName,
        simpleNames = listOf(simpleName),
        origin = LsiOrigin(LsiOriginKind.GENERATED),
    )
}

/** 以冻结源码类型的包和 enclosing 链为基准，派生同级生成类型声明。 */
fun LsiWorkspace.generatedSiblingClass(
    sourceTypeId: LsiSymbolId,
    generatedTypeId: LsiSymbolId,
    simpleNameSuffix: String,
    nestedSimpleNames: List<String> = emptyList(),
): LsiClass {
    require(simpleNameSuffix.isNotEmpty() && '.' !in simpleNameSuffix) {
        "Generated sibling type suffix must be a non-empty source-name fragment: '$simpleNameSuffix'"
    }
    val sourceType = toLsiClasses(listOf(sourceTypeId)).single()
    return LsiClass(
        typeId = generatedTypeId,
        packageName = sourceType.packageName,
        simpleNames = sourceType.simpleNames.dropLast(1) +
            "${sourceType.simpleNames.last()}$simpleNameSuffix" +
            nestedSimpleNames,
        origin = LsiOrigin(LsiOriginKind.GENERATED),
    )
}

/** 从冻结声明和显式补充声明解析精确源码名称，不猜测包边界。 */
fun LsiWorkspace.toLsiClasses(
    typeIds: Collection<LsiSymbolId>,
    additional: Collection<LsiClass> = emptyList(),
): List<LsiClass> {
    val duplicateAdditionalIds = additional
        .groupingBy(LsiClass::id)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .sorted()
    require(duplicateAdditionalIds.isEmpty()) {
        "Duplicate additional LSI type ids: ${duplicateAdditionalIds.joinToString { id -> id.value }}"
    }
    val additionalById = additional.associateBy(LsiClass::id)
    additionalById.forEach { (typeId, explicitType) ->
        if (this[typeId] != null) {
            val workspaceType = requireExactClass(typeId)
            require(explicitType.hasSameSourceName(workspaceType)) {
                "Additional LSI class name conflicts with workspace declaration: ${typeId.value}"
            }
        }
    }
    val requestedTypeIds = sortedSetOf<LsiSymbolId>().apply {
        typeIds.forEach { typeId ->
            require(typeId.isTypeId()) {
                "LSI class resolution requires type ids: ${typeId.value}"
            }
            add(typeId)
        }
    }
    return requestedTypeIds.map { typeId ->
        additionalById[typeId] ?: requireExactClass(typeId)
    }
}

private fun LsiWorkspace.requireExactClass(typeId: LsiSymbolId): LsiClass {
    val declarationChain = mutableListOf<LsiClass>()
    val visitedTypeIds = mutableSetOf<LsiSymbolId>()
    var currentTypeId: LsiSymbolId? = typeId
    var nestedDeclaration: LsiClass? = null
    while (currentTypeId != null) {
        require(visitedTypeIds.add(currentTypeId)) {
            "Cyclic LSI enclosing type chain: ${currentTypeId.value}"
        }
        val declaration = classDeclaration(currentTypeId)
        require(declaration != null) {
            "Missing LSI type declaration for source type name: ${currentTypeId.value}"
        }
        require(declaration.qualifiedName == currentTypeId.requireTypeQualifiedName()) {
            "LSI type declaration qualified name does not match its id: ${currentTypeId.value}"
        }
        nestedDeclaration?.let { child ->
            require(child.qualifiedName == "${declaration.qualifiedName}.${child.name}") {
                "LSI nested type qualified name does not match its enclosing declaration: ${child.id.value}"
            }
        }
        declarationChain += declaration
        nestedDeclaration = declaration
        currentTypeId = declaration.enclosingTypeId
    }

    val simpleNames = declarationChain.asReversed().map(LsiClass::name)
    val declaration = declarationChain.first()
    val simpleNameSuffix = simpleNames.joinToString(".")
    val packageName = when {
        declaration.qualifiedName == simpleNameSuffix -> ""
        declaration.qualifiedName.endsWith(".$simpleNameSuffix") ->
            declaration.qualifiedName.dropLast(simpleNameSuffix.length + 1)
        else -> throw IllegalArgumentException(
            "LSI type declaration name chain does not match qualified name: ${declaration.qualifiedName}"
        )
    }
    return declaration.copy(packageName = packageName, simpleNames = simpleNames)
}

private fun LsiClass.hasSameSourceName(other: LsiClass): Boolean {
    return id == other.id &&
        packageName == other.packageName &&
        simpleNames == other.simpleNames &&
        canonicalName == other.canonicalName
}
