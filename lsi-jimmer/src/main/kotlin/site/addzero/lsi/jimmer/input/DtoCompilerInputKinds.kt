package site.addzero.lsi.jimmer.input

import site.addzero.lsi.compiler.CompilerInputDocumentKind
import site.addzero.lsi.compiler.CompilerInputDocumentReferenceKind
import site.addzero.lsi.model.LsiTypeSeedMode

val DTO_INPUT_DOCUMENT_KIND = CompilerInputDocumentKind("jimmer.dto")

val DTO_SUBJECT_TYPE_REFERENCE_KIND = dtoReferenceKind("subject-type")

val DTO_TARGET_TYPE_REFERENCE_KIND = dtoReferenceKind("target-type")

val DTO_ANNOTATION_TYPE_REFERENCE_KIND = dtoReferenceKind("annotation-type")

val DTO_SUPER_TYPE_REFERENCE_KIND = dtoReferenceKind("super-type")

val DTO_MODEL_TYPE_REFERENCE_KIND = dtoReferenceKind("model-type")

val DTO_REUSABLE_TYPE_REFERENCE_KIND = dtoReferenceKind("reusable-type")

val DTO_TYPE_USAGE_REFERENCE_KIND = dtoReferenceKind(
    name = "type-usage",
    seedMode = LsiTypeSeedMode.HEADER,
)

val DTO_CONFIG_IMPLEMENTATION_REFERENCE_KIND = dtoReferenceKind("config-implementation")

private fun dtoReferenceKind(
    name: String,
    seedMode: LsiTypeSeedMode = LsiTypeSeedMode.FULL_DECLARATION,
): CompilerInputDocumentReferenceKind {
    return CompilerInputDocumentReferenceKind("jimmer.dto.$name", seedMode)
}
