package site.addzero.lsi.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompilerFeatureGraphTest {

    @Test
    fun `descriptor validates apt metadata names`() {
        val descriptor = CompilerFeatureDescriptor(
            id = "immutable",
            aptAnnotationTypes = setOf("org.babyfish.jimmer.Immutable"),
            supportedOptions = setOf("jimmer.source.includes"),
        )

        assertEquals(setOf("org.babyfish.jimmer.Immutable"), descriptor.aptAnnotationTypes)
        assertEquals(setOf("jimmer.source.includes"), descriptor.supportedOptions)
        listOf("", " Immutable", "Immutable", "org..Immutable", "org.example.Invalid-Name")
            .forEach { annotationType ->
                assertFailsWith<IllegalArgumentException> {
                    CompilerFeatureDescriptor(
                        id = "invalid",
                        aptAnnotationTypes = setOf(annotationType),
                    )
                }
            }
        listOf("", " jimmer.option", "jimmer..option", "jimmer.option=value")
            .forEach { optionName ->
                assertFailsWith<IllegalArgumentException> {
                    CompilerFeatureDescriptor(
                        id = "invalid",
                        supportedOptions = setOf(optionName),
                    )
                }
            }
    }

    @Test
    fun `descriptor validates stable input resource paths`() {
        val descriptor = CompilerFeatureDescriptor(
            id = "module",
            inputResourcePaths = sortedSetOf(
                "META-INF/jimmer/entities",
                "META-INF/jimmer/immutables",
            ),
        )

        assertEquals(
            sortedSetOf("META-INF/jimmer/entities", "META-INF/jimmer/immutables"),
            descriptor.inputResourcePaths,
        )
        assertFailsWith<IllegalArgumentException> {
            CompilerFeatureDescriptor("invalid", inputResourcePaths = setOf("/absolute"))
        }
    }

    @Test
    fun `依赖图按确定顺序排列`() {
        val client = feature("client", "dto", "error")
        val error = feature("error")
        val immutable = feature("immutable")
        val dto = feature("dto", "immutable")

        val sorted = CompilerFeatureGraph.sort(listOf(client, error, immutable, dto))

        assertEquals(
            listOf("error", "immutable", "dto", "client"),
            sorted.map { provider -> provider.descriptor.id }
        )
    }

    @Test
    fun `重复功能标识直接失败`() {
        val exception = assertFailsWith<DuplicateCompilerFeatureException> {
            CompilerFeatureGraph.sort(listOf(feature("immutable"), feature("immutable")))
        }

        assertEquals("immutable", exception.featureId)
    }

    @Test
    fun `缺失依赖直接失败`() {
        val exception = assertFailsWith<MissingCompilerFeatureDependencyException> {
            CompilerFeatureGraph.sort(listOf(feature("client", "dto")))
        }

        assertEquals("client", exception.featureId)
        assertEquals("dto", exception.dependencyId)
    }

    @Test
    fun `依赖环直接失败并给出闭环路径`() {
        val exception = assertFailsWith<CyclicCompilerFeatureDependencyException> {
            CompilerFeatureGraph.sort(
                listOf(
                    feature("client", "dto"),
                    feature("dto", "immutable"),
                    feature("immutable", "client")
                )
            )
        }

        assertEquals(listOf("client", "dto", "immutable", "client"), exception.cycle)
    }

    private fun feature(id: String, vararg dependencies: String): CompilerFeatureProvider =
        object : CompilerFeatureProvider {
            override val descriptor = CompilerFeatureDescriptor(id, dependencies.toSet())
        }
}
