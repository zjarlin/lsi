package site.addzero.lsi.poet.kotlinpoet

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationUseSiteTarget
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.type.LsiTypeParameter
import site.addzero.lsi.type.LsiTypeParameterRef
import site.addzero.lsi.type.LsiUnresolvedType
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiAccessor
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentLayout
import site.addzero.lsi.model.LsiAnnotationArrayStyle
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.LsiFileNameStyle
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeReferenceStyle
import site.addzero.lsi.model.sourceLsiAnnotation

class LsiKotlinPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    private val typeNames = listOf(
        LsiClass(LsiSymbolId.type("java.lang.String"), "java.lang", listOf("String")),
        LsiClass(LsiSymbolId.type("java.io.IOException"), "java.io", listOf("IOException")),
        LsiClass(LsiSymbolId.type("kotlin.Suppress"), "kotlin", listOf("Suppress")),
        LsiClass(LsiSymbolId.type("demo.annotation.Container"), "demo.annotation", listOf("Container")),
        LsiClass(LsiSymbolId.type("demo.annotation.Nested"), "demo.annotation", listOf("Nested")),
        LsiClass(LsiSymbolId.type("demo.annotation.Ordered"), "demo.annotation", listOf("Ordered")),
        LsiClass(LsiSymbolId.type("demo.annotation.TypeMarker"), "demo.annotation", listOf("TypeMarker")),
        LsiClass(LsiSymbolId.type("demo.external.External"), "demo.external", listOf("External")),
        LsiClass(
            LsiSymbolId.type("demo.generated.QueryExtensions"),
            "demo.generated",
            listOf("QueryExtensions"),
        ),
        LsiClass(LsiSymbolId.type("demo.Order"), "demo", listOf("Order")),
        LsiClass(LsiSymbolId.type("demo.Source"), "demo", listOf("Source")),
    )

    @Test
    fun `renders an embeddable Kotlin code block exactly`() {
        val codeBlock = LsiCodeBlock.build {
            statement {
                name("consume")
                text("(")
                type(stringType)
                text(", ")
                string("value")
                text(")")
            }
        }

        val rendered = LsiKotlinPoetRenderer().renderCodeBlock(codeBlock, typeNames)

        assertEquals(
            CodeBlock::class.java,
            LsiKotlinPoetRenderer::class.java
                .getDeclaredMethod("renderCodeBlock", LsiCodeBlock::class.java, List::class.java)
                .returnType,
        )
        assertEquals("consume(kotlin.String, \"value\")\n", rendered.toString())
    }

    @Test
    fun `appends control flow into an existing initializer state`() {
        val enumType = LsiDeclaredType(LsiSymbolId.type("demo.Order"))
        val codeBlock = LsiCodeBlock.build {
            text("{")
            indent {
                line()
                beginControlFlow {
                    text("when (it as ")
                    type(enumType)
                    text(")")
                }
                statement {
                    type(enumType)
                    text(".FIRST -> 1")
                }
                statement {
                    type(enumType)
                    text(".SECOND -> 2")
                }
                endControlFlow()
            }
            text("}")
        }
        val initializer = CodeBlock.builder().apply {
            add("Wrapper(")
            indent()
            add("\n")
            LsiKotlinPoetRenderer().appendCodeBlock(this, codeBlock, typeNames)
            unindent()
            add("\n)")
        }.build()
        val rendered = TypeSpec.classBuilder("Container")
            .addProperty(
                PropertySpec.builder("mapping", ANY)
                    .initializer(initializer)
                    .build()
            )
            .build()
            .toString()

        assertEquals(
            """
                public class Container {
                  public val mapping: kotlin.Any = Wrapper(
                    {
                      when (it as demo.Order) {
                        demo.Order.FIRST -> 1
                        demo.Order.SECOND -> 2
                      }
                    }
                  )
                }

            """.trimIndent(),
            rendered,
        )
    }

    @Test
    fun `renders an embeddable Kotlin type structure exactly`() {
        val type = LsiClass(
            name = "Marker",
            kind = LsiTypeDeclarationKind.INTERFACE,
            modifiers = setOf(LsiModifier.PUBLIC),
        )

        val rendered = LsiKotlinPoetRenderer().renderType(type, emptyList())

        assertEquals(
            TypeSpec::class.java,
            LsiKotlinPoetRenderer::class.java
                .getDeclaredMethod("renderType", LsiClass::class.java, List::class.java)
                .returnType,
        )
        assertEquals("public interface Marker\n", rendered.toString())
    }

    @Test
    fun `renders a Kotlin class through a GeneratedArtifact boundary`() {
        val type = LsiClass(
            name = "Greeting",
            kind = LsiTypeDeclarationKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            primaryConstructor = LsiConstructor(
                parameters = listOf(LsiParameter("name", stringType)),
            ),
            members = listOf(
                LsiProperty(
                    name = "name",
                    type = stringType,
                    mutable = false,
                    modifiers = setOf(LsiModifier.PRIVATE),
                    initializer = LsiCodeBlock.build { name("name") },
                ),
                LsiFunction(
                    name = "message",
                    modifiers = setOf(LsiModifier.PUBLIC),
                    returnType = stringType,
                    body = LsiCodeBlock.build {
                        text("return ")
                        string("Hello ")
                        text(" + ")
                        name("name")
                        line()
                    },
                ),
            ),
        )
        val artifact = artifact(type, "Greeting")

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiKotlinPoetRenderer::class.java
            .getDeclaredMethod("render", LsiSourceArtifact::class.java).returnType)
        assertPublicApiDoesNotExposeOtherPoet(LsiKotlinPoetRenderer::class.java)
        assertEquals("demo/generated/Greeting.kt", generated.path)
        assertEquals(
            """
                package demo.generated

                import kotlin.String

                public class Greeting(
                    private val name: String,
                ) {
                    public fun message(): String {
                        return "Hello " + name
                    }
                }
            """.trimIndent(),
            generated.content.trimIndent(),
        )
    }

    @Test
    fun `rejects Java fields and unresolved types`() {
        val fieldType = LsiClass(
            name = "FieldHolder",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiField("name", stringType),
            ),
        )
        assertFailsWith<IllegalStateException> {
            LsiKotlinPoetRenderer().render(artifact(fieldType, "FieldHolder"))
        }

        val unresolvedType = LsiClass(
            name = "Broken",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "value",
                    returnType = LsiUnresolvedType("Missing"),
                )
            ),
        )
        val exception = assertFailsWith<IllegalStateException> {
            LsiKotlinPoetRenderer().render(artifact(unresolvedType, "Broken"))
        }
        assertTrue(exception.message.orEmpty().contains("unresolved"))
    }

    @Test
    fun `renders positional file suppression without a member name`() {
        val type = LsiClass(
            name = "Suppressed",
            kind = LsiTypeDeclarationKind.CLASS,
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "Suppressed",
                annotations = listOf(
                    sourceLsiAnnotation(
                        type = LsiSymbolId.type("kotlin.Suppress"),
                        arguments = listOf(
                            LsiSourceAnnotationArgument.Positional(
                                LsiAnnotationValue.StringValue("warnings")
                            )
                        ),
                        useSiteTarget = LsiAnnotationUseSiteTarget.FILE,
                    )
                ),
                members = listOf(type),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertContains(generated.content, "@file:Suppress(\"warnings\")")
        assertFalse("value =" in generated.content)
    }

    @Test
    fun `renders nested source annotation and core type annotation`() {
        val nested = sourceLsiAnnotation(
            type = LsiSymbolId.type("demo.annotation.Nested"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("inside"))
            ),
        )
        val annotatedType = stringType.copy(
            annotations = listOf(LsiAnnotation(LsiSymbolId.type("demo.annotation.TypeMarker")))
        )
        val type = LsiClass(
            name = "NestedAnnotation",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "nested",
                            value = LsiAnnotationValue.NestedAnnotationValue(nested),
                        )
                    ),
                )
            ),
            members = listOf(LsiFunction(name = "value", returnType = annotatedType)),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact(type, "NestedAnnotation"))

        assertContains(generated.content, "@Container(nested = Nested(\"inside\"))")
        assertFalse("@Nested(\"inside\")" in generated.content)
        assertContains(generated.content, "fun `value`(): @TypeMarker String")
    }

    @Test
    fun `renders constructor throws vararg override and structural control flow`() {
        val exceptionType = LsiDeclaredType(LsiSymbolId.type("java.io.IOException"))
        val type = LsiClass(
            name = "Service",
            kind = LsiTypeDeclarationKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            primaryConstructor = LsiConstructor(
                parameters = listOf(
                    LsiParameter(
                        name = "values",
                        type = stringType,
                        modifiers = setOf(LsiModifier.VARARG),
                    )
                ),
                thrownTypes = listOf(exceptionType),
            ),
            members = listOf(
                LsiFunction(
                    name = "consume",
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.UNIT),
                    body = LsiCodeBlock.build {
                        beginControlFlow { text("if (values.isEmpty())") }
                        statement { text("return") }
                        endControlFlow()
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Service")).content

        assertTrue("@Throws(IOException::class)" in content, content)
        assertTrue("vararg values: String" in content, content)
        assertTrue("public override fun consume()" in content, content)
        assertTrue("if (values.isEmpty()) {\n            return\n        }" in content, content)
    }

    @Test
    fun `renders structural return as a Kotlin expression body`() {
        val type = LsiClass(
            name = "Returns",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiCodeBlock.build {
                        returnValue { string("ok") }
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Returns")).content

        assertContains(content, "public fun message(): String = \"ok\"")
    }

    @Test
    fun `renders a returned braced expression as a Kotlin expression body`() {
        val type = LsiClass(
            name = "ReturnsBlock",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiCodeBlock.build {
                        returnBracedExpression(
                            prefix = { text("call()") },
                            body = { statement { string("ok") } },
                        )
                    },
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "ReturnsBlock")).content

        assertContains(content, "public fun message(): String = call() {")
    }

    @Test
    fun `renders escaped declaration names and explicit imports exactly`() {
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "FetcherDsl",
                imports = listOf(LsiImport("demo.child", "by")),
                members = listOf(
                    LsiClass(
                        name = "Order-ItemFetcherDsl",
                        kind = LsiTypeDeclarationKind.CLASS,
                        nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                        members = listOf(
                            LsiProperty(
                                name = "emptyOrder-ItemFetcher",
                                type = stringType,
                                mutable = false,
                                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                                initializer = LsiCodeBlock.build { string("empty") },
                            ),
                            LsiFunction(
                                name = "children*",
                                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                            ),
                        ),
                    )
                ),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(artifact).content

        assertEquals(
            """
                package demo.generated

                import demo.child.`by`
                import kotlin.String

                public class `Order-ItemFetcherDsl` {
                    public val `emptyOrder-ItemFetcher`: String = "empty"

                    public fun `children*`() {
                    }
                }
            """.trimIndent(),
            content.trimIndent(),
        )
    }

    @Test
    fun `renders escaped parameters named setters and annotation array factory calls`() {
        val type = LsiClass(
            name = "EscapedParameters",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "literalValues",
                            value = LsiAnnotationValue.ArrayValue(
                                listOf(LsiAnnotationValue.StringValue("literal"))
                            ),
                        ),
                        LsiSourceAnnotationArgument.Named(
                            name = "factoryValues",
                            value = LsiAnnotationValue.ArrayValue(
                                elements = listOf(LsiAnnotationValue.StringValue("factory")),
                                sourceStyle = LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF,
                            ),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiProperty(
                    name = "display-name",
                    type = stringType,
                    mutable = true,
                    nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                    initializer = LsiCodeBlock.build { string("") },
                    setter = LsiAccessor(
                        setterParameterName = "display-name",
                        setterParameterNameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                        body = LsiCodeBlock.build {
                            statement {
                                text("println(")
                                name("display-name")
                                text(")")
                            }
                        },
                    ),
                ),
                LsiFunction(
                    name = "update",
                    parameters = listOf(
                        LsiParameter(
                            name = "display-name",
                            type = stringType,
                            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                        )
                    ),
                ),
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "EscapedParameters")).content

        assertContains(content, "literalValues = [\"literal\"]")
        assertContains(content, "factoryValues = arrayOf(\"factory\")")
        assertContains(content, "set(`display-name`)")
        assertContains(content, "fun update(`display-name`: String)")
    }

    @Test
    fun `preserves raw source stem in generated artifact path`() {
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "order-item.partFetcher",
                fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
                members = listOf(LsiClass("OrderFetcher", LsiTypeDeclarationKind.CLASS)),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Order")),
        )

        val generated = LsiKotlinPoetRenderer().render(artifact)

        assertEquals("demo/generated/order-item.partFetcher.kt", generated.path)
        assertContains(generated.content, "public class OrderFetcher")
    }

    @Test
    fun `renders inline reified type parameters exactly`() {
        val parameterId = LsiSymbolId.typeParameter(
            LsiSymbolId.type("demo.generated.QueryExtensions"),
            "S",
        )
        val function = LsiFunction(
            name = "query",
            modifiers = setOf(LsiModifier.INLINE),
            typeParameters = listOf(
                LsiTypeParameter(
                    id = parameterId,
                    name = "S",
                    upperBounds = listOf(stringType),
                )
            ),
            reifiedTypeParameterIds = setOf(parameterId),
            returnType = LsiTypeParameterRef(parameterId),
            body = LsiCodeBlock.build {
                returnValue { text("error(\"unused\")") }
            },
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "QueryExtensions",
                members = listOf(function),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(artifact).content

        assertContains(content, "public inline fun <reified S : String> query(): S = error(\"unused\")")
    }

    @Test
    fun `renders fully qualified Kotlin type references without imports`() {
        val externalType = LsiDeclaredType(LsiSymbolId.type("demo.external.External"))
        val function = LsiFunction(
            name = "typeName",
            body = LsiCodeBlock.build {
                statement {
                    type(externalType, LsiTypeReferenceStyle.FULLY_QUALIFIED)
                    text("::class")
                }
            },
        )
        val file = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = "Qualified",
                members = listOf(function),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiKotlinPoetRenderer().render(file).content

        assertContains(content, "demo.`external`.External::class")
        assertTrue("import demo.external.External" !in content)
    }

    @Test
    fun `renders explicit expression and single line layout`() {
        val type = LsiClass(
            name = "Layout",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Ordered"),
                    arguments = listOf("dummy", "id", "name").map { value ->
                        LsiSourceAnnotationArgument.Positional(
                            LsiAnnotationValue.StringValue(value)
                        )
                    },
                    argumentLayout = LsiAnnotationArgumentLayout.SINGLE_LINE,
                )
            ),
            members = listOf(
                LsiProperty(
                    name = "created",
                    type = stringType,
                    mutable = false,
                    initializer = LsiCodeBlock.build {
                        preserveExplicitIndentation()
                        text("Factory")
                        line()
                        indent { text(".create()") }
                    },
                ),
                LsiProperty(
                    name = "missing",
                    type = stringType,
                    mutable = false,
                    getter = LsiAccessor(
                        body = LsiCodeBlock.build { text("throw IllegalStateException()") },
                        bodyStyle = LsiBodyStyle.EXPRESSION,
                    ),
                ),
                LsiFunction(
                    name = "pick",
                    returnType = stringType,
                    body = LsiCodeBlock.build { text("when (value) { else -> value }") },
                    bodyStyle = LsiBodyStyle.EXPRESSION,
                ),
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "Layout")).content

        assertContains(content, "@Ordered(\"dummy\", \"id\", \"name\")")
        assertContains(content, "public val created: String = Factory\n        .create()")
        assertContains(content, "get() = throw IllegalStateException()")
        assertContains(content, "public fun pick(): String = when (value) { else -> value }")
    }

    private fun artifact(type: LsiClass, fileName: String): LsiSourceArtifact {
        return LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo.generated",
                fileName = fileName,
                members = listOf(type),
            ),
            typeNames = typeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
    }

    @Test
    fun `renders typed values in multiline annotation arrays`() {
        val type = LsiClass(
            name = "MultilineAnnotation",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "value",
                            value = LsiAnnotationValue.ArrayValue(
                                elements = listOf(
                                    LsiAnnotationValue.StringValue("dto-name")
                                ),
                                sourceStyle = LsiAnnotationArrayStyle.MULTI_LINE_LITERAL,
                            ),
                        )
                    ),
                    argumentLayout = LsiAnnotationArgumentLayout.MULTI_LINE,
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "MultilineAnnotation")).content

        assertContains(
            content,
            """
                @Container(
                    `value` = [
                        "dto-name"
                    ]
                )
            """.trimIndent(),
        )
    }

    @Test
    fun `renders line separated annotation array elements without moving brackets`() {
        val annotation = sourceLsiAnnotation(
            type = LsiSymbolId.type("demo.annotation.Container"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(
                    LsiAnnotationValue.ArrayValue(
                        elements = listOf(
                            LsiAnnotationValue.StringValue("first"),
                            LsiAnnotationValue.StringValue("second"),
                        ),
                        sourceStyle = LsiAnnotationArrayStyle.LINE_SEPARATED_LITERAL,
                    )
                )
            ),
        )

        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiClass(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals("@demo.`annotation`.Container([\"first\",\n\"second\"])", rendered.toString())
    }

    @Test
    fun `renders compact multiline annotation array elements inside brackets`() {
        val annotation = sourceLsiAnnotation(
            type = LsiSymbolId.type("demo.annotation.Container"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(
                    LsiAnnotationValue.ArrayValue(
                        elements = listOf(
                            LsiAnnotationValue.StringValue("first"),
                            LsiAnnotationValue.StringValue("second"),
                        ),
                        sourceStyle = LsiAnnotationArrayStyle.COMPACT_MULTI_LINE_LITERAL,
                    )
                )
            ),
        )

        val rendered = LsiKotlinPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiClass(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals(
            "@demo.`annotation`.Container([\n  \"first\",\n  \"second\"\n])",
            rendered.toString(),
        )
    }

    @Test
    fun `escapes keyword annotation argument names at every annotation boundary`() {
        val keywordArgument = LsiAnnotationArgument(
            value = LsiAnnotationValue.StringValue("core"),
            origin = LsiAnnotationArgumentOrigin.EXPLICIT,
        )
        val nested = sourceLsiAnnotation(
            type = LsiSymbolId.type("demo.annotation.Nested"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Named(
                    name = "when",
                    value = LsiAnnotationValue.StringValue("nested"),
                )
            ),
        )
        val type = LsiClass(
            name = "KeywordAnnotation",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "when",
                            value = LsiAnnotationValue.StringValue("source"),
                        ),
                        LsiSourceAnnotationArgument.Named(
                            name = "nested",
                            value = LsiAnnotationValue.NestedAnnotationValue(nested),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiProperty(
                    name = "value",
                    type = stringType.copy(
                        annotations = listOf(
                            LsiAnnotation(
                                type = LsiSymbolId.type("demo.annotation.Container"),
                                arguments = mapOf("when" to keywordArgument),
                            )
                        ),
                    ),
                    mutable = false,
                )
            ),
        )

        val content = LsiKotlinPoetRenderer().render(artifact(type, "KeywordAnnotation")).content

        assertContains(content, "`when` = \"source\"")
        assertContains(content, "Nested(`when` = \"nested\")")
        assertContains(content, "Container(`when` = \"core\") String")
    }

    private fun assertPublicApiDoesNotExposeOtherPoet(type: Class<*>) {
        val methodTypes = type.declaredMethods
            .filter { method -> java.lang.reflect.Modifier.isPublic(method.modifiers) }
            .flatMap { method -> listOf(method.returnType) + method.parameterTypes }
        val constructorTypes = type.declaredConstructors
            .filter { constructor -> java.lang.reflect.Modifier.isPublic(constructor.modifiers) }
            .flatMap { constructor -> constructor.parameterTypes.toList() }
        val fieldTypes = type.declaredFields
            .filter { field -> java.lang.reflect.Modifier.isPublic(field.modifiers) }
            .map { field -> field.type }
        val exposedTypes = methodTypes + constructorTypes + fieldTypes

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.javapoet.") })
    }
}
