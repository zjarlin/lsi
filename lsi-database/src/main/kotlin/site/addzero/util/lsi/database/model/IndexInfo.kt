package site.addzero.util.lsi.database.model

/**
 * 索引信息数据类
 * 用于表示数据库索引
 */
data class IndexInfo(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false
)