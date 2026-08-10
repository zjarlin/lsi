package site.addzero.lsi.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import site.addzero.lsi.codegen.ArtifactAggregationMode
import site.addzero.lsi.codegen.LsiSourceArtifact
import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

class LsiTypeNameTest {

    @Test
    fun `使用显式边界创建顶层生成类型名`() {
        assertEquals(
            LsiTypeName(
                typeId = LsiSymbolId.type("Demo.API.Result"),
                packageName = "Demo.API",
                simpleNames = listOf("Result"),
            ),
            generatedTopLevelTypeName("Demo.API", "Result"),
        )
        assertEquals(
            LsiTypeName(
                typeId = LsiSymbolId.type("Result"),
                packageName = "",
                simpleNames = listOf("Result"),
            ),
            generatedTopLevelTypeName("", "Result"),
        )
    }

    @Test
    fun `保留大写包和小写嵌套类型的精确边界`() {
        val typeName = LsiTypeName(
            typeId = LsiSymbolId.type("Demo.API.order.item"),
            packageName = "Demo.API",
            simpleNames = listOf("order", "item"),
        )

        assertEquals("Demo.API.order.item", typeName.canonicalName)
    }

    @Test
    fun `保留需要 Kotlin 转义的源码类型名`() {
        val typeName = LsiTypeName(
            typeId = LsiSymbolId.type("demo.escaped.Order-Item.Draft Impl"),
            packageName = "demo.escaped",
            simpleNames = listOf("Order-Item", "Draft Impl"),
        )

        assertEquals("demo.escaped.Order-Item.Draft Impl", typeName.canonicalName)
    }

    @Test
    fun `拒绝与稳定类型身份不一致的源码名`() {
        assertFailsWith<IllegalArgumentException> {
            LsiTypeName(
                typeId = LsiSymbolId.type("demo.order.item"),
                packageName = "demo.order",
                simpleNames = listOf("Item"),
            )
        }
    }

    @Test
    fun `从声明嵌套链解析并合并显式生成类型名`() {
        val outerId = LsiSymbolId.type("Demo.API.order")
        val nestedId = LsiSymbolId.type("Demo.API.order.item")
        val generatedId = LsiSymbolId.type("Generated.Package.result.row")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )
        val generated = LsiTypeName(
            typeId = generatedId,
            packageName = "Generated.Package",
            simpleNames = listOf("result", "row"),
        )

        assertEquals(
            listOf(
                LsiTypeName(nestedId, "Demo.API", listOf("order", "item")),
                generated,
            ),
            workspace.toLsiTypeNames(
                typeIds = listOf(nestedId, generatedId),
                additional = listOf(generated),
            ),
        )
    }

    @Test
    fun `从冻结嵌套声明派生同级生成类型名`() {
        val outerId = LsiSymbolId.type("Demo.API.order")
        val nestedId = LsiSymbolId.type("Demo.API.order.item")
        val generatedId = LsiSymbolId.type("Demo.API.order.itemTable.Remote")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )

        assertEquals(
            LsiTypeName(
                typeId = generatedId,
                packageName = "Demo.API",
                simpleNames = listOf("order", "itemTable", "Remote"),
            ),
            workspace.generatedSiblingTypeName(
                sourceTypeId = nestedId,
                generatedTypeId = generatedId,
                simpleNameSuffix = "Table",
                nestedSimpleNames = listOf("Remote"),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            workspace.generatedSiblingTypeName(
                sourceTypeId = nestedId,
                generatedTypeId = generatedId,
                simpleNameSuffix = "Table.Remote",
            )
        }
    }

    @Test
    fun `忽略未请求的显式名称并拒绝与声明边界冲突`() {
        val typeId = LsiSymbolId.type("demo.order.item")
        val workspace = LsiWorkspace(
            declarations = listOf(typeDeclaration(typeId, "item")),
        )
        val conflicting = LsiTypeName(
            typeId = typeId,
            packageName = "demo",
            simpleNames = listOf("order", "item"),
        )
        val unused = LsiTypeName(
            typeId = LsiSymbolId.type("generated.value"),
            packageName = "generated",
            simpleNames = listOf("value"),
        )

        assertEquals(emptyList(), LsiWorkspace.EMPTY.toLsiTypeNames(emptyList(), listOf(unused)))
        assertFailsWith<IllegalArgumentException> {
            workspace.toLsiTypeNames(listOf(typeId), listOf(conflicting))
        }
        assertFailsWith<IllegalArgumentException> {
            LsiWorkspace.EMPTY.toLsiTypeNames(emptyList(), listOf(unused, unused))
        }
    }

    @Test
    fun `声明缺失时不回退到类型标识字符串拆分`() {
        assertFailsWith<IllegalArgumentException> {
            LsiWorkspace.EMPTY.toLsiTypeNames(
                listOf(LsiSymbolId.type("demo.missing.type")),
            )
        }
    }

    @Test
    fun `拒绝限定名与 enclosing 链不一致的声明`() {
        val outerId = LsiSymbolId.type("other.order")
        val nestedId = LsiSymbolId.type("demo.order.item")
        val workspace = LsiWorkspace(
            declarations = listOf(
                typeDeclaration(outerId, "order"),
                typeDeclaration(nestedId, "item", outerId),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            workspace.toLsiTypeNames(listOf(nestedId))
        }
    }

    @Test
    fun `源码产物拒绝重复和缺失类型名`() {
        val referencedId = LsiSymbolId.type("demo.value")
        val typeName = LsiTypeName(referencedId, "demo", listOf("value"))
        val file = LsiFile(
            language = LsiLanguage.KOTLIN,
            packageName = "demo.generated",
            fileName = "Result",
            members = listOf(
                LsiFunction(
                    name = "value",
                    returnType = LsiDeclaredType(referencedId),
                )
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            LsiSourceArtifact(
                file = file,
                typeNames = emptyList(),
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            LsiSourceArtifact(
                file = file,
                typeNames = listOf(typeName, typeName),
                aggregationMode = ArtifactAggregationMode.AGGREGATING,
            )
        }
    }

    private fun typeDeclaration(
        id: LsiSymbolId,
        name: String,
        enclosingTypeId: LsiSymbolId? = null,
    ): LsiClass {
        return LsiClass(
            id = id,
            name = name,
            qualifiedName = id.requireTypeQualifiedName(),
            kind = LsiTypeDeclarationKind.CLASS,
            enclosingTypeId = enclosingTypeId,
            origin = LsiOrigin(LsiOriginKind.SYNTHETIC),
        )
    }
}
