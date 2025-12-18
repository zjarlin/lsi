package site.addzero.util.lsi.database

/**
 * 外键信息数据类
 * 用于表示数据库外键约束
 */
data class ForeignKeyInfo(
    val name: String,
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String
)
