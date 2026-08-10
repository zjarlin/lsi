package site.addzero.lsi.poet.javapoet

import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.GeneratedArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiDeclaredType
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeParameter
import site.addzero.lsi.model.LsiTypeParameterRef
import site.addzero.lsi.model.LsiTypeArgument
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.model.LsiBodyStyle
import site.addzero.lsi.model.LsiSourceAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentLayout
import site.addzero.lsi.model.LsiAnnotationArrayStyle
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiCodeBlock
import site.addzero.lsi.model.LsiConstructor
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiFunction
import site.addzero.lsi.model.LsiImport
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.LsiModifier
import site.addzero.lsi.model.LsiNameStyle
import site.addzero.lsi.model.LsiParameter
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeName
import site.addzero.lsi.model.LsiTypeReferenceStyle
import site.addzero.lsi.model.LsiFile
import site.addzero.lsi.model.sourceLsiAnnotation

class LsiJavaPoetRendererTest {

    private val stringType = LsiDeclaredType(LsiSymbolId.type("java.lang.String"))

    @Test
    fun `renders an embeddable Java code block exactly`() {
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

        val rendered = LsiJavaPoetRenderer().renderCodeBlock(codeBlock, commonTypeNames)

        assertEquals(
            CodeBlock::class.java,
            LsiJavaPoetRenderer::class.java
                .getDeclaredMethod("renderCodeBlock", LsiCodeBlock::class.java, List::class.java)
                .returnType,
        )
        assertEquals("consume(java.lang.String, \"value\");\n", rendered.toString())
    }

    @Test
    fun `renders an embeddable Java type structure exactly`() {
        val type = LsiTypeDeclaration(
            name = "Marker",
            kind = LsiTypeDeclarationKind.INTERFACE,
            modifiers = setOf(LsiModifier.PUBLIC),
        )

        val rendered = LsiJavaPoetRenderer().renderType(type, emptyList())

        assertEquals(
            TypeSpec::class.java,
            LsiJavaPoetRenderer::class.java
                .getDeclaredMethod("renderType", LsiTypeDeclaration::class.java, List::class.java)
                .returnType,
        )
        assertEquals("public interface Marker {\n}\n", rendered.toString())
    }

    @Test
    fun `rejects package-relative type references without file context`() {
        val type = LsiTypeDeclaration(
            name = "Owner",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiField(
                    name = "nested",
                    type = LsiDeclaredType(LsiSymbolId.type("demo.generated.Owner.Nested")),
                    typeReferenceStyle = LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().renderType(
                type,
                listOf(
                    typeName(
                        LsiSymbolId.type("demo.generated.Owner.Nested"),
                        "demo.generated",
                        "Owner",
                        "Nested",
                    )
                ),
            )
        }

        assertContains(exception.message.orEmpty(), "requires file package context")
    }

    @Test
    fun `preserves package-relative nested type references in the default package`() {
        val type = LsiTypeDeclaration(
            name = "Owner",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiField(
                    name = "nested",
                    type = LsiDeclaredType(LsiSymbolId.type("Owner.Nested")),
                    typeReferenceStyle = LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
                LsiTypeDeclaration(
                    name = "Nested",
                    kind = LsiTypeDeclarationKind.CLASS,
                    modifiers = setOf(LsiModifier.STATIC),
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(
            artifact(type, "Owner", packageName = "")
        ).content

        assertContains(content, "Nested nested;")
    }

    @Test
    fun `renders a Java class through a GeneratedArtifact boundary`() {
        val type = LsiTypeDeclaration(
            name = "Greeting",
            kind = LsiTypeDeclarationKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            members = listOf(
                LsiField(
                    name = "name",
                    type = stringType,
                    modifiers = setOf(LsiModifier.PRIVATE, LsiModifier.FINAL),
                ),
                LsiConstructor(
                    modifiers = setOf(LsiModifier.PUBLIC),
                    parameters = listOf(LsiParameter("name", stringType)),
                    body = LsiCodeBlock.build {
                        text("this.")
                        name("name")
                        text(" = ")
                        name("name")
                        text(";")
                        line()
                    },
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
                        text(";")
                        line()
                    },
                ),
            ),
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "Greeting",
                members = listOf(type),
            ),
            typeNames = commonTypeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
            originatingSources = setOf(LsiSource.of("demo/Source.java", LsiLanguage.JAVA)),
        )

        val generated = LsiJavaPoetRenderer().render(artifact)

        assertEquals(GeneratedArtifact::class.java, LsiJavaPoetRenderer::class.java
            .getDeclaredMethod("render", LsiSourceArtifact::class.java).returnType)
        assertPublicApiDoesNotExposeOtherPoet(LsiJavaPoetRenderer::class.java)
        assertEquals("demo/generated/Greeting.java", generated.path)
        assertEquals(
            """
                package demo.generated;

                import java.lang.String;

                public class Greeting {
                    private final String name;

                    public Greeting(String name) {
                        this.name = name;
                    }

                    public String message() {
                        return "Hello " + name;
                    }
                }
            """.trimIndent(),
            generated.content.trimIndent(),
        )
    }

    @Test
    fun `rejects Kotlin properties and unresolved types`() {
        val property = LsiProperty(
            name = "name",
            type = stringType,
            mutable = false,
        )
        val propertyType = LsiTypeDeclaration(
            name = "PropertyHolder",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(property),
        )
        val propertyArtifact = artifact(propertyType, "PropertyHolder")
        assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(propertyArtifact)
        }

        val unresolvedType = LsiTypeDeclaration(
            name = "Broken",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "value",
                    returnType = site.addzero.lsi.model.LsiUnresolvedType("Missing"),
                )
            ),
        )
        val exception = assertFailsWith<IllegalStateException> {
            LsiJavaPoetRenderer().render(artifact(unresolvedType, "Broken"))
        }
        assertTrue(exception.message.orEmpty().contains("unresolved"))
    }

    @Test
    fun `renders a single Java positional argument as value`() {
        val type = LsiTypeDeclaration(
            name = "Annotated",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Label"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Positional(
                            LsiAnnotationValue.StringValue("book")
                        )
                    ),
                )
            ),
        )

        val generated = LsiJavaPoetRenderer().render(artifact(type, "Annotated"))

        assertContains(generated.content, "@Label(\"book\")")
    }

    @Test
    fun `rejects Java positional and named argument combinations`() {
        val type = LsiTypeDeclaration(
            name = "InvalidAnnotation",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Label"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Positional(
                            LsiAnnotationValue.StringValue("book")
                        ),
                        LsiSourceAnnotationArgument.Named(
                            name = "level",
                            value = LsiAnnotationValue.StringValue("warning"),
                        ),
                    ),
                )
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(type, "InvalidAnnotation"))
        }

        assertContains(exception.message.orEmpty(), "cannot combine positional and named")
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
        val type = LsiTypeDeclaration(
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

        val generated = LsiJavaPoetRenderer().render(artifact(type, "NestedAnnotation"))

        assertContains(generated.content, "nested = @Nested(\"inside\")")
        assertContains(generated.content, "@TypeMarker String value()")
    }

    @Test
    fun `renders constructor contracts override vararg and structural control flow`() {
        val ownerId = LsiSymbolId.type("demo.generated.Service")
        val parameterId = LsiSymbolId.typeParameter(ownerId, "T")
        val exceptionType = LsiDeclaredType(LsiSymbolId.type("java.io.IOException"))
        val type = LsiTypeDeclaration(
            name = "Service",
            kind = LsiTypeDeclarationKind.CLASS,
            modifiers = setOf(LsiModifier.PUBLIC),
            members = listOf(
                LsiConstructor(
                    modifiers = setOf(LsiModifier.PUBLIC),
                    typeParameters = listOf(LsiTypeParameter(parameterId, "T")),
                    parameters = listOf(
                        LsiParameter(
                            name = "values",
                            type = LsiTypeParameterRef(parameterId),
                            modifiers = setOf(LsiModifier.VARARG),
                        )
                    ),
                    thrownTypes = listOf(exceptionType),
                ),
                LsiFunction(
                    name = "consume",
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.OVERRIDE),
                    parameters = listOf(
                        LsiParameter(
                            name = "values",
                            type = stringType,
                            modifiers = setOf(LsiModifier.VARARG),
                        )
                    ),
                    returnType = LsiPrimitiveType(LsiPrimitiveKind.VOID),
                    body = LsiCodeBlock.build {
                        beginControlFlow {
                            text("if (")
                            name("values")
                            text(".length == 0)")
                        }
                        statement { text("return") }
                        endControlFlow()
                    },
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "Service")).content

        assertTrue("public <T> Service(T... values) throws IOException" in content)
        assertTrue("@Override\n    public void consume(String... values)" in content)
        assertTrue("if (values.length == 0) {\n            return;\n        }" in content)
    }

    @Test
    fun `renders structural return with Java termination`() {
        val type = LsiTypeDeclaration(
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

        val content = LsiJavaPoetRenderer().render(artifact(type, "Returns")).content

        assertContains(content, "return \"ok\";")
    }

    @Test
    fun `renders a returned braced expression with an inline suffix`() {
        val type = LsiTypeDeclaration(
            name = "ReturnsBlock",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(
                LsiFunction(
                    name = "message",
                    returnType = stringType,
                    body = LsiCodeBlock.build {
                        returnBracedExpression(
                            prefix = { text("call(") },
                            body = { statement { string("ok") } },
                            suffix = { text(")") },
                        )
                    },
                )
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "ReturnsBlock")).content

        assertContains(content, "return call( {\n            \"ok\";\n        });")
    }

    @Test
    fun `rejects Kotlin only declaration names and explicit imports`() {
        val escapedFunction = LsiFunction(
            name = "children*",
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val escapedException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(escapedFunction, "Escaped"))
        }
        assertContains(escapedException.message.orEmpty(), "escaped Kotlin function name")

        val escapedType = LsiTypeDeclaration(
            name = "Order-ItemFetcherDsl",
            kind = LsiTypeDeclarationKind.CLASS,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val escapedTypeException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(
                artifact(
                    LsiTypeDeclaration(
                        name = "Escaped",
                        kind = LsiTypeDeclarationKind.CLASS,
                        members = listOf(escapedType),
                    ),
                    "Escaped",
                )
            )
        }
        assertContains(escapedTypeException.message.orEmpty(), "escaped Kotlin type name")

        val escapedParameter = LsiFunction(
            name = "consume",
            parameters = listOf(
                LsiParameter(
                    name = "display-name",
                    type = stringType,
                    nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
                )
            ),
        )
        val escapedParameterException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(escapedParameter, "EscapedParameter"))
        }
        assertContains(escapedParameterException.message.orEmpty(), "escaped Kotlin parameter name")

        val importedArtifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "Imported",
                imports = listOf(LsiImport("demo.child", "by")),
                members = listOf(LsiTypeDeclaration("Imported", LsiTypeDeclarationKind.CLASS)),
            ),
            typeNames = emptyList(),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
        val importException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(importedArtifact)
        }
        assertContains(importException.message.orEmpty(), "explicit imports")
    }

    @Test
    fun `rejects annotation array factory call source style`() {
        val type = LsiTypeDeclaration(
            name = "FactoryArray",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.annotation.Container"),
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "groups",
                            value = LsiAnnotationValue.ArrayValue(
                                elements = emptyList(),
                                sourceStyle = LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF,
                            ),
                        )
                    ),
                )
            ),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(type, "FactoryArray"))
        }

        assertContains(exception.message.orEmpty(), "annotation array factory call")
    }

    @Test
    fun `renders typed values in multiline annotation arrays`() {
        val type = LsiTypeDeclaration(
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
                )
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(type, "MultilineAnnotation")).content

        assertContains(
            content,
            """
                @Container({
                            "dto-name"
                        })
            """.trimIndent(),
        )
    }

    @Test
    fun `renders line separated annotation array elements without moving braces`() {
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

        val rendered = LsiJavaPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals("@demo.annotation.Container({\"first\",\n    \"second\"})", rendered.toString())
    }

    @Test
    fun `renders compact multiline annotation array elements inside braces`() {
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

        val rendered = LsiJavaPoetRenderer().renderAnnotation(
            annotation,
            listOf(LsiTypeName(annotation.type, "demo.annotation", listOf("Container"))),
        )

        assertEquals(
            "@demo.annotation.Container({\n      \"first\",\n      \"second\"\n    })",
            rendered.toString(),
        )
    }

    @Test
    fun `rejects reified type parameters`() {
        val parameterId = LsiSymbolId.typeParameter(
            LsiSymbolId.type("demo.generated.Reified"),
            "S",
        )
        val function = LsiFunction(
            name = "query",
            modifiers = setOf(LsiModifier.INLINE),
            typeParameters = listOf(LsiTypeParameter(parameterId, "S")),
            reifiedTypeParameterIds = setOf(parameterId),
            returnType = LsiTypeParameterRef(parameterId),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(function, "Reified"))
        }

        assertContains(exception.message.orEmpty(), "reified type parameters")
    }

    @Test
    fun `renders fully qualified type references without symbol prefixes`() {
        val listType = LsiDeclaredType(
            declarationId = LsiSymbolId.type("java.util.List"),
            arguments = listOf(LsiTypeArgument.invariant(stringType)),
        )
        val function = LsiFunction(
            name = "cast",
            modifiers = setOf(LsiModifier.PUBLIC),
            parameters = listOf(LsiParameter("value", LsiDeclaredType(LsiSymbolId.type("java.lang.Object")))),
            returnType = listType,
            body = LsiCodeBlock.build {
                returnValue {
                    text("(")
                    type(listType, LsiTypeReferenceStyle.FULLY_QUALIFIED)
                    text(")value")
                }
            },
        )

        val content = LsiJavaPoetRenderer().render(artifact(function, "Qualified")).content

        assertContains(content, "return (java.util.List<java.lang.String>)value;")
        assertTrue("type:" !in content)
    }

    @Test
    fun `preserves same package outer qualification in a field type`() {
        val producerType = LsiDeclaredType(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer")
        )
        val draftType = LsiTypeDeclaration(
            name = "BasicBookDraft",
            kind = LsiTypeDeclarationKind.INTERFACE,
            members = listOf(
                LsiField(
                    name = "$",
                    type = producerType,
                    modifiers = setOf(
                        LsiModifier.PUBLIC,
                        LsiModifier.STATIC,
                        LsiModifier.FINAL,
                    ),
                    initializer = LsiCodeBlock.build {
                        type(producerType)
                        text(".INSTANCE")
                    },
                    typeReferenceStyle = LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
                ),
                LsiTypeDeclaration(
                    name = "Producer",
                    kind = LsiTypeDeclarationKind.CLASS,
                    modifiers = setOf(LsiModifier.PUBLIC, LsiModifier.STATIC),
                ),
            ),
        )

        val content = LsiJavaPoetRenderer().render(artifact(draftType, "BasicBookDraft")).content

        assertContains(content, "BasicBookDraft.Producer $ = Producer.INSTANCE;")
        assertTrue("import demo.generated.BasicBookDraft.Producer;" !in content)
        assertEquals(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer"),
            producerType.declarationId,
        )
    }

    @Test
    fun `renders exact uppercase package lowercase and deeply nested names`() {
        val lowercaseId = LsiSymbolId.type("UPPER.pkg.lowercase")
        val nestedId = LsiSymbolId.type("UPPER.pkg.outer.middle.inner")
        val annotationId = LsiSymbolId.type("UPPER.meta.marker")
        val enumId = LsiSymbolId.type("UPPER.values.outer.mode")
        val type = LsiTypeDeclaration(
            name = "ExactNames",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = annotationId,
                    arguments = listOf(
                        LsiSourceAnnotationArgument.Named(
                            name = "kind",
                            value = LsiAnnotationValue.EnumValue(enumId, "ON"),
                        ),
                        LsiSourceAnnotationArgument.Named(
                            name = "target",
                            value = LsiAnnotationValue.ClassValue(LsiDeclaredType(lowercaseId)),
                        ),
                    ),
                )
            ),
            members = listOf(
                LsiFunction(name = "lower", returnType = LsiDeclaredType(lowercaseId)),
                LsiFunction(name = "nested", returnType = LsiDeclaredType(nestedId)),
            ),
        )
        val artifact = LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo.generated",
                fileName = "ExactNames",
                members = listOf(type),
            ),
            typeNames = listOf(
                typeName(lowercaseId, "UPPER.pkg", "lowercase"),
                typeName(nestedId, "UPPER.pkg", "outer", "middle", "inner"),
                typeName(annotationId, "UPPER.meta", "marker"),
                typeName(enumId, "UPPER.values", "outer", "mode"),
            ),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )

        val content = LsiJavaPoetRenderer().render(artifact).content

        assertContains(content, "import UPPER.meta.marker;")
        assertContains(content, "import UPPER.pkg.lowercase;")
        assertContains(content, "import UPPER.values.outer;")
        assertContains(content, "kind = outer.mode.ON")
        assertContains(content, "target = lowercase.class")
        assertContains(content, "lowercase lower()")
        assertContains(content, "UPPER.pkg.outer.middle.inner nested()")
    }

    @Test
    fun `rejects a declared type without an exact source name`() {
        val type = LsiTypeDeclaration(
            name = "MissingName",
            kind = LsiTypeDeclarationKind.CLASS,
            members = listOf(LsiFunction(name = "value", returnType = stringType)),
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().renderType(type, emptyList())
        }

        assertContains(exception.message.orEmpty(), "requires exactly one source type name")
        assertContains(exception.message.orEmpty(), "java.lang.String")
    }

    @Test
    fun `rejects expression bodies at the Java adapter boundary`() {
        val function = LsiFunction(
            name = "message",
            returnType = stringType,
            body = LsiCodeBlock.build { string("ok") },
            bodyStyle = LsiBodyStyle.EXPRESSION,
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(function, "Expression"))
        }

        assertContains(exception.message.orEmpty(), "expression function body")
    }

    @Test
    fun `rejects Kotlin-only layout hints at the Java adapter boundary`() {
        val annotationType = LsiTypeDeclaration(
            name = "Annotated",
            kind = LsiTypeDeclarationKind.CLASS,
            annotations = listOf(
                sourceLsiAnnotation(
                    type = LsiSymbolId.type("demo.Ordered"),
                    argumentLayout = LsiAnnotationArgumentLayout.SINGLE_LINE,
                )
            ),
        )
        val annotationException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(annotationType, "Annotated"))
        }
        assertContains(annotationException.message.orEmpty(), "forced annotation layout")

        val explicitlyIndentedFunction = LsiFunction(
            name = "create",
            body = LsiCodeBlock.build {
                preserveExplicitIndentation()
                statement { text("Factory.create()") }
            },
        )
        val indentationException = assertFailsWith<IllegalArgumentException> {
            LsiJavaPoetRenderer().render(artifact(explicitlyIndentedFunction, "Indented"))
        }
        assertContains(indentationException.message.orEmpty(), "explicit Kotlin code indentation")
    }

    private fun artifact(
        member: LsiMember,
        fileName: String,
        packageName: String = "demo.generated",
    ): LsiSourceArtifact {
        val type = if (member is LsiTypeDeclaration) member else {
            LsiTypeDeclaration(fileName, LsiTypeDeclarationKind.CLASS, members = listOf(member))
        }
        return LsiSourceArtifact(
            file = LsiFile(
                language = LsiLanguage.JAVA,
                packageName = packageName,
                fileName = fileName,
                members = listOf(type),
            ),
            typeNames = commonTypeNames,
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(LsiSymbolId.type("demo.Source")),
        )
    }

    private fun typeName(
        typeId: LsiSymbolId,
        packageName: String,
        vararg simpleNames: String,
    ): LsiTypeName = LsiTypeName(typeId, packageName, simpleNames.toList())

    private val commonTypeNames = listOf(
        typeName(LsiSymbolId.type("Owner.Nested"), "", "Owner", "Nested"),
        typeName(LsiSymbolId.type("demo.Ordered"), "demo", "Ordered"),
        typeName(LsiSymbolId.type("demo.annotation.Container"), "demo.annotation", "Container"),
        typeName(LsiSymbolId.type("demo.annotation.Label"), "demo.annotation", "Label"),
        typeName(LsiSymbolId.type("demo.annotation.Nested"), "demo.annotation", "Nested"),
        typeName(LsiSymbolId.type("demo.annotation.TypeMarker"), "demo.annotation", "TypeMarker"),
        typeName(
            LsiSymbolId.type("demo.generated.BasicBookDraft.Producer"),
            "demo.generated",
            "BasicBookDraft",
            "Producer",
        ),
        typeName(
            LsiSymbolId.type("demo.generated.Owner.Nested"),
            "demo.generated",
            "Owner",
            "Nested",
        ),
        typeName(LsiSymbolId.type("java.io.IOException"), "java.io", "IOException"),
        typeName(LsiSymbolId.type("java.lang.Object"), "java.lang", "Object"),
        typeName(LsiSymbolId.type("java.lang.String"), "java.lang", "String"),
        typeName(LsiSymbolId.type("java.util.List"), "java.util", "List"),
    )

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

        assertTrue(exposedTypes.none { exposedType -> exposedType.name.startsWith("com.squareup.kotlinpoet.") })
    }
}
