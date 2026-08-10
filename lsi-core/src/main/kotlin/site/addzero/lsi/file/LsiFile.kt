package site.addzero.lsi.file

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.model.FrozenLsiFile
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.model.LsiMember
import site.addzero.lsi.model.isJvmIdentifier

enum class LsiFileNameStyle {
    JVM_IDENTIFIER,
    KOTLIN_SOURCE_STEM,
}

data class LsiImport(
    val packageName: String,
    val simpleName: String,
) {
    init {
        require(packageName.split('.').all(String::isJvmIdentifier)) {
            "LSI import package name must be a qualified JVM name: '$packageName'"
        }
        require(simpleName.isJvmIdentifier()) {
            "LSI import simple name must be a JVM identifier: '$simpleName'"
        }
    }
}

/** 语言无关的待渲染源码文件。 */
interface LsiFile {
    val language: LsiLanguage
    val packageName: String
    val fileName: String
    val fileNameStyle: LsiFileNameStyle
    val annotations: List<LsiAnnotation>
    val imports: List<LsiImport>
    val members: List<LsiMember>
    val headerComment: String?
}

fun LsiFile(
    language: LsiLanguage,
    packageName: String,
    fileName: String,
    fileNameStyle: LsiFileNameStyle = LsiFileNameStyle.JVM_IDENTIFIER,
    annotations: List<LsiAnnotation> = emptyList(),
    imports: List<LsiImport> = emptyList(),
    members: List<LsiMember>,
    headerComment: String? = null,
): LsiFile = FrozenLsiFile(
    language = language,
    packageName = packageName,
    fileName = fileName,
    fileNameStyle = fileNameStyle,
    annotations = annotations,
    imports = imports,
    members = members,
    headerComment = headerComment,
)

fun LsiFile.copy(
    language: LsiLanguage = this.language,
    packageName: String = this.packageName,
    fileName: String = this.fileName,
    fileNameStyle: LsiFileNameStyle = this.fileNameStyle,
    annotations: List<LsiAnnotation> = this.annotations,
    imports: List<LsiImport> = this.imports,
    members: List<LsiMember> = this.members,
    headerComment: String? = this.headerComment,
): LsiFile = LsiFile(
    language = language,
    packageName = packageName,
    fileName = fileName,
    fileNameStyle = fileNameStyle,
    annotations = annotations,
    imports = imports,
    members = members,
    headerComment = headerComment,
)
