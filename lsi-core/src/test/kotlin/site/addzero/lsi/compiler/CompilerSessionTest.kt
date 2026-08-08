package site.addzero.lsi.compiler

import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.GeneratedArtifactConflictException
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSourceKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeSeed
import site.addzero.lsi.model.LsiTypeSeedMode
import site.addzero.lsi.model.LsiWorkspace

class CompilerSessionTest {

    @Test
    fun `类型声明请求按符号合并并由完整声明优先`() {
        val alphaId = LsiSymbolId.type("example.Alpha")
        val betaId = LsiSymbolId.type("example.Beta")
        val headerFeature = HeaderSeedFeature(alphaId, betaId)
        val fullFeature = FullSeedFeature(betaId)
        val session = CompilerSession("type-seeds", listOf(fullFeature, headerFeature))

        assertEquals(
            listOf(
                LsiTypeSeed(alphaId, LsiTypeSeedMode.HEADER),
                LsiTypeSeed(betaId, LsiTypeSeedMode.FULL_DECLARATION),
            ),
            session.requestedTypeSeeds(emptyRound(0)),
        )
    }

    @Test
    fun `类型声明请求不会执行功能或推进会话轮次`() {
        val invocations = FeatureInvocations()
        val feature = SeedOnlyFeature(invocations)
        val session = CompilerSession("seed-query", listOf(feature))

        session.requestedTypeSeeds(emptyRound(0))
        session.requestedTypeSeeds(emptyRound(0))

        assertEquals(FeatureInvocations(), invocations)
        assertTrue(session.snapshot().rounds.isEmpty())
    }

    @Test
    fun `最终轮禁止请求额外类型声明`() {
        val session = CompilerSession("final-seed-query", emptyList())

        val exception = assertFailsWith<IllegalArgumentException> {
            session.requestedTypeSeeds(emptyRound(0, isFinal = true))
        }

        assertEquals("Final compiler round cannot request additional type declarations", exception.message)
    }

    @Test
    fun `空功能图在一次固定点迭代内完成`() {
        val result = CompilerSession(
            id = "empty-feature-graph",
            features = emptyList(),
            maximumFixedPointIterations = 1,
        ).execute(emptyRound(0))

        assertEquals(1, result.fixedPointIterations)
        assertEquals(0, result.featureResults.size)
    }

    @Test
    fun `round exposes frozen input resources to features`() {
        val feature = ResourceReaderFeature()
        val result = CompilerSession("input-resource-test", listOf(feature)).execute(
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = emptySet(),
                inputResources = mapOf("META-INF/jimmer/entities" to "demo.Book\n"),
                inputDocumentSnapshots = emptyList(),
            )
        )

        assertEquals(
            "demo.Book\n",
            result.featureResults.getValue(ResourceReaderFeature.KEY).state.fingerprint,
        )
    }

    @Test
    fun `round rejects current roots outside current workspace`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentWorkspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = setOf(LsiSymbolId.type("example.Drifting")),
                inputDocumentSnapshots = emptyList(),
            )
        }

        assertEquals(
            "Current compiler root types must exist in the current workspace",
            exception.message,
        )
    }

    @Test
    fun `final round rejects current roots`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerRound(
                number = 0,
                workspace = LsiWorkspace.EMPTY,
                currentRootTypeIds = setOf(LsiSymbolId.type("example.Final")),
                isFinal = true,
                inputDocumentSnapshots = emptyList(),
            )
        }

        assertEquals("Final compiler round cannot contain current root types", exception.message)
    }

    @Test
    fun `多轮会话按阶段传递依赖和上一轮快照`() {
        val executions = mutableListOf<FeatureExecution>()
        val immutable = ImmutableRecordingFeature(executions)
        val client = ClientRecordingFeature(executions)
        val session = CompilerSession("test", listOf(client, immutable))

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1, isFinal = true))

        assertEquals(
            listOf(
                FeatureExecution(ImmutableRecordingFeature.KEY, 0, 0, emptySet()),
                FeatureExecution(ClientRecordingFeature.KEY, 0, 0, setOf(ImmutableRecordingFeature.KEY)),
                FeatureExecution(ImmutableRecordingFeature.KEY, 0, 0, emptySet()),
                FeatureExecution(ClientRecordingFeature.KEY, 0, 0, setOf(ImmutableRecordingFeature.KEY)),
                FeatureExecution(ImmutableRecordingFeature.KEY, 1, 1, emptySet()),
                FeatureExecution(ClientRecordingFeature.KEY, 1, 1, setOf(ImmutableRecordingFeature.KEY)),
                FeatureExecution(ImmutableRecordingFeature.KEY, 1, 1, emptySet()),
                FeatureExecution(ClientRecordingFeature.KEY, 1, 1, setOf(ImmutableRecordingFeature.KEY)),
            ),
            executions,
        )
        assertEquals(2, first.fixedPointIterations)
        assertEquals(2, second.fixedPointIterations)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(first, session.snapshot().rounds.first())
        assertEquals(second, session.snapshot().rounds.last())
    }

    @Test
    fun `预编译会执行到稳定固定点`() {
        val invocations = FeatureInvocations()
        val feature = ConvergingFeature(invocations)

        val result = CompilerSession("fixed-point", listOf(feature))
            .execute(emptyRound(0))

        assertEquals(4, result.fixedPointIterations)
        assertEquals(4, invocations.precompiles)
        assertEquals(2, result.featureResults.getValue(ConvergingFeature.KEY).state.value)
    }

    @Test
    fun `收集和渲染也必须达到稳定固定点`() {
        val invocations = FeatureInvocations()
        val feature = AllPhasesFeature(invocations)

        val result = CompilerSession("all-phases", listOf(feature)).execute(emptyRound(0))

        assertEquals(4, result.fixedPointIterations)
        assertEquals(4, invocations.collects)
        assertEquals(4, invocations.renders)
        assertEquals(
            "2",
            result.featureResults.getValue(AllPhasesFeature.KEY).artifacts.single().content,
        )
    }

    @Test
    fun `跨轮完全相同的资源不重复写出`() {
        val resource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "{}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val session = CompilerSession(
            "resource",
            listOf(ClientArtifactFeature(listOf(resource))),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1, isFinal = true))

        assertEquals(listOf(resource), first.newArtifacts)
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(1, second.fixedPointIterations)
        assertEquals(listOf(resource), session.artifacts())
    }

    @Test
    fun `稳定源码连续两个有效轮相同后才写出`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val session = CompilerSession(
            "stable-source",
            listOf(ImmutableArtifactFeature(listOf(source))),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertTrue(first.newArtifacts.isEmpty())
        assertTrue(session.snapshot().rounds.first().newArtifacts.isEmpty())
        assertEquals(listOf(source), second.newArtifacts)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `稳定源码忽略轮次来源投影后在第二轮写出`() {
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE).withSourceProjection(
            path = "src/main/java/example/Book.java",
            kind = LsiSourceKind.SOURCE,
        )
        val second = first.withSourceProjection(
            path = "build/generated/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val session = CompilerSession(
            "stable-source-projection",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(if (round == 0) first else second)
                }
            ),
        )

        assertTrue(session.execute(emptyRound(0)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码的符号依赖变化后继续等待`() {
        val subtypeId = LsiSymbolId.type("example.SpecialBook")
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val second = first.copy(dependencySymbols = first.dependencySymbols + subtypeId)
        val session = CompilerSession(
            "stable-source-symbol-dependency",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(if (round == 0) first else second)
                }
            ),
        )

        assertTrue(session.execute(emptyRound(0)).newArtifacts.isEmpty())
        assertTrue(session.execute(emptyRound(1)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.execute(emptyRound(2)).newArtifacts)
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码候选暴露下一轮需重冻的来源符号`() {
        val firstTypeId = LsiSymbolId.type("example.Book")
        val secondTypeId = LsiSymbolId.type("example.SpecialBook")
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE).copy(
            originatingSymbols = setOf(firstTypeId, secondTypeId),
            dependencySymbols = setOf(firstTypeId, secondTypeId),
        )
        val session = CompilerSession(
            "stable-source-origins",
            listOf(ImmutableArtifactFeature(listOf(source))),
        )

        session.execute(emptyRound(0))

        assertEquals(
            setOf(firstTypeId, secondTypeId),
            session.pendingStableSourceOriginatingSymbols(),
        )

        session.execute(emptyRound(1))

        assertTrue(session.pendingStableSourceOriginatingSymbols().isEmpty())
    }

    @Test
    fun `稳定源码变化后重新等待连续相同轮`() {
        val firstSource = generatedSource("first", ArtifactEmissionMode.STABLE)
        val secondSource = firstSource.copy(content = "second")
        val session = CompilerSession(
            "changing-stable-source",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(if (round == 0) firstSource else secondSource)
                }
            ),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))
        val third = session.execute(emptyRound(2))

        assertTrue(first.newArtifacts.isEmpty())
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(listOf(secondSource), third.newArtifacts)
        assertEquals(listOf(secondSource), session.artifacts())
    }

    @Test
    fun `稳定源码写出后变化直接冲突`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val changed = source.copy(content = "changed")
        val session = CompilerSession(
            "emitted-stable-source-conflict",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(if (round < 2) source else changed)
                }
            ),
        )

        session.execute(emptyRound(0))
        session.execute(emptyRound(1))
        val exception = assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(emptyRound(2))
        }

        assertEquals(source, exception.existing)
        assertEquals(changed, exception.incoming)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `稳定源码写出后允许来源投影变化`() {
        val first = generatedSource("stable", ArtifactEmissionMode.STABLE).withSourceProjection(
            path = "src/main/java/example/Book.java",
            kind = LsiSourceKind.SOURCE,
        )
        val second = first.withSourceProjection(
            path = "build/generated/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val third = second.withSourceProjection(
            path = "build/generated/round-2/example/Book.java",
            kind = LsiSourceKind.GENERATED,
        )
        val session = CompilerSession(
            "emitted-stable-source-projection",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(
                        when (round) {
                            0 -> first
                            1 -> second
                            else -> third
                        }
                    )
                }
            ),
        )

        session.execute(emptyRound(0))
        assertEquals(listOf(second), session.execute(emptyRound(1)).newArtifacts)
        assertTrue(session.execute(emptyRound(2)).newArtifacts.isEmpty())
        assertEquals(listOf(second), session.artifacts())
    }

    @Test
    fun `稳定源码写出后符号依赖变化直接冲突`() {
        val source = generatedSource("stable", ArtifactEmissionMode.STABLE)
        val changed = source.copy(
            dependencySymbols = source.dependencySymbols + LsiSymbolId.type("example.SpecialBook"),
        )
        val session = CompilerSession(
            "emitted-stable-source-symbol-conflict",
            listOf(
                ImmutableArtifactFeature { round ->
                    listOf(if (round < 2) source else changed)
                }
            ),
        )

        session.execute(emptyRound(0))
        session.execute(emptyRound(1))
        val exception = assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(emptyRound(2))
        }

        assertEquals(source, exception.existing)
        assertEquals(changed, exception.incoming)
        assertEquals(2, session.snapshot().rounds.size)
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `最终轮仍有稳定源码候选时直接失败`() {
        val source = generatedSource("pending", ArtifactEmissionMode.STABLE)
        val session = CompilerSession(
            "pending-stable-source",
            listOf(
                ImmutableArtifactFeature { round ->
                    if (round == 0) listOf(source) else emptyList()
                }
            ),
        )

        session.execute(emptyRound(0))
        val exception = assertFailsWith<PendingStableSourceArtifactsException> {
            session.execute(emptyRound(1, isFinal = true))
        }

        assertEquals(listOf(source), exception.artifacts)
        assertEquals(1, session.snapshot().rounds.size)
        assertTrue(session.artifacts().isEmpty())
    }

    @Test
    fun `即时源码仍在首个有效轮写出且相同内容不重复`() {
        val source = generatedSource("immediate", ArtifactEmissionMode.IMMEDIATE)
        val session = CompilerSession(
            "immediate-source",
            listOf(ImmutableArtifactFeature(listOf(source))),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertEquals(listOf(source), first.newArtifacts)
        assertTrue(second.newArtifacts.isEmpty())
        assertEquals(listOf(source), session.artifacts())
    }

    @Test
    fun `源码静默功能等待其他功能本轮真正新增的源码`() {
        val immutableSource = generatedSource(
            content = "immutable",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val dtoResource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/dto",
            content = "dto",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val session = CompilerSession(
            "source-quiescence",
            listOf(
                ImmutableArtifactFeature(listOf(immutableSource)),
                DtoArtifactFeature(listOf(dtoSource, dtoResource)),
            ),
        )

        val first = session.execute(emptyRound(0))
        val second = session.execute(emptyRound(1))

        assertEquals(listOf(immutableSource, dtoResource).sortedBy(GeneratedArtifact::key), first.newArtifacts)
        assertEquals(listOf(dtoSource), second.newArtifacts)
        assertEquals(
            listOf(immutableSource, dtoSource, dtoResource).sortedBy(GeneratedArtifact::key),
            session.artifacts(),
        )
    }

    @Test
    fun `稳定源码本轮成熟时继续阻止源码静默功能`() {
        val firstSource = generatedSource(
            content = "first",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.FirstDraft",
        )
        val stableSource = generatedSource(
            content = "stable",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.StableDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "stable-source-quiescence",
            listOf(
                ImmutableArtifactFeature { round ->
                    if (round == 0) listOf(firstSource, stableSource) else listOf(stableSource)
                },
                DtoArtifactFeature(listOf(dtoSource)),
            ),
        )

        assertEquals(listOf(firstSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(stableSource), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(dtoSource), session.execute(emptyRound(2)).newArtifacts)
    }

    @Test
    fun `普通稳定源码首轮候选不阻止源码静默功能`() {
        val stableSource = generatedSource(
            content = "stable",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.StableDraft",
        )
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "stable-candidate-source-quiescence",
            listOf(
                ImmutableArtifactFeature(listOf(stableSource)),
                DtoArtifactFeature(listOf(dtoSource)),
            ),
        )

        assertEquals(listOf(dtoSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(stableSource), session.execute(emptyRound(1)).newArtifacts)
    }

    @Test
    fun `多个源码静默功能在同一静默轮一起写出`() {
        val dtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookView",
        )
        val moduleSource = generatedSource(
            content = "module",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.JimmerModule",
        )
        val session = CompilerSession(
            "shared-source-quiescence",
            listOf(
                DtoArtifactFeature(listOf(dtoSource)),
                QuiescentModuleArtifactFeature(listOf(moduleSource)),
            ),
        )

        assertEquals(
            listOf(dtoSource, moduleSource).sortedBy(GeneratedArtifact::key),
            session.execute(emptyRound(0)).newArtifacts,
        )
    }

    @Test
    fun `被阻止的成熟静默稳定源码刷新当前轮候选`() {
        val triggerSource = generatedSource(
            content = "trigger",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.TriggerView",
        )
        val immutableSource = generatedSource(
            content = "immutable",
            emissionMode = ArtifactEmissionMode.IMMEDIATE,
            qualifiedName = "example.BookDraft",
        )
        val stableDtoSource = generatedSource(
            content = "dto",
            emissionMode = ArtifactEmissionMode.STABLE,
            qualifiedName = "example.BookView",
        )
        val session = CompilerSession(
            "mature-stable-quiescent-source",
            listOf(
                ImmutableArtifactFeature { round ->
                    if (round == 1) listOf(immutableSource) else emptyList()
                },
                DtoArtifactFeature(listOf(triggerSource, stableDtoSource)),
            ),
        )

        assertEquals(listOf(triggerSource), session.execute(emptyRound(0)).newArtifacts)
        assertEquals(listOf(immutableSource), session.execute(emptyRound(1)).newArtifacts)
        assertEquals(listOf(stableDtoSource), session.execute(emptyRound(2)).newArtifacts)
    }

    @Test
    fun `最终轮禁止源码静默功能生成源码`() {
        val sourceId = LsiSymbolId.type("example.Book")
        val source = GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = "example.BookDraft",
            content = "package example; class BookDraft {}",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            originatingSymbols = setOf(sourceId),
        )
        val session = CompilerSession(
            "final-source",
            listOf(DtoArtifactFeature(listOf(source))),
        )

        val exception = assertFailsWith<FinalRoundSourceGenerationException> {
            session.execute(
                emptyRound(0, isFinal = true)
            )
        }

        assertEquals(DtoArtifactFeature.KEY, exception.featureKey)
        assertEquals(listOf(source), exception.artifacts)
    }

    @Test
    fun `最终轮只允许聚合资源`() {
        val sourceId = LsiSymbolId.type("example.Book")
        val resource = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/example.Book",
            content = "example.Book",
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(sourceId),
        )
        val session = CompilerSession(
            "final-isolating",
            listOf(ModuleArtifactFeature(listOf(resource))),
        )

        val exception = assertFailsWith<FinalRoundIsolatingArtifactException> {
            session.execute(
                emptyRound(0, isFinal = true)
            )
        }

        assertEquals(ModuleArtifactFeature.KEY, exception.featureKey)
        assertEquals(listOf(resource), exception.artifacts)
    }

    @Test
    fun `轮次产物冲突时会话保持原状`() {
        val first = GeneratedArtifact.create(
            kind = ArtifactKind.RESOURCE,
            path = "META-INF/jimmer/client",
            content = "first",
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
        )
        val conflict = first.copy(content = "second")
        val session = CompilerSession(
            "atomic-round",
            listOf(
                FirstArtifactFeature(listOf(first)),
                SecondArtifactFeature(listOf(conflict)),
            ),
        )

        assertFailsWith<GeneratedArtifactConflictException> {
            session.execute(emptyRound(0))
        }

        assertTrue(session.snapshot().rounds.isEmpty())
        assertTrue(session.artifacts().isEmpty())
    }

    @Test
    fun `固定点不收敛时直接失败`() {
        val feature = UnstableFeature()
        val session = CompilerSession(
            id = "unstable",
            features = listOf(feature),
            maximumFixedPointIterations = 3,
        )

        val exception = assertFailsWith<CompilerFixedPointException> {
            session.execute(emptyRound(0))
        }

        assertEquals(3, exception.maximumIterations)
        assertTrue(session.snapshot().rounds.isEmpty())
    }

    private fun emptyRound(
        number: Int,
        isFinal: Boolean = false,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = LsiWorkspace.EMPTY,
            currentRootTypeIds = emptySet(),
            isFinal = isFinal,
            inputDocumentSnapshots = emptyList(),
        )
    }

    private fun generatedSource(
        content: String,
        emissionMode: ArtifactEmissionMode,
        qualifiedName: String = "example.BookDraft",
    ): GeneratedArtifact {
        return GeneratedArtifact.source(
            kind = ArtifactKind.JAVA_SOURCE,
            qualifiedName = qualifiedName,
            content = content,
            aggregationMode = ArtifactAggregationMode.AGGREGATING,
            emissionMode = emissionMode,
            originatingSymbols = setOf(LsiSymbolId.type("example.Book")),
        )
    }

    private fun GeneratedArtifact.withSourceProjection(
        path: String,
        kind: LsiSourceKind,
    ): GeneratedArtifact {
        val source = LsiSource.of(path, LsiLanguage.JAVA, kind)
        return copy(
            originatingSources = setOf(source),
            dependencySources = setOf(source),
        )
    }

    private abstract class EmptyStateFeature :
        CompilerFeature<EmptyCompilerFeatureState, EmptyCompilerFeatureState> {

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }
    }

    private class HeaderSeedFeature(
        private val alphaId: LsiSymbolId,
        private val betaId: LsiSymbolId,
    ) : EmptyStateFeature() {

        override val key = KEY

        override fun requestTypeSeeds(context: CompilerTypeSeedContext): Collection<LsiTypeSeed> {
            return listOf(
                LsiTypeSeed(betaId, LsiTypeSeedMode.HEADER),
                LsiTypeSeed(alphaId, LsiTypeSeedMode.HEADER),
            )
        }

        companion object {
            val KEY = compilerFeatureKey<
                HeaderSeedFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class FullSeedFeature(
        private val typeId: LsiSymbolId,
    ) : EmptyStateFeature() {

        override val key = KEY

        override fun requestTypeSeeds(context: CompilerTypeSeedContext): Collection<LsiTypeSeed> {
            return listOf(LsiTypeSeed(typeId, LsiTypeSeedMode.FULL_DECLARATION))
        }

        companion object {
            val KEY = compilerFeatureKey<
                FullSeedFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class SeedOnlyFeature(
        private val invocations: FeatureInvocations,
    ) : EmptyStateFeature() {

        override val key = KEY

        override fun requestTypeSeeds(context: CompilerTypeSeedContext): Collection<LsiTypeSeed> {
            assertEquals(0, context.round.number)
            assertTrue(context.session.rounds.isEmpty())
            return listOf(
                LsiTypeSeed(LsiSymbolId.type("example.Payload"), LsiTypeSeedMode.FULL_DECLARATION),
            )
        }

        override fun collect(
            context: CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            invocations.collects++
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            invocations.precompiles++
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }

        override fun render(
            context: CompilerRenderContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeatureRenderResult {
            invocations.renders++
            return CompilerFeatureRenderResult()
        }

        companion object {
            val KEY = compilerFeatureKey<
                SeedOnlyFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class ResourceReaderFeature :
        CompilerFeature<EmptyCompilerFeatureState, TextState> {

        override val key = KEY

        override val metadata = CompilerFeatureMetadata(
            inputResourcePaths = setOf("META-INF/jimmer/entities"),
        )

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, TextState>,
        ): CompilerFeaturePrecompileResult<TextState> {
            return CompilerFeaturePrecompileResult(
                TextState(context.round.inputResources.getValue("META-INF/jimmer/entities")),
            )
        }

        companion object {
            val KEY = compilerFeatureKey<ResourceReaderFeature, EmptyCompilerFeatureState, TextState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private abstract class RecordingFeature(
        private val executions: MutableList<FeatureExecution>,
    ) : CompilerFeature<EmptyCompilerFeatureState, TextState> {

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, TextState>,
        ): CompilerFeaturePrecompileResult<TextState> {
            return CompilerFeaturePrecompileResult(TextState(context.round.number.toString()))
        }

        override fun render(
            context: CompilerRenderContext<EmptyCompilerFeatureState, TextState>,
        ): CompilerFeatureRenderResult {
            executions += FeatureExecution(
                key = key,
                roundNumber = context.round.number,
                completedRounds = context.session.rounds.size,
                dependencyKeys = context.dependencyStates.keys,
            )
            return CompilerFeatureRenderResult()
        }
    }

    private class ImmutableRecordingFeature(
        executions: MutableList<FeatureExecution>,
    ) : RecordingFeature(executions) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                ImmutableRecordingFeature,
                EmptyCompilerFeatureState,
                TextState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class ClientRecordingFeature(
        executions: MutableList<FeatureExecution>,
    ) : RecordingFeature(executions) {

        override val key = KEY

        override val dependencies = setOf(ImmutableRecordingFeature.KEY)

        companion object {
            val KEY = compilerFeatureKey<
                ClientRecordingFeature,
                EmptyCompilerFeatureState,
                TextState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class ConvergingFeature(
        private val invocations: FeatureInvocations,
    ) : CompilerFeature<EmptyCompilerFeatureState, NumericState> {

        override val key = KEY

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, NumericState>,
        ): CompilerFeaturePrecompileResult<NumericState> {
            invocations.precompiles++
            val previous = context.previousState?.value ?: -1
            return CompilerFeaturePrecompileResult(NumericState(min(previous + 1, 2)))
        }

        companion object {
            val KEY = compilerFeatureKey<ConvergingFeature, EmptyCompilerFeatureState, NumericState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class AllPhasesFeature(
        private val invocations: FeatureInvocations,
    ) : CompilerFeature<TextState, TextState> {

        override val key = KEY

        override fun collect(context: CompilerCollectContext): CompilerFeatureCollection<TextState> {
            val value = min(invocations.collects++, 2).toString()
            return CompilerFeatureCollection(TextState(value))
        }

        override fun precompile(
            context: CompilerPrecompileContext<TextState, TextState>,
        ): CompilerFeaturePrecompileResult<TextState> {
            return CompilerFeaturePrecompileResult(context.collection.state)
        }

        override fun render(
            context: CompilerRenderContext<TextState, TextState>,
        ): CompilerFeatureRenderResult {
            invocations.renders++
            return CompilerFeatureRenderResult(
                artifacts = listOf(
                    GeneratedArtifact.create(
                        kind = ArtifactKind.RESOURCE,
                        path = "META-INF/jimmer/all-phases",
                        content = context.state.fingerprint,
                        aggregationMode = ArtifactAggregationMode.AGGREGATING,
                    ),
                ),
            )
        }

        companion object {
            val KEY = compilerFeatureKey<AllPhasesFeature, TextState, TextState>(TextState("empty"))
        }
    }

    private abstract class ArtifactFeature(
        private val artifactsByRound: (Int) -> List<GeneratedArtifact>,
        requiresSourceQuiescence: Boolean = false,
    ) : EmptyStateFeature() {

        override val metadata = CompilerFeatureMetadata(
            requiresSourceQuiescence = requiresSourceQuiescence,
        )

        override fun render(
            context: CompilerRenderContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeatureRenderResult {
            return CompilerFeatureRenderResult(artifacts = artifactsByRound(context.round.number))
        }
    }

    private class ClientArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                ClientArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class ImmutableArtifactFeature(
        artifactsByRound: (Int) -> List<GeneratedArtifact>,
    ) : ArtifactFeature(artifactsByRound) {

        constructor(artifacts: List<GeneratedArtifact>) : this({ artifacts })

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                ImmutableArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class DtoArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }, requiresSourceQuiescence = true) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                DtoArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class ModuleArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                ModuleArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class QuiescentModuleArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }, requiresSourceQuiescence = true) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                QuiescentModuleArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class FirstArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                FirstArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class SecondArtifactFeature(
        artifacts: List<GeneratedArtifact>,
    ) : ArtifactFeature({ artifacts }) {

        override val key = KEY

        companion object {
            val KEY = compilerFeatureKey<
                SecondArtifactFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class UnstableFeature : CompilerFeature<EmptyCompilerFeatureState, NumericState> {

        override val key = KEY

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, NumericState>,
        ): CompilerFeaturePrecompileResult<NumericState> {
            val previous = context.previousState?.value ?: 0
            return CompilerFeaturePrecompileResult(NumericState(previous + 1))
        }

        companion object {
            val KEY = compilerFeatureKey<UnstableFeature, EmptyCompilerFeatureState, NumericState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private data class FeatureExecution(
        val key: CompilerFeatureKey<*, *>,
        val roundNumber: Int,
        val completedRounds: Int,
        val dependencyKeys: Set<CompilerFeatureKey<*, *>>,
    )

    private data class FeatureInvocations(
        var collects: Int = 0,
        var precompiles: Int = 0,
        var renders: Int = 0,
    )

    private data class TextState(
        override val fingerprint: String,
    ) : CompilerFeatureState

    private data class NumericState(
        val value: Int,
    ) : CompilerFeatureState {
        override val fingerprint: String = value.toString()
    }
}
