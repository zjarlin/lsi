package site.addzero.lsi.ksp.anno

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import java.io.ByteArrayOutputStream
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import site.addzero.lsi.anno.LsiAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class KspLsiAnnotationTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `preserves the same nested collections as APT`() {
        var processed = false
        val provider = SymbolProcessorProvider {
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    val type = resolver.getClassDeclarationByName(resolver.getKSNameFromString("demo.Model"))
                    if (type != null) {
                        assertValues(KspLsiAnnotation(type.annotations.single()))
                        processed = true
                    }
                    return emptyList()
                }
            }
        }
        val messages = ByteArrayOutputStream()
        val result = KotlinCompilation().apply {
            workingDir = folder.newFolder()
            sources = listOf(SourceFile.kotlin("Model.kt", """
                package demo
                annotation class Item(val name: String, val values: Array<String>)
                annotation class Fixture(val nested: Array<Item>, val names: Array<String>, val numbers: IntArray, val empty: Array<String>, val single: Item)
                @Fixture(
                    nested = [Item("first", ["a", "b"]), Item("second", ["c", "d"])],
                    names = ["left", "right"], numbers = [1, 2], empty = [], single = Item("single", ["x", "y"])
                )
                interface Model
            """.trimIndent()))
            inheritClassPath = true
            jvmTarget = "17"
            languageVersion = "2.1"
            apiVersion = "2.1"
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            verbose = false
            messageOutputStream = messages
            useKsp2()
            symbolProcessorProviders = mutableListOf(provider)
        }.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, messages.toString())
        assertTrue(processed)
    }

    private fun assertValues(annotation: LsiAnnotation) {
        val nested = assertIs<List<*>>(annotation.getAttribute("nested")).map { assertIs<LsiAnnotation>(it) }
        assertEquals(listOf("first", "second"), nested.map { it.getAttribute("name") })
        assertEquals(listOf("a", "b"), nested[0].getAttribute("values"))
        assertEquals(listOf("c", "d"), nested[1].getAttribute("values"))
        assertEquals(listOf("left", "right"), annotation.getAttribute("names"))
        assertEquals(listOf("1", "2"), assertIs<List<*>>(annotation.getAttribute("numbers")).map { it.toString() })
        assertEquals(emptyList<Any>(), annotation.getAttribute("empty"))
        assertEquals(listOf("x", "y"), assertIs<LsiAnnotation>(annotation.getAttribute("single")).getAttribute("values"))
    }
}
