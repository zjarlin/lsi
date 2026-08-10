package site.addzero.lsi.model

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.file.LsiFileNameStyle
import site.addzero.lsi.file.LsiImport

internal data class FrozenLsiFile(
    override val language: LsiLanguage,
    override val packageName: String,
    override val fileName: String,
    override val fileNameStyle: LsiFileNameStyle = LsiFileNameStyle.JVM_IDENTIFIER,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val imports: List<LsiImport> = emptyList(),
    override val members: List<LsiMember>,
    override val headerComment: String? = null,
) : LsiFile {
    init {
        require(language == LsiLanguage.JAVA || language == LsiLanguage.KOTLIN) {
            "LSI file language must be Java or Kotlin: $language"
        }
        require(packageName == packageName.trim()) {
            "LSI package name cannot have surrounding whitespace: '$packageName'"
        }
        require(packageName.isEmpty() || packageName.isQualifiedName()) {
            "LSI package name must be a qualified JVM name: '$packageName'"
        }
        when (fileNameStyle) {
            LsiFileNameStyle.JVM_IDENTIFIER -> require(fileName.isJvmIdentifier()) {
                "LSI file name must be a JVM identifier without an extension: '$fileName'"
            }
            LsiFileNameStyle.KOTLIN_SOURCE_STEM -> {
                require(language == LsiLanguage.KOTLIN) {
                    "Kotlin source stem can only be used by a Kotlin LSI file: '$fileName'"
                }
                require(fileName.isKotlinSourceStem()) {
                    "LSI Kotlin source stem is invalid: '$fileName'"
                }
            }
        }
        require(imports.distinct() == imports) {
            "LSI file cannot contain duplicate explicit imports: $fileName"
        }
        require(members.isNotEmpty()) { "LSI file must contain at least one member: $fileName" }
    }
}

private fun String.isQualifiedName(): Boolean = split('.').all(String::isJvmIdentifier)

private fun String.isKotlinSourceStem(): Boolean {
    return isNotBlank() && this == trim() && this != "." && this != ".." && none { character ->
        character == '/' || character == '\\' || character == '\n' || character == '\r' || character == '\u0000'
    }
}
