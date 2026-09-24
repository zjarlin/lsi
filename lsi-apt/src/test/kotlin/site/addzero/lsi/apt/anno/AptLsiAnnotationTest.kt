package site.addzero.lsi.apt.anno

import java.net.URI
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import site.addzero.lsi.anno.LsiAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AptLsiAnnotationTest {
    @Test
    fun `retains every nested annotation and scalar array element including empty arrays`() {
        var captured: LsiAnnotation? = null
        val processor = object : AbstractProcessor() {
            override fun getSupportedAnnotationTypes() = setOf("*")
            override fun getSupportedSourceVersion() = SourceVersion.latestSupported()
            override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
                if (!roundEnv.processingOver()) {
                    val type = processingEnv.elementUtils.getTypeElement("demo.Model")
                    captured = AptLsiAnnotation(type.annotationMirrors.single())
                }
                return false
            }
        }
        val text = """
            package demo;
            @interface Item { String name(); String[] values(); }
            @interface Fixture { Item[] nested(); String[] names(); int[] numbers(); String[] empty(); Item single(); }
            @Fixture(
                nested = {@Item(name = "first", values = {"a", "b"}), @Item(name = "second", values = {"c", "d"})},
                names = {"left", "right"}, numbers = {1, 2}, empty = {},
                single = @Item(name = "single", values = {"x", "y"})
            )
            interface Model {}
        """.trimIndent()
        val source = object : SimpleJavaFileObject(URI.create("string:///demo/Model.java"), JavaFileObject.Kind.SOURCE) {
            override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = text
        }
        val compiler = ToolProvider.getSystemJavaCompiler()
        compiler.getStandardFileManager(null, null, null).use { manager ->
            val task = compiler.getTask(null, manager, null, listOf("-proc:only"), null, listOf(source))
            task.setProcessors(listOf(processor))
            assertTrue(task.call())
        }
        val annotation = requireNotNull(captured)
        val nested = assertIs<List<*>>(annotation.getAttribute("nested")).map { assertIs<LsiAnnotation>(it) }
        assertEquals(listOf("first", "second"), nested.map { it.getAttribute("name") })
        assertEquals(listOf("a", "b"), nested[0].getAttribute("values"))
        assertEquals(listOf("c", "d"), nested[1].getAttribute("values"))
        assertEquals(listOf("left", "right"), annotation.getAttribute("names"))
        assertEquals(listOf("1", "2"), annotation.getAttribute("numbers"))
        assertEquals(emptyList<Any>(), annotation.getAttribute("empty"))
        assertEquals(listOf("x", "y"), assertIs<LsiAnnotation>(annotation.getAttribute("single")).getAttribute("values"))
    }
}
