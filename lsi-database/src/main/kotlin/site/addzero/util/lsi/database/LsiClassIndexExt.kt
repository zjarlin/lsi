package site.addzero.util.lsi.database

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.field.getArg
import site.addzero.util.lsi.field.hasAnnotationIgnoreCase

/**
 * 索引信息
 */
data class IndexDefinition(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false,
    val type: IndexType = IndexType.NORMAL
)

enum class IndexType {
    NORMAL,     // 普通索引
    UNIQUE,     // 唯一索引
    FULLTEXT    // 全文索引
}

/**
 * 是否有Key注解（用于索引）
 */
val LsiField.isKey: Boolean
    get() = hasAnnotationIgnoreCase("Key")

/**
 * 获取Key注解的group参数
 * Jimmer支持 @Key(group = "groupName") 来创建联合索引
 */
val LsiField.keyGroup: String?
    get() {
        if (!isKey) return null
        return getArg("Key", "group")
    }

/**
 * 是否是唯一键
 */
val LsiField.isUnique: Boolean
    get() = hasAnnotationIgnoreCase("Unique") || 
            annotations.any { 
                it.qualifiedName?.endsWith(".Column") == true && 
                it.getAttribute("unique")?.toString()?.toBoolean() == true 
            }

/**
 * 获取所有索引字段
 */
val LsiClass.indexFields: List<LsiField>
    get() = databaseFields.filter { it.isKey || it.isUnique }

/**
 * 生成索引定义
 * 
 * 支持：
 * 1. 单字段 @Key - 生成单列唯一索引
 * 2. @Key(group="groupName") - 生成联合唯一索引
 * 3. @Unique - 生成唯一索引
 */
fun LsiClass.getIndexDefinitions(): List<IndexDefinition> {
    val indexes = mutableListOf<IndexDefinition>()
    val tableName = name?.lowercase() ?: "table"
    
    // ===== 1. 处理联合索引（@Key(group="xxx")） =====
    // 按group分组
    val keyFieldsByGroup = databaseFields
        .filter { it.isKey && !it.isPrimaryKey && it.keyGroup != null }
        .groupBy { it.keyGroup!! }
    
    // 为每个group生成联合索引
    keyFieldsByGroup.forEach { (groupName, fields) ->
        val columns = fields.mapNotNull { it.columnName ?: it.name }
        if (columns.isNotEmpty()) {
            indexes.add(
                IndexDefinition(
                    name = "uk_${tableName}_${groupName}",
                    columns = columns,
                    unique = true,  // Jimmer的@Key是唯一键
                    type = IndexType.UNIQUE
                )
            )
        }
    }
    
    // ===== 2. 处理单字段索引（@Key不带group） =====
    databaseFields.forEach { field ->
        if (field.isPrimaryKey) return@forEach
        
        val columnName = field.columnName ?: field.name ?: return@forEach
        
        // 没有group的@Key注解生成单列唯一索引
        if (field.isKey && field.keyGroup == null) {
            indexes.add(
                IndexDefinition(
                    name = "uk_${tableName}_${columnName}",
                    columns = listOf(columnName),
                    unique = true,  // Jimmer的@Key是唯一键
                    type = IndexType.UNIQUE
                )
            )
        }
        
        // @Unique注解生成唯一索引
        if (field.isUnique && !field.isKey) {
            indexes.add(
                IndexDefinition(
                    name = "uk_${tableName}_${columnName}",
                    columns = listOf(columnName),
                    unique = true,
                    type = IndexType.UNIQUE
                )
            )
        }
    }
    
    return indexes
}
