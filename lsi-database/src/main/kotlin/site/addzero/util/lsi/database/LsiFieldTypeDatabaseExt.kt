package site.addzero.util.lsi.database

import site.addzero.util.lsi.field.LsiField

/**
 * 判断是否为字符串类型
 */
val LsiField.isStringType: Boolean
    get() {
        val typeName = this.typeName ?: return false
        return typeName == "java.lang.String" || 
               typeName.substringAfterLast('.').equals("string", ignoreCase = true)
    }

/**
 * 判断是否为长文本类型
 * 根据字段名称包含的关键词判断
 */
fun LsiField.isTextType(): Boolean {
    val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
    return textKeywords.any { name?.contains(it, ignoreCase = true) ?: false }
}
