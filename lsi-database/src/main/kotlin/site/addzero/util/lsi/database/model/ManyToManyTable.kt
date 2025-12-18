package site.addzero.util.lsi.database.model

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * 多对多中间表信息
 */
data class ManyToManyTable(
    val tableName: String,
    val leftTableName: String,
    val leftColumnName: String,
    val rightTableName: String,
    val rightColumnName: String,
    val leftEntity: LsiClass,
    val rightEntity: LsiClass,
    val field: LsiField
) {
    /**
     * 生成唯一的中间表名
     * 格式：{left_table}_{right_table}_mapping
     */
    companion object {
        fun generateTableName(leftTable: String, rightTable: String): String {
            // 按字母顺序排序，确保 user_role 和 role_user 都生成 role_user_mapping
            val tables = listOf(leftTable, rightTable).sorted()
            return "${tables[0]}_${tables[1]}_mapping"
        }
    }
}