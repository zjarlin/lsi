package site.addzero.util.lsi.database

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.type.LsiType

/**
 * Jimmer ID生成策略检测测试
 */
class JimmerIdGenerationTest {

    @Test
    fun `test IDENTITY without strategy - Jimmer default`() {
        // Given: @GeneratedValue without strategy (Jimmer默认为IDENTITY)
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", emptyMap())
            )
        )

        // Then: 应该被识别为自增
        assertTrue(field.isAutoIncrement, "不指定strategy应默认为IDENTITY")
        assertFalse(field.isSequence)
        assertFalse(field.isUUID)
        assertFalse(field.hasCustomIdGenerator)
    }

    @Test
    fun `test IDENTITY with explicit strategy`() {
        // Given: @GeneratedValue(strategy = IDENTITY)
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf("strategy" to "IDENTITY"))
            )
        )

        // Then
        assertTrue(field.isAutoIncrement)
        assertFalse(field.isSequence)
    }

    @Test
    fun `test AUTO strategy - same as IDENTITY`() {
        // Given: @GeneratedValue(strategy = AUTO)
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf("strategy" to "AUTO"))
            )
        )

        // Then: AUTO应该被识别为自增
        assertTrue(field.isAutoIncrement)
    }

    @Test
    fun `test SEQUENCE with generator name`() {
        // Given: @GeneratedValue(strategy = SEQUENCE, generatorName = "book_id_seq")
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf(
                    "strategy" to "SEQUENCE",
                    "generatorName" to "book_id_seq"
                ))
            )
        )

        // Then
        assertFalse(field.isAutoIncrement)
        assertTrue(field.isSequence, "应该被识别为序列")
        assertEquals("book_id_seq", field.sequenceName)
        assertFalse(field.isUUID)
        assertFalse(field.hasCustomIdGenerator)
    }

    @Test
    fun `test SEQUENCE without generator name`() {
        // Given: @GeneratedValue(strategy = SEQUENCE) 未指定generatorName
        val field = createMockIdField(
            name = "id",
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf("strategy" to "SEQUENCE"))
            )
        )

        // Then
        assertTrue(field.isSequence)
        assertNull(field.sequenceName, "未指定generatorName应返回null")
    }

    @Test
    fun `test UUID generator`() {
        // Given: @GeneratedValue(generatorType = UUIDIdGenerator.class)
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf(
                    "generatorType" to "org.babyfish.jimmer.sql.UUIDIdGenerator"
                ))
            )
        )

        // Then
        assertFalse(field.isAutoIncrement)
        assertFalse(field.isSequence)
        assertTrue(field.isUUID, "应该被识别为UUID生成器")
        assertFalse(field.hasCustomIdGenerator)
        assertNull(field.customIdGeneratorType)
    }

    @Test
    fun `test custom IdGenerator`() {
        // Given: @GeneratedValue(generatorType = SnowflakeIdGenerator.class)
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("GeneratedValue", mapOf(
                    "generatorType" to "com.example.SnowflakeIdGenerator"
                ))
            )
        )

        // Then
        assertFalse(field.isAutoIncrement)
        assertFalse(field.isSequence)
        assertFalse(field.isUUID)
        assertTrue(field.hasCustomIdGenerator, "应该被识别为自定义生成器")
        assertEquals("com.example.SnowflakeIdGenerator", field.customIdGeneratorType)
    }

    @Test
    fun `test no GeneratedValue annotation`() {
        // Given: 没有@GeneratedValue注解（手动赋值）
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("Id", emptyMap())
            )
        )

        // Then: 所有生成策略都应该为false
        assertFalse(field.isAutoIncrement)
        assertFalse(field.isSequence)
        assertFalse(field.isUUID)
        assertFalse(field.hasCustomIdGenerator)
    }

    @Test
    fun `test case insensitive annotation check`() {
        // Given: 注解名称大小写不同
        val field = createMockIdField(
            annotations = listOf(
                createAnnotation("generatedValue", mapOf("strategy" to "identity"))
            )
        )

        // Then: 应该能识别（不区分大小写）
        assertTrue(field.isAutoIncrement)
    }

    @Test
    fun `test JPA GenerationType constants`() {
        // Given: 使用JPA的GenerationType枚举值
        val testCases = listOf(
            "GenerationType.IDENTITY" to "isAutoIncrement",
            "GenerationType.SEQUENCE" to "isSequence",
            "GenerationType.AUTO" to "isAutoIncrement"
        )

        testCases.forEach { (strategy, expectedProperty) ->
            val field = createMockIdField(
                annotations = listOf(
                    createAnnotation("GeneratedValue", mapOf("strategy" to strategy))
                )
            )

            when (expectedProperty) {
                "isAutoIncrement" -> assertTrue(field.isAutoIncrement, "Strategy $strategy 应该是自增")
                "isSequence" -> assertTrue(field.isSequence, "Strategy $strategy 应该是序列")
            }
        }
    }

    // ============ Mock对象工厂 ============

    private fun createMockIdField(
        name: String = "id",
        typeName: String = "Long",
        annotations: List<LsiAnnotation> = emptyList()
    ): LsiField {
        return object : LsiField {
            override val name: String = name
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
        }
    }

    private fun createAnnotation(
        simpleName: String,
        attributes: Map<String, String>
    ): LsiAnnotation {
        return object : LsiAnnotation {
            override val qualifiedName: String = "org.babyfish.jimmer.sql.$simpleName"
            override val simpleName: String = simpleName
            override val attributes: Map<String, Any?> = attributes
            override fun getAttribute(name: String): Any? = attributes[name]
            override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
        }
    }
}
