package site.addzero.lsi.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompilerFeatureGraphTest {

    @Test
    fun `metadata validates apt names`() {
        val metadata = CompilerFeatureMetadata(
            aptAnnotationTypes = setOf("org.babyfish.jimmer.Immutable"),
            supportedOptions = setOf("jimmer.source.includes"),
        )

        assertEquals(setOf("org.babyfish.jimmer.Immutable"), metadata.aptAnnotationTypes)
        assertEquals(setOf("jimmer.source.includes"), metadata.supportedOptions)
        listOf("", " Immutable", "Immutable", "org..Immutable", "org.example.Invalid-Name")
            .forEach { annotationType ->
                assertFailsWith<IllegalArgumentException> {
                    CompilerFeatureMetadata(aptAnnotationTypes = setOf(annotationType))
                }
            }
        listOf("", " jimmer.option", "jimmer..option", "jimmer.option=value")
            .forEach { optionName ->
                assertFailsWith<IllegalArgumentException> {
                    CompilerFeatureMetadata(supportedOptions = setOf(optionName))
                }
            }
    }

    @Test
    fun `metadata validates stable input resource paths`() {
        val metadata = CompilerFeatureMetadata(
            inputResourcePaths = sortedSetOf(
                "META-INF/jimmer/entities",
                "META-INF/jimmer/immutables",
            ),
        )

        assertEquals(
            sortedSetOf("META-INF/jimmer/entities", "META-INF/jimmer/immutables"),
            metadata.inputResourcePaths,
        )
        assertFailsWith<IllegalArgumentException> {
            CompilerFeatureMetadata(inputResourcePaths = setOf("/absolute"))
        }
    }

    @Test
    fun `依赖图按确定顺序排列`() {
        val sorted = CompilerFeatureGraph.sort(
            listOf(ClientFeature(), ErrorFeature(), ImmutableFeature(), DtoFeature()),
        )

        assertEquals(
            listOf(ErrorFeature.Key, ImmutableFeature.Key, DtoFeature.Key, ClientFeature.Key),
            sorted.map(CompilerFeature<*, *>::key),
        )
    }

    @Test
    fun `重复功能类型直接失败`() {
        val exception = assertFailsWith<DuplicateCompilerFeatureException> {
            CompilerFeatureGraph.sort(listOf(ImmutableFeature(), ImmutableFeature()))
        }

        assertEquals(ImmutableFeature.Key, exception.featureKey)
    }

    @Test
    fun `缺失类型化依赖直接失败`() {
        val exception = assertFailsWith<MissingCompilerFeatureDependencyException> {
            CompilerFeatureGraph.sort(listOf(ClientFeature()))
        }

        assertEquals(ClientFeature.Key, exception.featureKey)
        assertEquals(DtoFeature.Key, exception.dependencyKey)
    }

    @Test
    fun `依赖环直接失败并给出闭环路径`() {
        val exception = assertFailsWith<CyclicCompilerFeatureDependencyException> {
            CompilerFeatureGraph.sort(
                listOf(CyclicClientFeature(), CyclicDtoFeature(), CyclicImmutableFeature()),
            )
        }

        assertEquals(
            listOf(
                CyclicClientFeature.Key,
                CyclicDtoFeature.Key,
                CyclicImmutableFeature.Key,
                CyclicClientFeature.Key,
            ),
            exception.cycle,
        )
    }

    private abstract class StatelessFeature :
        CompilerFeature<EmptyCompilerFeatureState, EmptyCompilerFeatureState> {

        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }
    }

    private class ClientFeature : StatelessFeature() {
        override val key = Key
        override val dependencies = setOf(DtoFeature.Key, ErrorFeature.Key)

        companion object {
            val Key = compilerFeatureKey<ClientFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class DtoFeature : StatelessFeature() {
        override val key = Key
        override val dependencies = setOf(ImmutableFeature.Key)

        companion object {
            val Key = compilerFeatureKey<DtoFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class ErrorFeature : StatelessFeature() {
        override val key = Key

        companion object {
            val Key = compilerFeatureKey<ErrorFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class ImmutableFeature : StatelessFeature() {
        override val key = Key

        companion object {
            val Key = compilerFeatureKey<ImmutableFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class CyclicClientFeature : StatelessFeature() {
        override val key = Key
        override val dependencies = setOf(CyclicDtoFeature.Key)

        companion object {
            val Key = compilerFeatureKey<CyclicClientFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class CyclicDtoFeature : StatelessFeature() {
        override val key = Key
        override val dependencies = setOf(CyclicImmutableFeature.Key)

        companion object {
            val Key = compilerFeatureKey<CyclicDtoFeature, EmptyCompilerFeatureState, EmptyCompilerFeatureState>(
                EmptyCompilerFeatureState,
            )
        }
    }

    private class CyclicImmutableFeature : StatelessFeature() {
        override val key = Key
        override val dependencies = setOf(CyclicClientFeature.Key)

        companion object {
            val Key = compilerFeatureKey<
                CyclicImmutableFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState
            >(EmptyCompilerFeatureState)
        }
    }
}
