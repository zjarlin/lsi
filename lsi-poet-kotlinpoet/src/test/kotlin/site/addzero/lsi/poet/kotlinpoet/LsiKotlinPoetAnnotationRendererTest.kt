package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiSourceAnnotationArgument
import site.addzero.lsi.anno.LsiAnnotationArgumentLayout
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.anno.LsiClassLiteralStyle
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.anno.sourceLsiAnnotation

class LsiKotlinPoetAnnotationRendererTest {

    @Test
    fun `renders one annotation and an ordered annotation list`() {
        val firstId = LsiSymbolId.type("sample.First")
        val secondId = LsiSymbolId.type("sample.Second")
        val typeNames = listOf(
            LsiClass(firstId, "sample", listOf("First")),
            LsiClass(secondId, "sample", listOf("Second")),
        )
        val first = sourceLsiAnnotation(
            type = firstId,
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(
                    LsiAnnotationValue.StringValue("first"),
                )
            ),
            argumentLayout = LsiAnnotationArgumentLayout.SINGLE_LINE,
        )
        val second = sourceLsiAnnotation(
            type = secondId,
            arguments = listOf(
                LsiSourceAnnotationArgument.Named(
                    name = "count",
                    value = LsiAnnotationValue.IntValue(2),
                )
            ),
        )
        val renderer = LsiKotlinPoetRenderer()

        val renderedFirst = renderer.renderAnnotation(first, typeNames)
        val renderedAll = renderer.renderAnnotations(listOf(first, second), typeNames)

        assertEquals(ClassName("sample", "First"), renderedFirst.typeName)
        assertEquals("@sample.First(\"first\")", renderedFirst.toString())
        assertEquals(
            listOf("@sample.First(\"first\")", "@sample.Second(count = 2)"),
            renderedAll.map(Any::toString),
        )
    }

    @Test
    fun `renders qualified java boxed primitive class literal without an import`() {
        val annotationId = LsiSymbolId.type("sample.Boxed")
        val annotation = sourceLsiAnnotation(
            type = annotationId,
            arguments = listOf(
                LsiSourceAnnotationArgument.Named(
                    name = "type",
                    value = LsiAnnotationValue.ClassValue(
                        type = LsiPrimitiveType(LsiPrimitiveKind.INT, boxed = true),
                        sourceStyle = LsiClassLiteralStyle.JAVA_BOXED_PRIMITIVE_QUALIFIED,
                    ),
                ),
            ),
        )

        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiClass(annotationId, "sample", listOf("Boxed"))),
        )

        assertEquals(
            "@sample.Boxed(type = java.lang.Integer::class)",
            rendered.toString(),
        )
    }
}
