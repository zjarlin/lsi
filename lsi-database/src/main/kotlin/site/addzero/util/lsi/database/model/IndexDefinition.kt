package site.addzero.util.lsi.database.model

/**
 * 索引信息
 */
data class IndexDefinition(
    val name: String,
    val columns: List<String>,
    val unique: Boolean = false,
    val type: IndexType = IndexType.NORMAL
)