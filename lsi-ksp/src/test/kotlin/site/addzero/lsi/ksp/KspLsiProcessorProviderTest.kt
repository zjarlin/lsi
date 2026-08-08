package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Proxy
import kotlin.KotlinVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import site.addzero.lsi.compiler.CompilerCollectContext
import site.addzero.lsi.compiler.CompilerFeatureCollection
import site.addzero.lsi.compiler.CompilerFeatureDescriptor
import site.addzero.lsi.compiler.CompilerFeatureProvider
import site.addzero.lsi.compiler.CompilerInputDocumentKind
import site.addzero.lsi.compiler.CompilerInputDocumentProvider
import site.addzero.lsi.compiler.CompilerInputDocumentSnapshot
import site.addzero.lsi.compiler.CompilerSourceSet
import site.addzero.lsi.compiler.CompilerWiring
import site.addzero.lsi.model.LsiFrontendOptions

class KspLsiProcessorProviderTest {

    @Test
    fun `delegates process and finish with injected providers and wiring`() {
        val featureProvider = CapturingFeatureProvider()
        val inputDocumentProvider = CapturingInputDocumentProvider()
        val wiring = CapturingWiring(inputDocumentProvider)
        val options = mapOf("test.option" to "present")
        val processor = KspLsiProcessorProvider(
            wiring = wiring,
            providers = listOf(featureProvider),
            sessionId = "ksp-provider-test",
        ).create(
            SymbolProcessorEnvironment(
                options,
                KotlinVersion.CURRENT,
                EmptyCodeGenerator(),
                ThrowingLogger(),
            )
        )

        val deferred = processor.process(emptyResolver())
        processor.finish()

        assertTrue(deferred.isEmpty())
        assertEquals(listOf(options), wiring.frontendOptionsCalls)
        assertEquals(listOf(setOf(INPUT_DOCUMENT_KIND) to options), wiring.inputDocumentProviderCalls)
        assertEquals(listOf(CompilerSourceSet.MAIN), inputDocumentProvider.scannedSourceSets)
        assertEquals(listOf(CompilerSourceSet.MAIN), inputDocumentProvider.discoveryChecks)
        assertEquals(
            listOf(0 to false, 1 to true),
            featureProvider.observedRounds.distinct(),
        )
    }

    private class CapturingFeatureProvider : CompilerFeatureProvider {
        override val descriptor = CompilerFeatureDescriptor(
            id = "ksp-provider-test",
            inputDocumentKinds = setOf(INPUT_DOCUMENT_KIND),
        )

        val observedRounds = mutableListOf<Pair<Int, Boolean>>()

        override fun collect(context: CompilerCollectContext): CompilerFeatureCollection {
            observedRounds += context.round.number to context.round.isFinal
            return CompilerFeatureCollection()
        }
    }

    private class CapturingWiring(
        private val inputDocuments: CompilerInputDocumentProvider,
    ) : CompilerWiring {
        val frontendOptionsCalls = mutableListOf<Map<String, String>>()

        val inputDocumentProviderCalls =
            mutableListOf<Pair<Set<CompilerInputDocumentKind>, Map<String, String>>>()

        override fun frontendOptions(options: Map<String, String>): LsiFrontendOptions {
            frontendOptionsCalls += options
            return LsiFrontendOptions(keepJavaBooleanGetterIsPrefix = true)
        }

        override fun inputDocumentProvider(
            kinds: Set<CompilerInputDocumentKind>,
            options: Map<String, String>,
        ): CompilerInputDocumentProvider {
            inputDocumentProviderCalls += kinds to options
            return inputDocuments
        }
    }

    private class CapturingInputDocumentProvider : CompilerInputDocumentProvider {
        val scannedSourceSets = mutableListOf<CompilerSourceSet>()

        val discoveryChecks = mutableListOf<CompilerSourceSet>()

        override fun scan(
            startPaths: Collection<File>,
            sourceSet: CompilerSourceSet,
        ): List<CompilerInputDocumentSnapshot> {
            assertTrue(startPaths.isEmpty())
            scannedSourceSets += sourceSet
            return emptyList()
        }

        override fun isFileSystemDiscoveryComplete(sourceSet: CompilerSourceSet): Boolean {
            discoveryChecks += sourceSet
            return true
        }
    }

    private class EmptyCodeGenerator : CodeGenerator {
        override val generatedFile: Collection<File> = emptyList()

        override fun createNewFile(
            dependencies: Dependencies,
            packageName: String,
            fileName: String,
            extensionName: String,
        ): OutputStream = error("This test does not generate package-based files")

        override fun createNewFileByPath(
            dependencies: Dependencies,
            path: String,
            extensionName: String,
        ): OutputStream = error("This test does not generate path-based files")

        override fun associate(
            sources: List<KSFile>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit

        override fun associateByPath(
            sources: List<KSFile>,
            path: String,
            extensionName: String,
        ) = Unit

        override fun associateWithClasses(
            classes: List<KSClassDeclaration>,
            packageName: String,
            fileName: String,
            extensionName: String,
        ) = Unit
    }

    private class ThrowingLogger : KSPLogger {
        override fun logging(message: String, symbol: KSNode?) = Unit

        override fun info(message: String, symbol: KSNode?) = Unit

        override fun warn(message: String, symbol: KSNode?) = Unit

        override fun error(message: String, symbol: KSNode?) {
            throw AssertionError(message)
        }

        override fun exception(e: Throwable) {
            throw e
        }
    }

    private fun emptyResolver(): Resolver {
        return Proxy.newProxyInstance(
            Resolver::class.java.classLoader,
            arrayOf(Resolver::class.java),
        ) { instance, method, arguments ->
            when (method.name) {
                "equals" -> instance === arguments?.firstOrNull()
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "EmptyResolver"
                "getAllFiles", "getNewFiles" -> emptySequence<KSFile>()
                else -> error("Unexpected resolver call: ${method.name}")
            }
        } as Resolver
    }

    companion object {
        private val INPUT_DOCUMENT_KIND = CompilerInputDocumentKind("test")
    }
}
