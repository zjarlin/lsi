package site.addzero.lsi.model

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationArgument
import site.addzero.lsi.anno.LsiAnnotationArgumentLayout
import site.addzero.lsi.anno.LsiAnnotationUseSiteTarget
import site.addzero.lsi.anno.LsiSourceAnnotationArgument
import site.addzero.lsi.core.LsiSymbolId

internal data class FrozenLsiAnnotation(
    override val type: LsiSymbolId,
    override val arguments: Map<String, LsiAnnotationArgument> = emptyMap(),
    override val useSiteTarget: LsiAnnotationUseSiteTarget? = null,
    override val explicitArgumentNamesInSourceOrder: List<String> = emptyList(),
    override val sourceArguments: List<LsiSourceAnnotationArgument> = emptyList(),
    override val argumentLayout: LsiAnnotationArgumentLayout = LsiAnnotationArgumentLayout.PLATFORM_DEFAULT,
) : LsiAnnotation {

    init {
        require(arguments.keys.none(String::isBlank)) { "LSI annotation argument name cannot be blank" }
        require(explicitArgumentNamesInSourceOrder.distinct().size == explicitArgumentNamesInSourceOrder.size) {
            "LSI annotation explicit argument order cannot contain duplicate names: ${type.value}"
        }
        require(
            explicitArgumentNamesInSourceOrder.isEmpty() ||
                explicitArgumentNamesInSourceOrder.toSet() == arguments
                    .filterValues(LsiAnnotationArgument::isExplicit)
                    .keys
        ) {
            "LSI annotation explicit argument order must contain every explicit argument: ${type.value}"
        }
        val namedArguments = sourceArguments.filterIsInstance<LsiSourceAnnotationArgument.Named>()
        require(namedArguments.map(LsiSourceAnnotationArgument.Named::name).distinct().size == namedArguments.size) {
            "LSI annotation cannot declare duplicate named source arguments: $type"
        }
        var namedArgumentObserved = false
        sourceArguments.forEach { argument ->
            when (argument) {
                is LsiSourceAnnotationArgument.Named -> namedArgumentObserved = true
                is LsiSourceAnnotationArgument.Positional -> require(!namedArgumentObserved) {
                    "LSI positional annotation arguments must precede named arguments: $type"
                }
            }
        }
    }
}
