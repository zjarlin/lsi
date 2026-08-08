package site.addzero.lsi.compiler

import site.addzero.lsi.model.LsiFrontendOptions

/**
 * 将领域编译约定注入平台中立的 LSI 编译流程。
 */
interface CompilerWiring {

    fun frontendOptions(options: Map<String, String>): LsiFrontendOptions = LsiFrontendOptions()

    fun inputDocumentProvider(
        kinds: Set<CompilerInputDocumentKind>,
        options: Map<String, String>,
    ): CompilerInputDocumentProvider = CompilerInputDocumentProvider.EMPTY

    companion object {

        val DEFAULT: CompilerWiring = object : CompilerWiring {}
    }
}
