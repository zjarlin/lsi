package site.addzero.lsi.compiler

import java.util.ServiceLoader
import kotlin.reflect.KClass
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed

data class CompilerFeatureMetadata(
    val aptAnnotationTypes: Set<String> = emptySet(),
    val supportedOptions: Set<String> = emptySet(),
    val classpathTypeIds: Set<LsiSymbolId> = emptySet(),
    val inputResourcePaths: Set<String> = emptySet(),
    val inputDocumentKinds: Set<CompilerInputDocumentKind> = emptySet(),
    /** 其他功能在当前轮实际写出源码时，将此功能的源码延后到下一真实轮。 */
    val requiresSourceQuiescence: Boolean = false,
) {

    init {
        aptAnnotationTypes.forEach(::requireAnnotationQualifiedName)
        supportedOptions.forEach(::requireCompilerOptionName)
        classpathTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        inputResourcePaths.forEach(::requireCompilerResourcePath)
    }

    companion object {
        val EMPTY = CompilerFeatureMetadata()
    }
}

/**
 * 以功能实现类型为稳定身份，并绑定收集态与预编译态的运行时类型。
 */
class CompilerFeatureKey<
    C : CompilerFeatureState,
    S : CompilerFeatureState,
> @PublishedApi internal constructor(
    val featureType: KClass<out CompilerFeature<C, S>>,
    @PublishedApi internal val collectionStateType: KClass<C>,
    @PublishedApi internal val stateType: KClass<S>,
    @PublishedApi internal val emptyCollectionState: C,
) : Comparable<CompilerFeatureKey<*, *>> {

    val id: String = requireNotNull(featureType.qualifiedName) {
        "Compiler feature type must have a qualified name: $featureType"
    }

    init {
        require(collectionStateType.java.isInstance(emptyCollectionState)) {
            "Compiler feature '$id' empty collection state must be ${collectionStateType.qualifiedName}"
        }
    }

    internal fun castCollectionState(state: CompilerFeatureState): C {
        return try {
            collectionStateType.java.cast(state)
        } catch (ex: ClassCastException) {
            throw CompilerFeatureStateTypeException(
                featureKey = this,
                expectedType = collectionStateType,
                actualType = state::class,
                phase = "collection",
                cause = ex,
            )
        }
    }

    internal fun castState(state: CompilerFeatureState): S {
        return try {
            stateType.java.cast(state)
        } catch (ex: ClassCastException) {
            throw CompilerFeatureStateTypeException(
                featureKey = this,
                expectedType = stateType,
                actualType = state::class,
                phase = "precompile",
                cause = ex,
            )
        }
    }

    override fun compareTo(other: CompilerFeatureKey<*, *>): Int = id.compareTo(other.id)

    override fun equals(other: Any?): Boolean {
        return this === other || other is CompilerFeatureKey<*, *> && featureType == other.featureType
    }

    override fun hashCode(): Int = featureType.hashCode()

    override fun toString(): String = id
}

inline fun <
    reified F : CompilerFeature<C, S>,
    reified C : CompilerFeatureState,
    reified S : CompilerFeatureState,
> compilerFeatureKey(
    emptyCollectionState: C,
): CompilerFeatureKey<C, S> {
    return CompilerFeatureKey(
        featureType = F::class,
        collectionStateType = C::class,
        stateType = S::class,
        emptyCollectionState = emptyCollectionState,
    )
}

/**
 * 由 ServiceLoader 发现并在每个真实编译轮执行的无平台功能。
 */
interface CompilerFeature<
    C : CompilerFeatureState,
    S : CompilerFeatureState,
> {
    val key: CompilerFeatureKey<C, S>

    val dependencies: Set<CompilerFeatureKey<*, *>>
        get() = emptySet()

    val metadata: CompilerFeatureMetadata
        get() = CompilerFeatureMetadata.EMPTY

    fun requestTypeSeeds(context: CompilerTypeSeedContext): Collection<LsiTypeSeed> = emptyList()

    fun collect(context: CompilerCollectContext): CompilerFeatureCollection<C> =
        CompilerFeatureCollection(key.emptyCollectionState)

    fun precompile(context: CompilerPrecompileContext<C, S>): CompilerFeaturePrecompileResult<S>

    fun render(context: CompilerRenderContext<C, S>): CompilerFeatureRenderResult =
        CompilerFeatureRenderResult()
}

/** 从指定类加载器发现功能，并立即执行严格图校验。 */
object CompilerFeatureLoader {

    fun load(
        classLoader: ClassLoader = CompilerFeature::class.java.classLoader,
    ): List<CompilerFeature<*, *>> {
        val features = ServiceLoader
            .load(CompilerFeature::class.java, classLoader)
            .toList()
        return CompilerFeatureGraph.sort(features)
    }
}

sealed class CompilerFeatureGraphException(message: String) : IllegalArgumentException(message)

class DuplicateCompilerFeatureException(
    val featureKey: CompilerFeatureKey<*, *>,
) : CompilerFeatureGraphException("Duplicate compiler feature: '${featureKey.id}'")

class MissingCompilerFeatureDependencyException(
    val featureKey: CompilerFeatureKey<*, *>,
    val dependencyKey: CompilerFeatureKey<*, *>,
) : CompilerFeatureGraphException(
    "Compiler feature '${featureKey.id}' depends on missing feature '${dependencyKey.id}'",
)

class CyclicCompilerFeatureDependencyException(
    val cycle: List<CompilerFeatureKey<*, *>>,
) : CompilerFeatureGraphException(
    "Compiler feature dependency cycle: ${cycle.joinToString(" -> ") { key -> key.id }}",
)

class CompilerFeatureStateTypeException(
    val featureKey: CompilerFeatureKey<*, *>,
    val expectedType: KClass<out CompilerFeatureState>,
    val actualType: KClass<out CompilerFeatureState>,
    val phase: String,
    cause: ClassCastException,
) : IllegalArgumentException(
    "Compiler feature '${featureKey.id}' $phase state must be ${expectedType.qualifiedName}, " +
        "but got ${actualType.qualifiedName}",
    cause,
)

/** 以功能类型全限定名为稳定排序键计算严格依赖顺序。 */
object CompilerFeatureGraph {

    fun sort(features: Iterable<CompilerFeature<*, *>>): List<CompilerFeature<*, *>> {
        val featuresByKey = linkedMapOf<CompilerFeatureKey<*, *>, CompilerFeature<*, *>>()
        for (feature in features) {
            val key = feature.key
            require(key.featureType.java.isInstance(feature)) {
                "Compiler feature '${feature::class.qualifiedName}' does not match key '${key.id}'"
            }
            if (featuresByKey.putIfAbsent(key, feature) != null) {
                throw DuplicateCompilerFeatureException(key)
            }
        }
        validateDependencies(featuresByKey)

        val remainingDependencies = featuresByKey.mapValues { (_, feature) ->
            feature.dependencies.size
        }.toMutableMap()
        val dependents = mutableMapOf<CompilerFeatureKey<*, *>, MutableList<CompilerFeatureKey<*, *>>>()
        for ((key, feature) in featuresByKey) {
            for (dependencyKey in feature.dependencies) {
                dependents.getOrPut(dependencyKey, ::mutableListOf) += key
            }
        }
        val ready = sortedSetOf<CompilerFeatureKey<*, *>>()
        remainingDependencies
            .filterValues { dependencyCount -> dependencyCount == 0 }
            .keys
            .let(ready::addAll)
        val sorted = mutableListOf<CompilerFeature<*, *>>()
        while (ready.isNotEmpty()) {
            val key = ready.first()
            ready.remove(key)
            sorted += requireNotNull(featuresByKey[key])
            for (dependentKey in dependents[key].orEmpty().sorted()) {
                val dependencyCount = requireNotNull(remainingDependencies[dependentKey]) - 1
                remainingDependencies[dependentKey] = dependencyCount
                if (dependencyCount == 0) {
                    ready += dependentKey
                }
            }
        }
        if (sorted.size != featuresByKey.size) {
            throw CyclicCompilerFeatureDependencyException(findCycle(featuresByKey))
        }
        return sorted
    }

    private fun validateDependencies(
        featuresByKey: Map<CompilerFeatureKey<*, *>, CompilerFeature<*, *>>,
    ) {
        for ((key, feature) in featuresByKey.toSortedMap()) {
            for (dependencyKey in feature.dependencies.sorted()) {
                if (dependencyKey !in featuresByKey) {
                    throw MissingCompilerFeatureDependencyException(key, dependencyKey)
                }
            }
        }
    }

    private fun findCycle(
        featuresByKey: Map<CompilerFeatureKey<*, *>, CompilerFeature<*, *>>,
    ): List<CompilerFeatureKey<*, *>> {
        val states = mutableMapOf<CompilerFeatureKey<*, *>, VisitState>()
        val stack = mutableListOf<CompilerFeatureKey<*, *>>()
        for (key in featuresByKey.keys.sorted()) {
            val cycle = findCycleFrom(key, featuresByKey, states, stack)
            if (cycle != null) {
                return cycle
            }
        }
        error("Cyclic compiler feature graph did not expose a cycle")
    }

    private fun findCycleFrom(
        key: CompilerFeatureKey<*, *>,
        featuresByKey: Map<CompilerFeatureKey<*, *>, CompilerFeature<*, *>>,
        states: MutableMap<CompilerFeatureKey<*, *>, VisitState>,
        stack: MutableList<CompilerFeatureKey<*, *>>,
    ): List<CompilerFeatureKey<*, *>>? {
        when (states[key]) {
            VisitState.VISITED -> return null
            VisitState.VISITING -> return cycleFrom(stack, key)
            null -> Unit
        }

        states[key] = VisitState.VISITING
        stack += key
        val feature = requireNotNull(featuresByKey[key])
        for (dependencyKey in feature.dependencies.sorted()) {
            val cycle = findCycleFrom(dependencyKey, featuresByKey, states, stack)
            if (cycle != null) {
                return cycle
            }
        }
        stack.removeLast()
        states[key] = VisitState.VISITED
        return null
    }

    private fun cycleFrom(
        stack: List<CompilerFeatureKey<*, *>>,
        repeatedKey: CompilerFeatureKey<*, *>,
    ): List<CompilerFeatureKey<*, *>> {
        val cycleStart = stack.indexOf(repeatedKey)
        return stack.subList(cycleStart, stack.size) + repeatedKey
    }

    private enum class VisitState {
        VISITING,
        VISITED,
    }
}

private fun requireAnnotationQualifiedName(qualifiedName: String) {
    requireCanonicalDottedName(qualifiedName, "APT annotation type")
    require('.' in qualifiedName) {
        "APT annotation type must be qualified: '$qualifiedName'"
    }
}

private fun requireCompilerOptionName(optionName: String) {
    requireCanonicalDottedName(optionName, "Compiler option")
}

private fun requireCanonicalDottedName(value: String, role: String) {
    require(value.isNotBlank()) { "$role cannot be blank" }
    require(value == value.trim()) { "$role cannot have surrounding whitespace: '$value'" }
    require(value.split('.').all(String::isCanonicalIdentifier)) {
        "$role must be a period-separated sequence of identifiers: '$value'"
    }
}

private fun String.isCanonicalIdentifier(): Boolean {
    if (isEmpty() || !Character.isJavaIdentifierStart(first())) {
        return false
    }
    return drop(1).all(Character::isJavaIdentifierPart)
}

internal fun requireCompilerResourcePath(path: String) {
    require(path.isNotBlank()) { "Compiler resource path cannot be blank" }
    require(path == path.trim().replace('\\', '/')) {
        "Compiler resource path must be normalized: '$path'"
    }
    require(!path.startsWith('/')) { "Compiler resource path must be relative: '$path'" }
    require(path.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." }) {
        "Compiler resource path contains an invalid segment: '$path'"
    }
}
