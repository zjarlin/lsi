package site.addzero.lsi.poet

import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.codegen.LsiSourceArtifact

/** 将纯 LSI 源码模型适配到具体 Poet 实现。 */
fun interface LsiPoetRenderer {
    fun render(artifact: LsiSourceArtifact): GeneratedArtifact
}
