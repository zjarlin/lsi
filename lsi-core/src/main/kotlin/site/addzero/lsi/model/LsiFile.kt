package site.addzero.lsi.model

import site.addzero.lsi.core.LsiLanguage

enum class LsiFileNameStyle {
    JVM_IDENTIFIER,
    KOTLIN_SOURCE_STEM,
}

data class LsiImport(
    val packageName: String,
    val simpleName: String,
) {
    init {
        require(packageName.isQualifiedName()) {
            "LSI import package name must be a qualified JVM name: '$packageName'"
        }
        require(simpleName.isJvmIdentifier()) {
            "LSI import simple name must be a JVM identifier: '$simpleName'"
        }
    }
}

data class LsiFile(
    val language: LsiLanguage,
    val packageName: String,
    val fileName: String,
    val fileNameStyle: LsiFileNameStyle = LsiFileNameStyle.JVM_IDENTIFIER,
    val annotations: List<LsiAnnotation> = emptyList(),
    val imports: List<LsiImport> = emptyList(),
    val members: List<LsiMember>,
    val headerComment: String? = null,
) {
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
