package site.addzero.util.lsi_impl.impl.intellij.virtualfile

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import site.addzero.util.lsi.constant.Language


/**
 * 获取Kotlin类文件中的实体类定义
 *
 * @param project 可选参数，如果不提供则通过ProjectLocator自动推断
 */
fun VirtualFile.guessProject(): Project {
    val project = ProjectLocator.getInstance().guessProjectForFile(this)
    return project ?: throw IllegalStateException("Cannot determine project for file: ${this.path}")
}

/**
 * 扩展属性：获取 VirtualFile 的语言类型
 * 根据文件扩展名判断语言类型
 * @return Language 枚举值，包括 Java、Kotlin 等
 * @throws IllegalArgumentException 当文件类型不支持时抛出异常
 */
val VirtualFile.language: Language
    get() {
        return when (val fileType = extension) {
            "java" -> Language.Java
            "kt" -> Language.Kotlin
            else -> throw IllegalArgumentException("Unsupported file type: ${fileType ?: "<no-type>"}")
        }
    }

