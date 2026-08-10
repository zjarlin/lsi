package site.addzero.lsi.model

import site.addzero.lsi.file.LsiImport
/**
 * 描述可嵌入既有源码文件的代码块及其显式导入依赖。
 */
data class LsiCodeFragment(
    val codeBlock: LsiCodeBlock,
    val imports: List<LsiImport> = emptyList(),
) {
    init {
        require(imports.distinct() == imports) {
            "LSI code fragment cannot contain duplicate explicit imports"
        }
    }
}
