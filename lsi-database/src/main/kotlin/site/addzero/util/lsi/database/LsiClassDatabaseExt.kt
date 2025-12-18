package site.addzero.util.lsi.database

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.clazz.getArg
import site.addzero.util.lsi.field.LsiField

/**
 * 获取数据库字段（排除静态、集合等非数据库字段）
 */
val LsiClass.databaseFields: List<LsiField>
    get() = fields.filter { it.isDbField }

/**
 * 获取主键列
 */
val LsiClass.primaryKeyColumn: LsiField?
    get() = fields.find { it.isPrimaryKey }

/**
 * 获取主键名字
 */
val LsiClass.primaryKeyName: String?
    get() = fields.filter { it.isPrimaryKey }.map { it.name }.firstOrNull()

/**
 * 获取所有非主键列
 */
val LsiClass.nonPrimaryColumns: List<LsiField>
    get() = fields.filter { !it.isPrimaryKey }

/**
 * 获取数据库架构
 */
val LsiClass.dataBaseOrSchemaName: String
    get() = getArg("DataBaseSchema") ?: ""

/**
 * 获取所有数据库字段（包括继承的字段）
 * 这个方法会递归获取父类的字段
 * @return 所有数据库字段列表
 */
fun LsiClass.getAllDbFields(): List<LsiField> {
    val result = mutableListOf<LsiField>()
    result.addAll(databaseFields)
    superClasses.forEach { superClass ->
        result.addAll(superClass.getAllDbFields())
    }
    return result
}

/**
 * 获取所有外键定义
 * 从字段的 @JoinColumn, @ManyToOne, @OneToOne 等注解中提取
 */
fun LsiClass.getDatabaseForeignKeys(): List<ForeignKeyInfo> {
    return databaseFields.mapNotNull { it.getForeignKeyInfo() }
}

/**
 * 获取索引定义
 *
 * @deprecated 使用 getIndexDefinitions() 替代，该方法支持更完整的索引类型
 * @see site.addzero.util.lsi.database.getIndexDefinitions
 */
@Deprecated(
    message = "Use getIndexDefinitions() instead",
    replaceWith = ReplaceWith("this.getIndexDefinitions()", "site.addzero.util.lsi.database.getIndexDefinitions")
)
fun LsiClass.getDatabaseIndexes(): List<IndexInfo> {
    // 转换为新的格式
    return getIndexDefinitions().map { indexDef ->
        IndexInfo(
            name = indexDef.name,
            columns = indexDef.columns,
            unique = indexDef.unique
        )
    }
}

/**
 * 表注解常量
 */
private val TABLE_ANNOTATIONS = setOf(
    "javax.persistence.Table",
    "jakarta.persistence.Table",
    "org.babyfish.jimmer.sql.Table",
    "com.baomidou.mybatisplus.annotation.TableName"
)
