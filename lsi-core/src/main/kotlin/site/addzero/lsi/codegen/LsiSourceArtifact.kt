package site.addzero.lsi.codegen

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.referencedTypeIds

/** 描述尚未绑定具体渲染实现的源码产物。 */
data class LsiSourceArtifact(
    val file: LsiFile,
    val typeNames: List<LsiTypeName>,
    val aggregationMode: ArtifactAggregationMode,
    val emissionMode: ArtifactEmissionMode = ArtifactEmissionMode.IMMEDIATE,
    val originatingSymbols: Set<LsiSymbolId> = emptySet(),
    val originatingSources: Set<LsiSource> = emptySet(),
    val dependencySymbols: Set<LsiSymbolId> = originatingSymbols,
    val dependencySources: Set<LsiSource> = originatingSources,
) {
    val kind: ArtifactKind = when (file.language) {
        LsiLanguage.JAVA -> ArtifactKind.JAVA_SOURCE
        LsiLanguage.KOTLIN -> ArtifactKind.KOTLIN_SOURCE
        LsiLanguage.UNKNOWN -> error("LSI source artifact requires Java or Kotlin source")
    }

    val qualifiedFileName: String = if (file.packageName.isEmpty()) {
        file.fileName
    } else {
        "${file.packageName}.${file.fileName}"
    }

    val path: String = buildString {
        if (file.packageName.isNotEmpty()) {
            append(file.packageName.replace('.', '/'))
            append('/')
        }
        append(file.fileName)
        append(
            when (kind) {
                ArtifactKind.JAVA_SOURCE -> ".java"
                ArtifactKind.KOTLIN_SOURCE -> ".kt"
                ArtifactKind.RESOURCE -> error("LSI source artifact cannot be a resource")
            }
        )
    }

    init {
        val duplicateTypeIds = typeNames
            .groupingBy(LsiTypeName::typeId)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateTypeIds.isEmpty()) {
            "Duplicate LSI source type ids: ${duplicateTypeIds.joinToString { id -> id.value }}"
        }
        val missingTypeIds = file.referencedTypeIds - typeNames.mapTo(hashSetOf(), LsiTypeName::typeId)
        require(missingTypeIds.isEmpty()) {
            "Missing LSI source type names for $qualifiedFileName: " +
                missingTypeIds.joinToString { id -> id.value }
        }
        if (aggregationMode == ArtifactAggregationMode.ISOLATING) {
            require(originatingSymbols.size == 1) {
                "Isolating LSI source artifact requires exactly one originating symbol: $qualifiedFileName"
            }
        }
        require(dependencySymbols.containsAll(originatingSymbols)) {
            "LSI source artifact dependencies must contain all originating symbols: $qualifiedFileName"
        }
        require(dependencySources.containsAll(originatingSources)) {
            "LSI source artifact dependencies must contain all originating sources: $qualifiedFileName"
        }
        require(
            emissionMode != ArtifactEmissionMode.STABLE ||
                aggregationMode == ArtifactAggregationMode.AGGREGATING
        ) {
            "Stable LSI source artifact must be aggregating: $qualifiedFileName"
        }
    }

    fun generatedArtifact(content: String): GeneratedArtifact {
        return GeneratedArtifact.create(
            kind = kind,
            path = path,
            content = content,
            aggregationMode = aggregationMode,
            emissionMode = emissionMode,
            originatingSymbols = originatingSymbols,
            originatingSources = originatingSources,
            dependencySymbols = dependencySymbols,
            dependencySources = dependencySources,
        )
    }
}
