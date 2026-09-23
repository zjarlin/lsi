package site.addzero.lsi.ksp.field

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.processing.Resolver
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import java.io.ByteArrayOutputStream
import java.io.File
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import site.addzero.lsi.ksp.clazz.KspLsiClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class KspComputedPropertyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `distinguishes simple and complex getters from stored and abstract properties`() {
        assertProperties(fromClasspath = false)
    }

    @Test
    fun `retains computed metadata for inherited getters from compiled dependencies`() {
        assertProperties(fromClasspath = true)
    }

    private fun assertProperties(fromClasspath: Boolean) {
        val observed = mutableMapOf<String, Boolean>()
        val base = SourceFile.kotlin("Base.kt", """
            package demo
            interface Base {
                val firstName: String
                val lastName: String
                val simple: String get() = firstName + " " + lastName
                val complex: String?
                    get() {
                        val parts = listOf(firstName, lastName).filter { it.isNotBlank() }
                        return if (parts.isEmpty()) null else parts.joinToString(" ").uppercase()
                    }
            }
        """.trimIndent())
        val models = SourceFile.kotlin("Models.kt", """
            package demo
            interface Author : Base {
                val id: Long
                val local: Boolean get() = firstName.isNotBlank()
            }
            class Stored(val constructorValue: String) {
                var stored: String = "value"
                var normalized: String = "initial"
                    get() = field.trim()
                val calculated: Int get() = stored.length
            }
        """.trimIndent())
        val dependencies = if (fromClasspath) {
            listOf(compile("dependency", listOf(base)).outputDirectory)
        } else {
            emptyList()
        }
        val sources = if (fromClasspath) listOf(models) else listOf(base, models)
        val provider = SymbolProcessorProvider {
            object : SymbolProcessor {
                override fun process(resolver: Resolver): List<KSAnnotated> {
                    for (name in listOf("Author", "Stored")) {
                        val declaration = requireNotNull(resolver.getClassDeclarationByName(resolver.getKSNameFromString("demo.$name")))
                        val fields = KspLsiClass(resolver, declaration).fields
                        fields.forEach { observed["$name.${it.name}"] = it.isComputed }
                    }
                    return emptyList()
                }
            }
        }
        compile("consumer", sources, dependencies, provider)
        assertEquals(
            mapOf(
                "Author.id" to false,
                "Author.firstName" to false,
                "Author.lastName" to false,
                "Author.simple" to true,
                "Author.complex" to true,
                "Author.local" to true,
                "Stored.constructorValue" to false,
                "Stored.stored" to false,
                "Stored.normalized" to false,
                "Stored.calculated" to true,
            ),
            observed,
        )
    }

    private fun compile(
        name: String,
        input: List<SourceFile>,
        dependencies: List<File> = emptyList(),
        provider: SymbolProcessorProvider? = null,
    ): JvmCompilationResult {
        val messages = ByteArrayOutputStream()
        val compilation = KotlinCompilation().apply {
            workingDir = temporaryFolder.newFolder(name)
            sources = input
            classpaths = dependencies
            inheritClassPath = true
            jvmTarget = "17"
            languageVersion = "2.1"
            apiVersion = "2.1"
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            verbose = false
            messageOutputStream = messages
            if (provider != null) {
                useKsp2()
                symbolProcessorProviders = mutableListOf(provider)
            }
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, messages.toString())
        return result
    }
}
