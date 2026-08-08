package site.addzero.lsi.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import site.addzero.lsi.compiler.CompilerFeatureProvider
import site.addzero.lsi.compiler.CompilerFeatureProviders
import site.addzero.lsi.compiler.CompilerWiring

/**
 * 将通用 LSI KSP 驱动适配为可复用的处理器入口。
 */
open class KspLsiProcessorProvider(
    private val wiring: CompilerWiring = CompilerWiring.DEFAULT,
    private val providers: Iterable<CompilerFeatureProvider> = CompilerFeatureProviders.load(),
    private val sessionId: String = "ksp",
) : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val driver = KspLsiCompilerDriver(
            environment = environment,
            providers = providers,
            wiring = wiring,
            sessionId = sessionId,
        )
        return object : SymbolProcessor {
            override fun process(resolver: Resolver): List<KSAnnotated> = driver.process(resolver)

            override fun finish() {
                driver.finish()
            }
        }
    }
}
