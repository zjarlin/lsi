package site.addzero.lsi.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

class CompilerClasspathTypeProbeTest {

    @Test
    fun `metadata accepts only stable type ids`() {
        val typeId = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
        val metadata = CompilerFeatureMetadata(
            classpathTypeIds = setOf(typeId),
        )

        assertEquals(setOf(typeId), metadata.classpathTypeIds)
        assertFailsWith<IllegalArgumentException> {
            CompilerFeatureMetadata(
                classpathTypeIds = setOf(LsiSymbolId.property(typeId, "factory")),
            )
        }
    }

    @Test
    fun `round exposes only frozen declared availability`() {
        val availableTypeId = LsiSymbolId.type("tools.jackson.databind.ObjectMapper")
        val unavailableTypeId = LsiSymbolId.type("com.fasterxml.jackson.databind.ObjectMapper")
        val rounds = mutableListOf<CompilerRound>()
        val feature = ClasspathProbeFeature(
            declaredTypeIds = setOf(availableTypeId, unavailableTypeId),
            rounds = rounds,
        )
        val session = CompilerSession("classpath-probe", listOf(feature))

        session.execute(round(0, setOf(availableTypeId)))
        session.execute(round(1, setOf(availableTypeId), isFinal = true))

        assertEquals(
            listOf(
                setOf(availableTypeId),
                setOf(availableTypeId),
                setOf(availableTypeId),
            ),
            rounds.map(CompilerRound::availableTypeIds),
        )
    }

    @Test
    fun `session rejects availability not declared by any feature`() {
        val feature = EmptyFeature()

        val exception = assertFailsWith<IllegalArgumentException> {
            CompilerSession("classpath-probe", listOf(feature)).execute(
                round(0, setOf(LsiSymbolId.type("tools.jackson.databind.ObjectMapper"))),
            )
        }

        assertEquals(
            "Available compiler types must be declared by a compiler feature",
            exception.message,
        )
    }

    private fun round(
        number: Int,
        availableTypeIds: Set<LsiSymbolId>,
        isFinal: Boolean = false,
    ): CompilerRound {
        return CompilerRound(
            number = number,
            workspace = LsiWorkspace.EMPTY,
            currentRootTypeIds = emptySet(),
            isFinal = isFinal,
            availableTypeIds = availableTypeIds,
            inputDocumentSnapshots = emptyList(),
        )
    }

    private class ClasspathProbeFeature(
        declaredTypeIds: Set<LsiSymbolId>,
        private val rounds: MutableList<CompilerRound>,
    ) : CompilerFeature<EmptyCompilerFeatureState, EmptyCompilerFeatureState> {

        override val key = KEY

        override val metadata = CompilerFeatureMetadata(classpathTypeIds = declaredTypeIds)

        override fun collect(
            context: CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            rounds += context.round
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }

        companion object {
            val KEY = compilerFeatureKey<
                ClasspathProbeFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class EmptyFeature : CompilerFeature<EmptyCompilerFeatureState, EmptyCompilerFeatureState> {

        override val key = KEY

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }

        companion object {
            val KEY = compilerFeatureKey<
                EmptyFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }
}
