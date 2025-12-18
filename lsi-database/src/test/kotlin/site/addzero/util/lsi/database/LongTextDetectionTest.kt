package site.addzero.util.lsi.database

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.type.LsiType
import site.addzero.util.lsi_impl.impl.database.field.isText
import site.addzero.util.lsi_impl.impl.database.field.length

/**
 * 长文本字段检测测试
 */
class LongTextDetectionTest {

    @Test
    fun `test isText with Length annotation over threshold`() {
        // Given: @Length(2000) - 超过1000阈值
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "2000"))
            )
        )

        // Then
        assertTrue(field.isText, "长度超过1000应该被识别为长文本")
        assertEquals(2000, field.length)
    }

    @Test
    fun `test isText with Length max parameter`() {
        // Given: @Length(max=5000)
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("max" to "5000"))
            )
        )

        // Then
        assertTrue(field.isText)
        assertEquals(5000, field.length)
    }

    @Test
    fun `test isText with Column length over threshold`() {
        // Given: @Column(length=3000)
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("length" to "3000"))
            )
        )

        // Then
        assertTrue(field.isText)
        assertEquals(3000, field.length)
    }

    @Test
    fun `test not isText with short length`() {
        // Given: @Length(255) - 未超过阈值
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "255"))
            )
        )

        // Then
        assertFalse(field.isText, "短长度不应该被识别为长文本")
        assertEquals(255, field.length)
    }

    @Test
    fun `test isText with Lob annotation`() {
        // Given: @Lob - 明确标记为大对象
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Lob", emptyMap())
            )
        )

        // Then
        assertTrue(field.isText, "@Lob应该被识别为长文本")
    }

    @Test
    fun `test isText with Column columnDefinition TEXT`() {
        // Given: @Column(columnDefinition = "TEXT")
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("columnDefinition" to "TEXT"))
            )
        )

        // Then
        assertTrue(field.isText)
    }

    @Test
    fun `test isText with Column columnDefinition LONGTEXT`() {
        // Given: @Column(columnDefinition = "LONGTEXT")
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("columnDefinition" to "LONGTEXT"))
            )
        )

        // Then
        assertTrue(field.isText)
    }

    @Test
    fun `test isText with Column columnDefinition CLOB`() {
        // Given: @Column(columnDefinition = "CLOB")
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("columnDefinition" to "CLOB"))
            )
        )

        // Then
        assertTrue(field.isText)
    }

    @Test
    fun `test not isText for non-String type`() {
        // Given: Integer类型字段，即使有@Length
        val field = createMockField(
            typeName = "Integer",
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "2000"))
            )
        )

        // Then
        assertFalse(field.isText, "非String类型不应该被识别为长文本")
    }

    @Test
    fun `test not isText without annotations`() {
        // Given: 普通String字段，无长度注解
        val field = createMockStringField(
            annotations = emptyList()
        )

        // Then
        assertFalse(field.isText)
        assertEquals(-1, field.length, "无长度注解应返回-1")
    }

    @Test
    fun `test length priority - Length before Column`() {
        // Given: 同时有@Length和@Column(length)
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "1000")),
                createAnnotation("Column", mapOf("length" to "500"))
            )
        )

        // Then: @Length优先级更高
        assertEquals(1000, field.length, "@Length应该优先于@Column")
    }

    @Test
    fun `test length with max parameter priority`() {
        // Given: @Length同时有value和max
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf(
                    "value" to "100",
                    "max" to "200"
                ))
            )
        )

        // Then: value优先级更高
        assertEquals(100, field.length, "value参数应该优先于max")
    }

    @Test
    fun `test threshold boundary - exactly 1000`() {
        // Given: 长度恰好为1000
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "1000"))
            )
        )

        // Then: 1000不应该被识别为长文本（需要>1000）
        assertFalse(field.isText, "长度恰好1000不应该是长文本")
    }

    @Test
    fun `test threshold boundary - 1001`() {
        // Given: 长度为1001
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Length", mapOf("value" to "1001"))
            )
        )

        // Then: 1001应该被识别为长文本
        assertTrue(field.isText, "长度1001应该是长文本")
    }

    @Test
    fun `test case insensitive columnDefinition`() {
        // Given: columnDefinition使用小写
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("columnDefinition" to "text"))
            )
        )

        // Then: 应该能识别（不区分大小写）
        assertTrue(field.isText)
    }

    @Test
    fun `test columnDefinition with mediumtext`() {
        // Given: @Column(columnDefinition = "MEDIUMTEXT")
        val field = createMockStringField(
            annotations = listOf(
                createAnnotation("Column", mapOf("columnDefinition" to "MEDIUMTEXT"))
            )
        )

        // Then
        assertTrue(field.isText)
    }

    // ============ Mock对象工厂 ============

    private fun createMockStringField(
        name: String = "content",
        annotations: List<LsiAnnotation> = emptyList()
    ): LsiField = createMockField("String", annotations)

    private fun createMockField(
        typeName: String,
        annotations: List<LsiAnnotation> = emptyList()
    ): LsiField {
        return object : LsiField {

            override val name: String = "field"
            override val typeName: String = typeName
            override val type: LsiType? = null
            override val annotations: List<LsiAnnotation> = annotations
            override val comment: String? = null
            override val isStatic: Boolean = false
            override val isConstant: Boolean = false
            override val isVar: Boolean = false
            override val isLateInit: Boolean = false
            override val isCollectionType: Boolean = false
            override val defaultValue: String? = null
            override val columnName: String? = null
            override val declaringClass get() = null
            override val fieldTypeClass get() = null
            override val isNestedObject: Boolean
                get() = TODO("Not yet implemented")
            override val children: List<LsiField>
                get() = TODO("Not yet implemented")
            override val isNullable: Boolean
                get() = TODO("Not yet implemented")
        }
    }

    private fun createAnnotation(
        simpleName: String,
        attributes: Map<String, String>
    ): LsiAnnotation {
        return object : LsiAnnotation {
            override val qualifiedName: String = "javax.validation.constraints.$simpleName"
            override val simpleName: String = simpleName
            override val attributes: Map<String, Any?> = attributes
            override fun getAttribute(name: String): Any? = attributes[name]
            override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
        }
    }
}
