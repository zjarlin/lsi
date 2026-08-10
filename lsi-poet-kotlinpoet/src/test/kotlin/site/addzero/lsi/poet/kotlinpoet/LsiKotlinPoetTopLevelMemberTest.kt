package site.addzero.lsi.poet.kotlinpoet

import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind

class LsiKotlinPoetTopLevelMemberTest {

    @Test
    fun `renders exact imports for keyword and extension members`() {
        val type = LsiClass(
            name = "References",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiMethod(
                    name = "render",
                    body = LsiCodeBlock.build {
                        statement {
                            topLevelMember(
                                packageName = "org.babyfish.jimmer.kt",
                                simpleName = "newFetcher",
                                extension = false,
                            )
                            text("(")
                            name("type")
                            text(").")
                            topLevelMember(
                                packageName = "demo.model",
                                simpleName = "by",
                                extension = true,
                            )
                            text("(")
                            name("block")
                            text(")")
                        }
                        statement {
                            name("source")
                            text(".")
                            topLevelMember(
                                packageName = "demo.extensions",
                                simpleName = "toView",
                                extension = true,
                            )
                            text("()")
                        }
                    },
                )
            ),
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "References",
                members = listOf(type),
            ),
            typeNames = emptyList(),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(artifact).content

        assertEquals(
            """
                package demo.generated

                import demo.extensions.toView
                import demo.model.`by`
                import org.babyfish.jimmer.kt.newFetcher

                public class References {
                    public fun render() {
                        newFetcher(type).`by`(block)
                        source.toView()
                    }
                }
            """.trimIndent(),
            content.trimIndent(),
        )
    }
}
