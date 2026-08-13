package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId

class LsiFrontendOptionsTest {

    @Test
    fun `注解和枚举声明始终要求完整冻结`() {
        val options = LsiFrontendOptions()

        assertTrue(type(LsiTypeDeclarationKind.ANNOTATION).requiresFullExternalDeclaration(options))
        assertTrue(type(LsiTypeDeclarationKind.ENUM).requiresFullExternalDeclaration(options))
        assertFalse(type(LsiTypeDeclarationKind.CLASS).requiresFullExternalDeclaration(options))
    }

    @Test
    fun `配置的标记注解要求完整冻结`() {
        val markerId = LsiSymbolId.type("example.FullDeclaration")
        val options = LsiFrontendOptions(
            fullExternalDeclarationAnnotationTypeIds = setOf(markerId),
        )
        val declaration = type(
            kind = LsiTypeDeclarationKind.INTERFACE,
            annotations = listOf(LsiAnnotation(markerId)),
        )

        assertTrue(declaration.requiresFullExternalDeclaration(options))
    }

    private fun type(
        kind: LsiTypeDeclarationKind,
        annotations: List<LsiAnnotation> = emptyList(),
    ): LsiClass {
        return LsiClass(
            id = LsiSymbolId.type("example.Type"),
            name = "Type",
            qualifiedName = "example.Type",
            kind = kind,
            annotations = annotations,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
    }
}
