package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.ArtifactEmissionMode
import site.addzero.lsi.codegen.ArtifactKind
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationArgument
import site.addzero.lsi.model.LsiAnnotationArgumentOrigin
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.type.LsiTypeParameter

class LsiSourceModelTest {

    @Test
    fun `rejects unsupported type alias declaration`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            LsiTypeDeclaration(
                name = "BookAlias",
                kind = LsiTypeDeclarationKind.TYPE_ALIAS,
            )
        }

        assertEquals(
            "Generated LSI type alias declarations are not supported: BookAlias",
            exception.message,
        )
    }

    @Test
    fun `builds language independent source artifact`() {
        val source = LsiSource.of("demo/Book.kt", LsiLanguage.KOTLIN)
        val bookTypeId = LsiSymbolId.type("demo.Book")
        val body = LsiCodeBlock.build {
            text("return ")
            name("value")
            line()
        }
        val file = LsiFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo.generated",
            fileName = "BookView",
            members = listOf(
                LsiTypeDeclaration(
                    name = "BookView",
                    kind = LsiTypeDeclarationKind.CLASS,
                    members = listOf(
                        LsiProperty(
                            name = "id",
                            type = LsiPrimitiveType(LsiPrimitiveKind.LONG),
                            mutable = false,
                        ),
                        LsiFunction(
                            name = "book",
                            returnType = LsiDeclaredType(bookTypeId),
                            body = body,
                        ),
                    ),
                )
            ),
        )
        val artifact = LsiSourceArtifact(
            file = file,
            typeNames = listOf(LsiTypeName(bookTypeId, "demo", listOf("Book"))),
            aggregationMode = ArtifactAggregationMode.ISOLATING,
            originatingSymbols = setOf(bookTypeId),
            originatingSources = setOf(source),
        )

        assertEquals(ArtifactKind.KOTLIN_SOURCE, artifact.kind)
        assertEquals("demo.generated.BookView", artifact.qualifiedFileName)
        val generated = artifact.generatedArtifact("package demo.generated\n")
        assertEquals("demo/generated/BookView.kt", generated.path)
        assertEquals(setOf(bookTypeId), generated.dependencySymbols)
    }

    @Test
    fun `rejects malformed code indentation and artifact origins`() {
        assertFailsWith<IllegalArgumentException> {
            LsiCodeBlock(listOf(LsiCodePart.Unindent))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiCodeBlock(listOf(LsiCodePart.EndControlFlow))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiCodeBlock(
                listOf(
                    LsiCodePart.NextControlFlow(
                        LsiCodeBlock.build { text("else") }
                    )
                )
            )
        }
        val file = LsiFile(
            language = LsiLanguage.JAVA,
            packageName = "demo",
            fileName = "Book",
            members = listOf(LsiTypeDeclaration("Book", LsiTypeDeclarationKind.CLASS)),
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            LsiSourceArtifact(
                file = file,
                typeNames = emptyList(),
                aggregationMode = ArtifactAggregationMode.ISOLATING,
            )
        }
        assertTrue(exception.message.orEmpty().contains("originating symbol"))
        assertFailsWith<IllegalArgumentException> {
            LsiSourceArtifact(
                file = file,
                typeNames = emptyList(),
                aggregationMode = ArtifactAggregationMode.ISOLATING,
                emissionMode = ArtifactEmissionMode.STABLE,
                originatingSymbols = setOf(LsiSymbolId.type("demo.Book")),
            )
        }
    }

    @Test
    fun `builds balanced structural statements and control flow`() {
        val body = LsiCodeBlock.build {
            beginControlFlow { text("if (ready)") }
            statement { text("run()") }
            nextControlFlow { text("else") }
            statement { text("stop()") }
            endControlFlow()
        }

        assertEquals(5, body.parts.size)
        assertTrue(body.parts.first() is LsiCodePart.BeginControlFlow)
        assertTrue(body.parts.last() is LsiCodePart.EndControlFlow)
    }

    @Test
    fun `models return with and without a value`() {
        val body = LsiCodeBlock.build {
            returnValue { name("value") }
            returnVoid()
        }

        assertEquals(2, body.parts.size)
        assertTrue((body.parts[0] as LsiCodePart.Return).value != null)
        assertEquals(null, (body.parts[1] as LsiCodePart.Return).value)
    }

    @Test
    fun `models returned and statement braced expressions`() {
        val body = LsiCodeBlock.build {
            returnBracedExpression(
                prefix = { text("transaction()") },
                body = { statement { text("run()") } },
            )
            statementBracedExpression(
                prefix = { text("consume(") },
                body = { statement { text("run()") } },
                suffix = { text(")") },
            )
        }

        val expressions = body.parts.filterIsInstance<LsiCodePart.BracedExpression>()
        assertEquals(
            listOf(
                LsiBracedExpressionCompletion.RETURN,
                LsiBracedExpressionCompletion.STATEMENT,
            ),
            expressions.map(LsiCodePart.BracedExpression::completion),
        )
    }

    @Test
    fun `rejects source extension and non trailing vararg`() {
        assertFailsWith<IllegalArgumentException> {
            LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo",
                fileName = "Book.java",
                members = listOf(LsiTypeDeclaration("Book", LsiTypeDeclarationKind.CLASS)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFunction(
                name = "consume",
                parameters = listOf(
                    LsiParameter(
                        name = "values",
                        type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                        modifiers = setOf(LsiModifier.VARARG),
                    ),
                    LsiParameter(
                        name = "tail",
                        type = LsiPrimitiveType(LsiPrimitiveKind.INT),
                    ),
                ),
            )
        }
    }

    @Test
    fun `models escaped Kotlin declarations and explicit imports`() {
        val type = LsiTypeDeclaration(
            name = "Order-ItemFetcherDsl",
            kind = LsiTypeDeclarationKind.CLASS,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val function = LsiFunction(
            name = "children*",
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val property = LsiProperty(
            name = "emptyOrder-ItemFetcher",
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            mutable = false,
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val parameter = LsiParameter(
            name = "display-name",
            type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
            nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val setter = LsiAccessor(
            setterParameterName = "display-name",
            setterParameterNameStyle = LsiNameStyle.KOTLIN_ESCAPED,
        )
        val sourceImport = LsiImport("demo.child", "by")

        assertEquals(LsiNameStyle.KOTLIN_ESCAPED, type.nameStyle)
        assertEquals(LsiNameStyle.KOTLIN_ESCAPED, function.nameStyle)
        assertEquals(LsiNameStyle.KOTLIN_ESCAPED, property.nameStyle)
        assertEquals(LsiNameStyle.KOTLIN_ESCAPED, parameter.nameStyle)
        assertEquals("display-name", setter.setterParameterName)
        assertEquals(LsiNameStyle.KOTLIN_ESCAPED, setter.setterParameterNameStyle)
        assertEquals("demo.child", sourceImport.packageName)
        assertFailsWith<IllegalArgumentException> {
            LsiTypeDeclaration(
                name = "broken`name",
                kind = LsiTypeDeclarationKind.CLASS,
                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFunction(
                name = "broken`name",
                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiProperty(
                name = "broken`name",
                type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                mutable = false,
                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiParameter(
                name = "broken`name",
                type = LsiDeclaredType(LsiSymbolId.type("java.lang.String")),
                nameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiAccessor(
                setterParameterName = "broken`name",
                setterParameterNameStyle = LsiNameStyle.KOTLIN_ESCAPED,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiImport("demo.child", "broken-name")
        }
    }

    @Test
    fun `models annotation array source styles without changing the default`() {
        val literal = LsiAnnotationValue.ArrayValue(emptyList())
        val factoryCall = LsiAnnotationValue.ArrayValue(
            elements = emptyList(),
            sourceStyle = LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF,
        )

        assertEquals(LsiAnnotationArrayStyle.LITERAL, literal.sourceStyle)
        assertEquals(LsiAnnotationArrayStyle.KOTLIN_ARRAY_OF, factoryCall.sourceStyle)
    }

    @Test
    fun `models source layout independently from semantic types and code`() {
        val producerType = LsiDeclaredType(LsiSymbolId.type("demo.BookDraft.Producer"))
        val field = LsiField(
            name = "producer",
            type = producerType,
            typeReferenceStyle = LsiTypeReferenceStyle.SAME_PACKAGE_OUTER_QUALIFIED,
        )
        val expression = LsiFunction(
            name = "producer",
            returnType = producerType,
            body = LsiCodeBlock.build { name("producer") },
            bodyStyle = LsiBodyStyle.EXPRESSION,
        )
        val explicitlyIndented = LsiCodeBlock.build {
            preserveExplicitIndentation()
            text("Factory\n")
            indent { text(".create()") }
        }
        val composedIndentation = LsiCodeBlock.build {
            text("val value = ")
            add(explicitlyIndented)
        }
        val annotation = sourceLsiAnnotation(
            type = LsiSymbolId.type("demo.Ordered"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("id")),
                LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("name")),
            ),
            argumentLayout = LsiAnnotationArgumentLayout.SINGLE_LINE,
        )

        assertEquals(producerType, field.type)
        assertEquals(LsiBodyStyle.EXPRESSION, expression.bodyStyle)
        assertEquals(LsiCodeBlockIndentation.EXPLICIT, explicitlyIndented.indentation)
        assertEquals(LsiCodeBlockIndentation.EXPLICIT, composedIndentation.indentation)
        assertEquals(LsiAnnotationArgumentLayout.SINGLE_LINE, annotation.argumentLayout)
        assertFailsWith<IllegalArgumentException> {
            LsiFunction(name = "empty", bodyStyle = LsiBodyStyle.EXPRESSION)
        }
        assertFailsWith<IllegalArgumentException> {
            LsiAccessor(bodyStyle = LsiBodyStyle.EXPRESSION)
        }
    }

    @Test
    fun `models raw Kotlin source stems without weakening Java file names`() {
        val kotlinFile = LsiFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo",
            fileName = "order-itemFetcher",
            fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
            members = listOf(LsiTypeDeclaration("OrderFetcher", LsiTypeDeclarationKind.CLASS)),
        )

        assertEquals("order-itemFetcher", kotlinFile.fileName)
        assertFailsWith<IllegalArgumentException> {
            LsiFile(
                language = LsiLanguage.JAVA,
                packageName = "demo",
                fileName = "order-itemFetcher",
                fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
                members = listOf(LsiTypeDeclaration("OrderFetcher", LsiTypeDeclarationKind.CLASS)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFile(
                language = LsiLanguage.KOTLIN,
                packageName = "demo",
                fileName = " order-itemFetcher",
                fileNameStyle = LsiFileNameStyle.KOTLIN_SOURCE_STEM,
                members = listOf(LsiTypeDeclaration("OrderFetcher", LsiTypeDeclarationKind.CLASS)),
            )
        }
    }

    @Test
    fun `models reified parameters only on inline functions`() {
        val parameterId = LsiSymbolId.typeParameter(
            LsiSymbolId.type("demo.QueryExtensions"),
            "S",
        )
        val parameter = LsiTypeParameter(parameterId, "S")
        val function = LsiFunction(
            name = "query",
            modifiers = setOf(LsiModifier.INLINE),
            typeParameters = listOf(parameter),
            reifiedTypeParameterIds = setOf(parameterId),
        )

        assertEquals(setOf(parameterId), function.reifiedTypeParameterIds)
        assertFailsWith<IllegalArgumentException> {
            LsiFunction(
                name = "query",
                typeParameters = listOf(parameter),
                reifiedTypeParameterIds = setOf(parameterId),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiFunction(
                name = "query",
                modifiers = setOf(LsiModifier.INLINE),
                reifiedTypeParameterIds = setOf(parameterId),
            )
        }
    }

    @Test
    fun `lowers only explicit annotation arguments in deterministic order`() {
        val nested = LsiAnnotation(
            type = LsiSymbolId.type("demo.Nested"),
            arguments = linkedMapOf(
                "zeta" to argument("ignored", LsiAnnotationArgumentOrigin.DEFAULT),
                "beta" to argument("second", LsiAnnotationArgumentOrigin.EXPLICIT),
                "alpha" to argument("first", LsiAnnotationArgumentOrigin.EXPLICIT),
            ),
        )
        val annotation = LsiAnnotation(
            type = LsiSymbolId.type("demo.Container"),
            arguments = linkedMapOf(
                "optional" to argument("ignored", LsiAnnotationArgumentOrigin.DEFAULT),
                "nested" to LsiAnnotationArgument(
                    value = LsiAnnotationValue.NestedAnnotationValue(nested),
                    origin = LsiAnnotationArgumentOrigin.EXPLICIT,
                ),
                "label" to argument("container", LsiAnnotationArgumentOrigin.EXPLICIT),
            ),
        )

        val lowered = annotation.toSourceAnnotation()

        val namedArguments = lowered.sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Named>()
        assertEquals(listOf("label", "nested"), namedArguments.map { argument -> argument.name })
        val nestedValue = namedArguments.last().value as LsiAnnotationValue.NestedAnnotationValue
        assertEquals(
            listOf("alpha", "beta"),
            nestedValue.annotation.sourceArguments
                .filterIsInstance<LsiSourceAnnotationArgument.Named>()
                .map { argument -> argument.name },
        )
    }

    @Test
    fun `models positional arguments before named arguments`() {
        val annotation = sourceLsiAnnotation(
            type = LsiSymbolId.type("kotlin.Suppress"),
            arguments = listOf(
                LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("first")),
                LsiSourceAnnotationArgument.Positional(LsiAnnotationValue.StringValue("second")),
                LsiSourceAnnotationArgument.Named(
                    name = "level",
                    value = LsiAnnotationValue.StringValue("warning"),
                ),
            ),
        )

        assertTrue(annotation.sourceArguments[0] is LsiSourceAnnotationArgument.Positional)
        assertEquals("level", (annotation.sourceArguments[2] as LsiSourceAnnotationArgument.Named).name)
        assertFailsWith<IllegalArgumentException> {
            sourceLsiAnnotation(
                type = annotation.type,
                arguments = listOf(
                    LsiSourceAnnotationArgument.Named(
                        name = "level",
                        value = LsiAnnotationValue.StringValue("warning"),
                    ),
                    LsiSourceAnnotationArgument.Positional(
                        LsiAnnotationValue.StringValue("late")
                    ),
                ),
            )
        }
    }

    private fun argument(
        value: String,
        origin: LsiAnnotationArgumentOrigin,
    ): LsiAnnotationArgument {
        return LsiAnnotationArgument(
            value = LsiAnnotationValue.StringValue(value),
            origin = origin,
        )
    }
}
