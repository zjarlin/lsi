package site.addzero.lsi.type

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.core.LsiSymbolId

class LsiTypeExtensionsTest {

    @Test
    fun `补充注解覆盖同类型原注解并保留其余注解`() {
        val retained = LsiAnnotation(LsiSymbolId.type("example.Retained"))
        val inherited = LsiAnnotation(LsiSymbolId.type("example.Override"))
        val declared = LsiAnnotation(LsiSymbolId.type("example.Override"))
        val type = LsiDeclaredType(
            declarationId = LsiSymbolId.type("example.Value"),
            annotations = listOf(inherited, retained),
        )

        val resolved = type.withAdditionalAnnotations(listOf(declared))

        assertEquals(listOf(declared, retained), resolved.annotations)
    }

    @Test
    fun `没有补充注解时保留原类型实例`() {
        val type = LsiPrimitiveType(LsiPrimitiveKind.INT)

        assertSame(type, type.withAdditionalAnnotations(emptyList()))
    }
}
