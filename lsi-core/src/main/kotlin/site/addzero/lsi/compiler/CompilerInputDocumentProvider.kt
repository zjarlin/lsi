package site.addzero.lsi.compiler

import java.io.File

/**
 * 为平台驱动提供不可变输入文档快照。
 */
interface CompilerInputDocumentProvider {

    fun scan(
        startPaths: Collection<File>,
        sourceSet: CompilerSourceSet,
    ): List<CompilerInputDocumentSnapshot> = emptyList()

    fun isFileSystemDiscoveryComplete(sourceSet: CompilerSourceSet): Boolean = true

    companion object {

        val EMPTY: CompilerInputDocumentProvider = object : CompilerInputDocumentProvider {}
    }
}
