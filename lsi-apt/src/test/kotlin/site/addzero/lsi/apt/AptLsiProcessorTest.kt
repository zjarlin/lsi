package site.addzero.lsi.apt

import java.nio.charset.StandardCharsets
import javax.lang.model.SourceVersion
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerFailureTranslation
import site.addzero.lsi.compiler.CompilerFailureTranslator
import site.addzero.lsi.compiler.CompilerCollectContext
import site.addzero.lsi.compiler.CompilerFeature
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeatureMetadata
import site.addzero.lsi.compiler.CompilerFeaturePrecompileResult
import site.addzero.lsi.compiler.CompilerPrecompileContext
import site.addzero.lsi.compiler.EmptyCompilerFeatureState
import site.addzero.lsi.compiler.compilerFeatureKey

class AptLsiProcessorTest {

    @Test
    fun `processor metadata is the union of feature metadata`() {
        val processor = AptLsiProcessor(
            features = listOf(SecondMetadataFeature(), FirstMetadataFeature()),
        )

        assertEquals(setOf("demo.First", "demo.Second"), processor.supportedAnnotationTypes)
        assertEquals(setOf("demo.first.option", "demo.second.option"), processor.supportedOptions)
        assertEquals(SourceVersion.latest(), processor.supportedSourceVersion)
    }

    @Test
    fun `translated feature failure is reported on its annotation target`() {
        val diagnostics = compile(FailingFeature(annotationTypeName = TRIGGER_ANNOTATION))

        val matchingDiagnostics = diagnostics.diagnostics.filter { diagnostic ->
            diagnostic.kind == Diagnostic.Kind.ERROR && diagnostic.getMessage(null) == FAILURE_MESSAGE
        }
        assertEquals(1, matchingDiagnostics.size)
        val diagnostic = matchingDiagnostics.single()
        assertTrue(diagnostic.source?.name.orEmpty().endsWith("Sample.java"))
        assertTrue(diagnostic.lineNumber > 0)
    }

    @Test
    fun `translated feature failure is rethrown when its target is absent`() {
        val failure = kotlin.runCatching {
            compile(FailingFeature(annotationTypeName = "demo.Missing"))
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(generateSequence(failure, Throwable::cause).any { cause -> cause === FEATURE_FAILURE })
    }

    private fun compile(feature: CompilerFeature<*, *>): DiagnosticCollector<JavaFileObject> {
        val projectDir = createTempDirectory(prefix = "apt-jimmer-processor").toFile()
        val sourceDir = projectDir.resolve("src").apply { mkdirs() }
        val classesDir = projectDir.resolve("classes").apply { mkdirs() }
        val generatedDir = projectDir.resolve("generated").apply { mkdirs() }
        val sourceFile = sourceDir.resolve("Sample.java").apply { writeText(SOURCE) }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: error("APT integration tests require a JDK compiler")
        val success = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8).use { fileManager ->
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(classesDir))
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, listOf(generatedDir))
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                listOf("-proc:only", "-classpath", System.getProperty("java.class.path")),
                null,
                fileManager.getJavaFileObjects(sourceFile),
            )
            task.setProcessors(listOf(AptLsiProcessor(features = listOf(feature))))
            task.call()
        }
        assertFalse(success)
        return diagnostics
    }

    private class FirstMetadataFeature : EmptyFeature() {
        override val key = Key

        override val metadata = CompilerFeatureMetadata(
            aptAnnotationTypes = setOf("demo.First"),
            supportedOptions = setOf("demo.first.option"),
        )

        companion object {
            val Key = compilerFeatureKey<
                FirstMetadataFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class SecondMetadataFeature : EmptyFeature() {
        override val key = Key

        override val metadata = CompilerFeatureMetadata(
            aptAnnotationTypes = setOf("demo.Second"),
            supportedOptions = setOf("demo.second.option"),
        )

        companion object {
            val Key = compilerFeatureKey<
                SecondMetadataFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private class FailingFeature(
        private val annotationTypeName: String,
    ) : EmptyFeature(), CompilerFailureTranslator {
        override val key = Key

        override val metadata = CompilerFeatureMetadata(
            aptAnnotationTypes = setOf(TRIGGER_ANNOTATION),
        )

        override fun collect(
            context: CompilerCollectContext,
        ): CompilerFeatureCollection<EmptyCompilerFeatureState> {
            if (!context.round.isFinal) {
                throw FEATURE_FAILURE
            }
            return CompilerFeatureCollection(EmptyCompilerFeatureState)
        }

        override fun translateFailure(failure: Throwable): CompilerFailureTranslation? {
            if (failure !== FEATURE_FAILURE) {
                return null
            }
            return CompilerFailureTranslation(
                message = FAILURE_MESSAGE,
                annotationTypeName = annotationTypeName,
            )
        }

        companion object {
            val Key = compilerFeatureKey<
                FailingFeature,
                EmptyCompilerFeatureState,
                EmptyCompilerFeatureState,
            >(EmptyCompilerFeatureState)
        }
    }

    private abstract class EmptyFeature : CompilerFeature<
        EmptyCompilerFeatureState,
        EmptyCompilerFeatureState,
    > {
        override fun precompile(
            context: CompilerPrecompileContext<EmptyCompilerFeatureState, EmptyCompilerFeatureState>,
        ): CompilerFeaturePrecompileResult<EmptyCompilerFeatureState> {
            return CompilerFeaturePrecompileResult(EmptyCompilerFeatureState)
        }
    }

    private companion object {
        const val TRIGGER_ANNOTATION = "demo.Trigger"

        const val FAILURE_MESSAGE = "Feature compilation failed"

        val FEATURE_FAILURE = IllegalStateException(FAILURE_MESSAGE)

        val SOURCE = """
            package demo;

            @interface Trigger {}

            @Trigger
            class Sample {}
        """.trimIndent()
    }
}
