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

    companion object {
        val EMPTY: CompilerFeatureState = object : CompilerFeatureState {
            override val fingerprint: String = "empty"

            override fun toString(): String = "CompilerFeatureState.EMPTY"
        }
    }
}

data class CompilerFeatureCollection(
    val state: CompilerFeatureState = CompilerFeatureState.EMPTY,
    val diagnostics: List<LsiDiagnostic> = emptyList(),
)

data class CompilerFeaturePrecompileResult(
    val state: CompilerFeatureState,
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

data class CompilerFeatureResult(
    val collection: CompilerFeatureCollection,
    val precompiled: CompilerFeaturePrecompileResult,
    val rendered: CompilerFeatureRenderResult,
) {
    val state: CompilerFeatureState
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

data class CompilerRoundResult(
    val round: CompilerRound,
    val fixedPointIterations: Int,
    val featureResults: Map<String, CompilerFeatureResult>,
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

data class CompilerPrecompileContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: CompilerFeatureCollection,
    val previousState: CompilerFeatureState?,
    val dependencyStates: Map<String, CompilerFeatureState>,
)

data class CompilerRenderContext(
    val session: CompilerSessionSnapshot,
    val round: CompilerRound,
    val collection: CompilerFeatureCollection,
    val state: CompilerFeatureState,
    val dependencyStates: Map<String, CompilerFeatureState>,
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
    val featureId: String,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '$featureId' generated source artifacts during final round: " +
        artifacts.joinToString { artifact -> artifact.path },
)

class FinalRoundIsolatingArtifactException(
    val featureId: String,
    val artifacts: List<GeneratedArtifact>,
) : IllegalStateException(
    "Compiler feature '$featureId' generated isolating artifacts during final round: " +
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
    providers: Iterable<CompilerFeatureProvider>,
    private val maximumFixedPointIterations: Int = 64,
) {
    private val orderedProviders = CompilerFeatureGraph.sort(providers)

    private val classpathTypeIds = orderedProviders
        .flatMapTo(sortedSetOf()) { provider -> provider.descriptor.classpathTypeIds }

    private val sourceQuiescentFeatureIds = orderedProviders
        .filter { provider -> provider.descriptor.requiresSourceQuiescence }
        .mapTo(sortedSetOf()) { provider -> provider.descriptor.id }

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
        for ((featureId, result) in featureResults) {
            validateFinalRoundOutput(round, featureId, result)
            roundArtifactSet.registerAll(result.artifacts)
            diagnostics += result.diagnostics
            val sourceKeys = result.artifacts
                .asSequence()
                .filter { artifact -> artifact.kind.isSource }
                .mapTo(mutableSetOf()) { artifact -> artifact.key }
            if (featureId in sourceQuiescentFeatureIds) {
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
            featureResults = featureResults.toMap(),
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
        return orderedProviders
            .flatMap { provider -> provider.requestTypeSeeds(context) }
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
            ?.mapValues { (_, result) -> result.precompiled }
            .orEmpty()
        var previousFingerprint = if (orderedProviders.isEmpty()) {
            emptyMap()
        } else {
            roundResults.lastOrNull()
                ?.featureResults
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
        collections: Map<String, CompilerFeatureCollection>,
        previousResults: Map<String, CompilerFeaturePrecompileResult>,
    ): Map<String, CompilerFeaturePrecompileResult> {
        val currentResults = linkedMapOf<String, CompilerFeaturePrecompileResult>()
        for (provider in orderedProviders) {
            val descriptor = provider.descriptor
            val dependencyStates = descriptor.dependsOn
                .sorted()
                .associateWith { dependencyId -> requireNotNull(currentResults[dependencyId]).state }
            val previousState = previousResults[descriptor.id]?.state
            currentResults[descriptor.id] = provider.precompile(
                CompilerPrecompileContext(
                    session = session,
                    round = round,
                    collection = requireNotNull(collections[descriptor.id]),
                    previousState = previousState,
                    dependencyStates = dependencyStates,
                ),
            )
        }
        return currentResults
    }

    private fun phaseFingerprint(
        featureResults: Map<String, CompilerFeatureResult>,
    ): Map<String, FeaturePhaseFingerprint> {
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
    ): Map<String, CompilerFeatureCollection> {
        return orderedProviders.associate { provider ->
            provider.descriptor.id to provider.collect(CompilerCollectContext(session, round))
        }
    }

    private fun render(
        session: CompilerSessionSnapshot,
        round: CompilerRound,
        collections: Map<String, CompilerFeatureCollection>,
        precompiledResults: Map<String, CompilerFeaturePrecompileResult>,
    ): Map<String, CompilerFeatureResult> {
        val results = linkedMapOf<String, CompilerFeatureResult>()
        for (provider in orderedProviders) {
            val descriptor = provider.descriptor
            val dependencyStates = descriptor.dependsOn
                .sorted()
                .associateWith { dependencyId -> requireNotNull(precompiledResults[dependencyId]).state }
            val collection = requireNotNull(collections[descriptor.id])
            val precompiled = requireNotNull(precompiledResults[descriptor.id])
            val rendered = provider.render(
                CompilerRenderContext(
                    session = session,
                    round = round,
                    collection = collection,
                    state = precompiled.state,
                    dependencyStates = dependencyStates,
                ),
            )
            results[descriptor.id] = CompilerFeatureResult(collection, precompiled, rendered)
        }
        return results
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
        featureId: String,
        result: CompilerFeatureResult,
    ) {
        if (!round.isFinal) {
            return
        }
        val sourceArtifacts = result.artifacts.filter { artifact -> artifact.kind.isSource }
        if (sourceArtifacts.isNotEmpty()) {
            throw FinalRoundSourceGenerationException(featureId, sourceArtifacts)
        }
        val isolatingArtifacts = result.artifacts.filter { artifact ->
            artifact.aggregationMode == ArtifactAggregationMode.ISOLATING
        }
        if (isolatingArtifacts.isNotEmpty()) {
            throw FinalRoundIsolatingArtifactException(featureId, isolatingArtifacts)
        }
    }

    private data class FixedPointResult(
        val iterations: Int,
        val results: Map<String, CompilerFeatureResult>,
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
