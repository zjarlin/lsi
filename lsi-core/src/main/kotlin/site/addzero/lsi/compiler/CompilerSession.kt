package site.addzero.lsi.compiler

import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactRegistration
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.codegen.GeneratedArtifactKey
import site.addzero.lsi.codegen.GeneratedArtifactSet
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.diagnostic.LsiDiagnostic
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.model.mergeLsiTypeSeeds

data class CompilerRound(
    val number: Int,
    val workspace: LsiWorkspace,
    val currentWorkspace: LsiWorkspace = workspace,
    val currentRootTypeIds: Set<LsiSymbolId>,
    val platform: CompilerPlatform = CompilerPlatform.UNKNOWN,
    val isFinal: Boolean = false,
    val options: Map<String, String> = emptyMap(),
    val availableTypeIds: Set<LsiSymbolId> = emptySet(),
    val frontendDeferred: Boolean = false,
    val inputDocumentDiscoveryComplete: Boolean = true,
    val inputResources: Map<String, String> = emptyMap(),
    val inputDocumentSnapshots: List<CompilerInputDocumentSnapshot>,
) {

    init {
        require(number >= 0) { "Compiler round number cannot be negative: $number" }
        currentRootTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        require(!isFinal || currentRootTypeIds.isEmpty()) {
            "Final compiler round cannot contain current root types"
        }
        require(currentRootTypeIds.all(currentWorkspace::contains)) {
            "Current compiler root types must exist in the current workspace"
        }
        require(options.keys.none(String::isBlank)) { "Compiler option name cannot be blank" }
        availableTypeIds.forEach(LsiSymbolId::requireTypeQualifiedName)
        inputResources.keys.forEach(::requireCompilerResourcePath)
        require(inputDocumentSnapshots == inputDocumentSnapshots.sorted()) {
            "Compiler input document snapshots must use stable source order"
        }
        require(
            inputDocumentSnapshots
                .distinctBy { snapshot -> snapshot.document.kind to snapshot.document.source.path }
                .size == inputDocumentSnapshots.size
        ) {
            "Compiler round cannot contain duplicate input document snapshots"
        }
    }
}

enum class CompilerPlatform {
    APT,
    KSP,
    UNKNOWN,
}

interface CompilerFeatureState {
    val fingerprint: String
}

data object EmptyCompilerFeatureState : CompilerFeatureState {
    override val fingerprint: String = "empty"
}

data class CompilerFeatureCollection<C : CompilerFeatureState>(
    val state: C,
    val diagnostics: List<LsiDiagnostic> = emptyList(),
)

data class CompilerFeaturePrecompileResult<S : CompilerFeatureState>(
    val state: S,
    val diagnostics: List<LsiDiagnostic> = emptyList(),
    val processedSymbols: Set<LsiSymbolId> = emptySet(),
    val unresolvedSymbols: Set<LsiSymbolId> = emptySet(),
) {

    init {
        require(state.fingerprint.isNotBlank()) { "Compiler feature state fingerprint cannot be blank" }
        val contradictorySymbols = processedSymbols intersect unresolvedSymbols
        require(contradictorySymbols.isEmpty()) {
            "Compiler feature cannot mark symbols as both processed and unresolved: " +
                contradictorySymbols.sorted().joinToString { symbol -> symbol.value }
        }
    }
}

data class CompilerFeatureRenderResult(
    val artifacts: List<GeneratedArtifact> = emptyList(),
    val diagnostics: List<LsiDiagnostic> = emptyList(),
)

data class CompilerFeatureResult<
    C : CompilerFeatureState,
    S : CompilerFeatureState,
>(
    val collection: CompilerFeatureCollection<C>,
    val precompiled: CompilerFeaturePrecompileResult<S>,
    val rendered: CompilerFeatureRenderResult,
) {
    val state: S
        get() = precompiled.state

    val artifacts: List<GeneratedArtifact>
        get() = rendered.artifacts

    val diagnostics: List<LsiDiagnostic>
        get() = collection.diagnostics + precompiled.diagnostics + rendered.diagnostics

    val processedSymbols: Set<LsiSymbolId>
        get() = precompiled.processedSymbols

    val unresolvedSymbols: Set<LsiSymbolId>
        get() = precompiled.unresolvedSymbols
}

class CompilerFeatureResults internal constructor(
    results: Map<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>>,
) {
    private val results = results.toMap()

    val keys: Set<CompilerFeatureKey<*, *>>
        get() = results.keys

    val values: Collection<CompilerFeatureResult<*, *>>
        get() = results.values

    val size: Int
        get() = results.size

    operator fun <C : CompilerFeatureState, S : CompilerFeatureState> get(
        key: CompilerFeatureKey<C, S>,
    ): CompilerFeatureResult<C, S>? {
        val result = results[key] ?: return null
        return key.typedResult(result)
    }

    fun <C : CompilerFeatureState, S : CompilerFeatureState> getValue(
        key: CompilerFeatureKey<C, S>,
    ): CompilerFeatureResult<C, S> {
        return requireNotNull(get(key)) { "Missing compiler feature result: '${key.id}'" }
    }

    internal fun erased(): Map<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>> = results

    override fun equals(other: Any?): Boolean {
        return this === other || other is CompilerFeatureResults && results == other.results
    }

    override fun hashCode(): Int = results.hashCode()

    override fun toString(): String = results.toString()
}

class CompilerFeatureStates(
    states: Map<CompilerFeatureKey<*, *>, CompilerFeatureState> = emptyMap(),
) {
    private val states = states.toMap().also { frozenStates ->
        frozenStates.forEach { (key, state) -> key.castState(state) }
    }

    val keys: Set<CompilerFeatureKey<*, *>>
        get() = states.keys

    val size: Int
        get() = states.size

    fun isEmpty(): Boolean = states.isEmpty()

    operator fun <C : CompilerFeatureState, S : CompilerFeatureState> get(
        key: CompilerFeatureKey<C, S>,
    ): S? {
        val state = states[key] ?: return null
        return key.castState(state)
    }

    fun <C : CompilerFeatureState, S : CompilerFeatureState> getValue(
        key: CompilerFeatureKey<C, S>,
    ): S {
        return requireNotNull(get(key)) { "Missing compiler feature state: '${key.id}'" }
    }

    companion object {
        val EMPTY = CompilerFeatureStates()
    }
}

data class CompilerRoundResult(
    val round: CompilerRound,
    val fixedPointIterations: Int,
    val featureResults: CompilerFeatureResults,
    val newArtifacts: List<GeneratedArtifact>,
    val diagnostics: List<LsiDiagnostic>,
) {
    val unresolvedSymbols: Set<LsiSymbolId>
        get() = featureResults.values.flatMapTo(sortedSetOf()) { result -> result.unresolvedSymbols }
}

data class CompilerSessionSnapshot(
    val id: String,
    val rounds: List<CompilerRoundResult>,
)

data class CompilerCollectContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
)

data class CompilerTypeSeedContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
)

data class CompilerPrecompileContext<
    C : CompilerFeatureState,
    S : CompilerFeatureState,
>(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: CompilerFeatureCollection<C>,
    val previousState: S?,
    val dependencyStates: CompilerFeatureStates,
)

data class CompilerRenderContext<
    C : CompilerFeatureState,
    S : CompilerFeatureState,
>(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: CompilerFeatureCollection<C>,
    val state: S,
    val dependencyStates: CompilerFeatureStates,
)

class CompilerSessionStateException(message: String) : IllegalStateException(message)

class CompilerFixedPointException(
    sessionId: String,
    roundNumber: Int,
    val maximumIterations: Int,
) : IllegalStateException(
    "Compiler session '$sessionId' round $roundNumber did not reach a fixed point after $maximumIterations iterations",
)

class FinalRoundSourceGenerationException(
    val featureKey: CompilerFeatureKey<*, *>,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '${featureKey.id}' generated source artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path },
)

class FinalRoundIsolatingArtifactException(
    val featureKey: CompilerFeatureKey<*, *>,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '${featureKey.id}' generated isolating artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path },
)

class PendingStableSourceArtifactsException(
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler session reached the final round before stable source artifacts converged: " +
        artifacts.joinToString { artifact -> artifact.path },
)

/**
 * 在真实 APT/KSP 轮次之间保存纯 LSI 状态，并在每轮执行预编译固定点。
 */
class CompilerSession(
    val id: String,
    features: Iterable<CompilerFeature<*, *>>,
    private val maximumFixedPointIterations: Int = 64,
) {
    private val orderedFeatures = CompilerFeatureGraph.sort(features)

    private val classpathTypeIds = orderedFeatures
        .flatMapTo(sortedSetOf()) { feature -> feature.metadata.classpathTypeIds }

    private val sourceQuiescentFeatureKeys = orderedFeatures
        .filter { feature -> feature.metadata.requiresSourceQuiescence }
        .mapTo(sortedSetOf()) { feature -> feature.key }

    private val artifactSet = GeneratedArtifactSet()

    private val stableArtifactCandidates = linkedMapOf<GeneratedArtifactKey, StableArtifactCandidate>()

    private val roundResults = mutableListOf<CompilerRoundResult>()

    private var finalRoundCompleted = false

    init {
        require(id.isNotBlank()) { "Compiler session id cannot be blank" }
        require(maximumFixedPointIterations >= 1) {
            "Compiler maximum fixed point iterations must be positive: $maximumFixedPointIterations"
        }
    }

    fun execute(round: CompilerRound): CompilerRoundResult {
        validateRound(round)

        val sessionSnapshot = snapshot()
        val fixedPoint = fixedPoint(sessionSnapshot, round)
        val featureResults = fixedPoint.results
        val roundArtifactSet = GeneratedArtifactSet()
        val stagedArtifactSet = GeneratedArtifactSet(artifactSet.snapshot())
        val stagedStableCandidates = LinkedHashMap(stableArtifactCandidates)
        val newArtifacts = mutableListOf<GeneratedArtifact>()
        val diagnostics = mutableListOf<LsiDiagnostic>()
        val quiescentSourceKeys = mutableSetOf<GeneratedArtifactKey>()
        val firstPhaseSourceKeys = mutableSetOf<GeneratedArtifactKey>()
        for ((featureKey, result) in featureResults) {
            validateFinalRoundOutput(round, featureKey, result)
            roundArtifactSet.registerAll(result.artifacts)
            diagnostics += result.diagnostics
            val sourceKeys = result.artifacts
                .asSequence()
                .filter { artifact -> artifact.kind.isSource }
                .mapTo(mutableSetOf()) { artifact -> artifact.key }
            if (featureKey in sourceQuiescentFeatureKeys) {
                quiescentSourceKeys += sourceKeys
            } else {
                firstPhaseSourceKeys += sourceKeys
            }
        }
        if (round.isFinal && stagedStableCandidates.isNotEmpty()) {
            throw PendingStableSourceArtifactsException(
                stagedStableCandidates.values
                    .map(StableArtifactCandidate::artifact)
                    .sortedBy(GeneratedArtifact::key),
            )
        }
        val currentStableKeys = mutableSetOf<GeneratedArtifactKey>()
        val exclusivelyQuiescentSourceKeys = quiescentSourceKeys - firstPhaseSourceKeys
        val (quiescentSources, firstPhaseArtifacts) = roundArtifactSet
            .snapshot()
            .partition { artifact -> artifact.key in exclusivelyQuiescentSourceKeys }
        val firstPhaseSourceEmitted = firstPhaseArtifacts.fold(false) { sourceEmitted, artifact ->
            stageArtifact(
                artifact = artifact,
                roundNumber = round.number,
                allowEmission = true,
                stagedArtifactSet = stagedArtifactSet,
                stagedStableCandidates = stagedStableCandidates,
                currentStableKeys = currentStableKeys,
                newArtifacts = newArtifacts,
            ) || sourceEmitted
        }
        for (artifact in quiescentSources) {
            stageArtifact(
                artifact = artifact,
                roundNumber = round.number,
                allowEmission = !firstPhaseSourceEmitted,
                stagedArtifactSet = stagedArtifactSet,
                stagedStableCandidates = stagedStableCandidates,
                currentStableKeys = currentStableKeys,
                newArtifacts = newArtifacts,
            )
        }
        if (!round.isFinal) {
            stagedStableCandidates.keys.retainAll(currentStableKeys)
        }

        val roundResult = CompilerRoundResult(
            round = round,
            fixedPointIterations = fixedPoint.iterations,
            featureResults = CompilerFeatureResults(featureResults),
            newArtifacts = newArtifacts.sortedBy(GeneratedArtifact::key),
            diagnostics = diagnostics.toList(),
        )
        artifactSet.registerAll(newArtifacts)
        stableArtifactCandidates.clear()
        stableArtifactCandidates.putAll(stagedStableCandidates)
        roundResults += roundResult
        finalRoundCompleted = round.isFinal
        return roundResult
    }

    /**
     * 在正式执行当前轮之前查询功能所需的额外类型声明，不推进会话状态。
     */
    fun requestedTypeSeeds(round: CompilerRound): List<LsiTypeSeed> {
        validateRound(round)
        require(!round.isFinal) { "Final compiler round cannot request additional type declarations" }
        val context = CompilerTypeSeedContext(snapshot(), round)
        return orderedFeatures
            .flatMap { feature -> feature.requestTypeSeeds(context) }
            .mergeLsiTypeSeeds()
    }

    fun snapshot(): CompilerSessionSnapshot = CompilerSessionSnapshot(id, roundResults.toList())

    fun artifacts(): List<GeneratedArtifact> = artifactSet.snapshot()

    fun pendingStableSourceOriginatingSymbols(): Set<LsiSymbolId> {
        return stableArtifactCandidates.values
            .flatMapTo(sortedSetOf()) { candidate -> candidate.artifact.originatingSymbols }
    }

    private fun stageArtifact(
        artifact: GeneratedArtifact,
        roundNumber: Int,
        allowEmission: Boolean,
        stagedArtifactSet: GeneratedArtifactSet,
        stagedStableCandidates: MutableMap<GeneratedArtifactKey, StableArtifactCandidate>,
        currentStableKeys: MutableSet<GeneratedArtifactKey>,
        newArtifacts: MutableList<GeneratedArtifact>,
    ): Boolean {
        return when (artifact.emissionMode) {
            ArtifactEmissionMode.IMMEDIATE -> {
                if (!allowEmission) {
                    val emitted = stagedArtifactSet[artifact.key]
                    if (emitted != null && emitted != artifact) {
                        throw GeneratedArtifactConflictException(emitted, artifact)
                    }
                    false
                } else if (stagedArtifactSet.register(artifact) == ArtifactRegistration.ADDED) {
                    newArtifacts += artifact
                    artifact.kind.isSource
                } else {
                    false
                }
            }
            ArtifactEmissionMode.STABLE -> {
                currentStableKeys += artifact.key
                val emitted = stagedArtifactSet[artifact.key]
                if (emitted != null) {
                    if (emitted.stableEmissionFingerprint() != artifact.stableEmissionFingerprint()) {
                        throw GeneratedArtifactConflictException(emitted, artifact)
                    }
                    stagedStableCandidates.remove(artifact.key)
                    false
                } else {
                    val candidate = stagedStableCandidates[artifact.key]
                    if (
                        allowEmission &&
                        candidate != null &&
                        candidate.roundNumber == roundNumber - 1 &&
                        candidate.artifact.stableEmissionFingerprint() == artifact.stableEmissionFingerprint()
                    ) {
                        stagedArtifactSet.register(artifact)
                        stagedStableCandidates.remove(artifact.key)
                        newArtifacts += artifact
                        true
                    } else {
                        stagedStableCandidates[artifact.key] = StableArtifactCandidate(artifact, roundNumber)
                        false
                    }
                }
            }
        }
    }

    /**
     * 收集、预编译和渲染必须观察同一个稳定状态。
     *
     * 预编译结果会影响渲染结果，渲染结果又会决定下一轮真正可见的源码，
     * 因此不能只对预编译状态做固定点判断后就把第一次渲染交给 filer。
     */
    private fun fixedPoint(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
    ): FixedPointResult {
        var previousPrecompiledResults = roundResults.lastOrNull()?.featureResults
            ?.erased()
            ?.mapValues { (_, result) -> result.precompiled }
            .orEmpty()
        var previousFingerprint = if (orderedFeatures.isEmpty()) {
            emptyMap()
        } else {
            roundResults.lastOrNull()
                ?.featureResults
                ?.erased()
                ?.let(::phaseFingerprint)
        }
        repeat(maximumFixedPointIterations) { iteration ->
            val collections = collect(session, round)
            val currentPrecompiledResults = precompile(
                session = session,
                round = round,
                collections = collections,
                previousResults = previousPrecompiledResults,
            )
            val currentResults = render(session, round, collections, currentPrecompiledResults)
            val currentFingerprint = phaseFingerprint(currentResults)
            if (previousFingerprint != null && previousFingerprint == currentFingerprint) {
                return FixedPointResult(iteration + 1, currentResults)
            }
            previousPrecompiledResults = currentPrecompiledResults
            previousFingerprint = currentFingerprint
        }
        throw CompilerFixedPointException(id, round.number, maximumFixedPointIterations)
    }

    private fun precompile(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<CompilerFeatureKey<*, *>, CompilerFeatureCollection<*>>,
        previousResults: Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>,
    ): Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>> {
        val currentResults = linkedMapOf<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>()
        for (feature in orderedFeatures) {
            currentResults[feature.key] = feature.precompileCaptured(
                session = session,
                round = round,
                collections = collections,
                previousResults = previousResults,
                currentResults = currentResults,
            )
        }
        return currentResults
    }

    private fun <C : CompilerFeatureState, S : CompilerFeatureState> CompilerFeature<C, S>.precompileCaptured(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<CompilerFeatureKey<*, *>, CompilerFeatureCollection<*>>,
        previousResults: Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>,
        currentResults: Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>,
    ): CompilerFeaturePrecompileResult<S> {
        val dependencyStates = CompilerFeatureStates(
            dependencies
                .sorted()
                .associateWith { dependencyKey -> requireNotNull(currentResults[dependencyKey]).state },
        )
        val previousState = previousResults[key]?.state?.let(key::castState)
        val collection = key.typedCollection(requireNotNull(collections[key]))
        val result = precompile(
            CompilerPrecompileContext(
                session = session,
                round = round,
                collection = collection,
                previousState = previousState,
                dependencyStates = dependencyStates,
            ),
        )
        key.castState(result.state)
        return result
    }

    private fun phaseFingerprint(
        featureResults: Map<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>>,
    ): Map<CompilerFeatureKey<*, *>, FeaturePhaseFingerprint> {
        return featureResults
            .toSortedMap()
            .mapValues { (_, result) ->
                FeaturePhaseFingerprint(
                    collectionState = result.collection.state.fingerprint,
                    collectionDiagnostics = result.collection.diagnostics,
                    state = result.precompiled.state.fingerprint,
                    precompileDiagnostics = result.precompiled.diagnostics,
                    processedSymbols = result.precompiled.processedSymbols.sorted(),
                    unresolvedSymbols = result.precompiled.unresolvedSymbols.sorted(),
                    artifacts = result.rendered.artifacts.sortedBy(GeneratedArtifact::key),
                    renderDiagnostics = result.rendered.diagnostics,
                )
            }
    }

    private data class FeaturePhaseFingerprint(
        val collectionState: String,
        val collectionDiagnostics: List<LsiDiagnostic>,
        val state: String,
        val precompileDiagnostics: List<LsiDiagnostic>,
        val processedSymbols: List<LsiSymbolId>,
        val unresolvedSymbols: List<LsiSymbolId>,
        val artifacts: List<GeneratedArtifact>,
        val renderDiagnostics: List<LsiDiagnostic>,
    )

    private fun collect(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
    ): Map<CompilerFeatureKey<*, *>, CompilerFeatureCollection<*>> {
        return orderedFeatures.associate { feature ->
            feature.key to feature.collect(CompilerCollectContext(session, round))
        }
    }

    private fun render(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<CompilerFeatureKey<*, *>, CompilerFeatureCollection<*>>,
        precompiledResults: Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>,
    ): Map<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>> {
        val results = linkedMapOf<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>>()
        for (feature in orderedFeatures) {
            results[feature.key] = feature.renderCaptured(
                session = session,
                round = round,
                collections = collections,
                precompiledResults = precompiledResults,
            )
        }
        return results
    }

    private fun <C : CompilerFeatureState, S : CompilerFeatureState> CompilerFeature<C, S>.renderCaptured(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<CompilerFeatureKey<*, *>, CompilerFeatureCollection<*>>,
        precompiledResults: Map<CompilerFeatureKey<*, *>, CompilerFeaturePrecompileResult<*>>,
    ): CompilerFeatureResult<C, S> {
        val dependencyStates = CompilerFeatureStates(
            dependencies
                .sorted()
                .associateWith { dependencyKey -> requireNotNull(precompiledResults[dependencyKey]).state },
        )
        val collection = key.typedCollection(requireNotNull(collections[key]))
        val precompiled = key.typedPrecompileResult(requireNotNull(precompiledResults[key]))
        val rendered = render(
            CompilerRenderContext(
                session = session,
                round = round,
                collection = collection,
                state = precompiled.state,
                dependencyStates = dependencyStates,
            ),
        )
        return CompilerFeatureResult(collection, precompiled, rendered)
    }

    private fun validateRound(round: CompilerRound) {
        if (finalRoundCompleted) {
            throw CompilerSessionStateException("Compiler session '$id' has already completed its final round")
        }
        if (round.number != roundResults.size) {
            throw CompilerSessionStateException(
                "Compiler session '$id' expected round ${roundResults.size}, got ${round.number}",
            )
        }
        require(round.availableTypeIds.all(classpathTypeIds::contains)) {
            "Available compiler types must be declared by a compiler feature"
        }
    }

    private fun validateFinalRoundOutput(
        round: CompilerRound,
        featureKey: CompilerFeatureKey<*, *>,
        result: CompilerFeatureResult<*, *>,
    ) {
        if (!round.isFinal) {
            return
        }
        val sourceArtifacts = result.artifacts.filter { artifact -> artifact.kind.isSource }
        if (sourceArtifacts.isNotEmpty()) {
            throw FinalRoundSourceGenerationException(featureKey, sourceArtifacts)
        }
        val isolatingArtifacts = result.artifacts.filter { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING
        }
        if (isolatingArtifacts.isNotEmpty()) {
            throw FinalRoundIsolatingArtifactException(featureKey, isolatingArtifacts)
        }
    }

    private data class FixedPointResult(
        val iterations: Int,
        val results: Map<CompilerFeatureKey<*, *>, CompilerFeatureResult<*, *>>,
    )

    private data class StableArtifactCandidate(
        val artifact: GeneratedArtifact,
        val roundNumber: Int,
    )

    /**
     * 稳定源码只按实际发射语义收敛，忽略前端在不同轮次观察到的来源投影。
     */
    private data class StableArtifactEmissionFingerprint(
        val key: GeneratedArtifactKey,
        val content: String,
        val aggregationMode: ArtifactAggregationMode,
        val emissionMode: ArtifactEmissionMode,
        val originatingSymbols: Set<LsiSymbolId>,
        val dependencySymbols: Set<LsiSymbolId>,
    )

    private fun GeneratedArtifact.stableEmissionFingerprint(): StableArtifactEmissionFingerprint {
        return StableArtifactEmissionFingerprint(
            key = key,
            content = content,
            aggregationMode = aggregationMode,
            emissionMode = emissionMode,
            originatingSymbols = originatingSymbols,
            dependencySymbols = dependencySymbols,
        )
    }
}

private fun <C : CompilerFeatureState, S : CompilerFeatureState> CompilerFeatureKey<C, S>.typedCollection(
    collection: CompilerFeatureCollection<*>,
): CompilerFeatureCollection<C> {
    return CompilerFeatureCollection(
        state = castCollectionState(collection.state),
        diagnostics = collection.diagnostics,
    )
}

private fun <C : CompilerFeatureState, S : CompilerFeatureState> CompilerFeatureKey<C, S>.typedPrecompileResult(
    result: CompilerFeaturePrecompileResult<*>,
): CompilerFeaturePrecompileResult<S> {
    return CompilerFeaturePrecompileResult(
        state = castState(result.state),
        diagnostics = result.diagnostics,
        processedSymbols = result.processedSymbols,
        unresolvedSymbols = result.unresolvedSymbols,
    )
}

private fun <C : CompilerFeatureState, S : CompilerFeatureState> CompilerFeatureKey<C, S>.typedResult(
    result: CompilerFeatureResult<*, *>,
): CompilerFeatureResult<C, S> {
    return CompilerFeatureResult(
        collection = typedCollection(result.collection),
        precompiled = typedPrecompileResult(result.precompiled),
        rendered = result.rendered,
    )
}
