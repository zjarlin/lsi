package site.addzero.lsi.poet.javapoet

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind

class LsiJavaPoetTopLevelMemberTest {

    @Test
    fun `rejects Kotlin top-level member references`() {
        val type = LsiTypeDeclaration(
            name = "References",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "render",
                    body = LsiCodeBlock.build {
                        statement {
                            topLevelMember(
                                packageName = "org.babyfish.jimmer.kt",
                                simpleName = "by",
                                extension = false,
                            )
                            text("()")
                        }
                    },
                )
            ),
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "References",
                members = listOf(type),
            ),
            typeNames = emptyList(),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val exception = assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(artifact)
        }

        assertContains(
            exception.message.orEmpty(),
            "Kotlin top-level member reference: org.babyfish.jimmer.kt.by",
        )
    }
}
