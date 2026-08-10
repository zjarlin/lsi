package site.addzero.lsi.model

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

enum class LsiAnnotationScopeKind {
    PACKAGE,
    FILE
}

/**
 * 承载不属于声明的包级和文件级注解语义。
 */
sealed interface LsiAnnotationScope {
    val id: LsiSymbolId
    val kind: LsiAnnotationScopeKind
    val packageName: String
    val annotations: List<LsiAnnotation>
    val location: LsiLocation?
    val origin: LsiOrigin
}

/** 包级注解作用域接口。 */
interface LsiPackageAnnotationScope : LsiAnnotationScope {
    override val id: LsiSymbolId
        get() = LsiSymbolId.packageScope(packageName)

    override val kind: LsiAnnotationScopeKind
        get() = LsiAnnotationScopeKind.PACKAGE
}

/** 文件级注解作用域接口。 */
interface LsiFileAnnotationScope : LsiAnnotationScope {
    val logicalPath: String

    override val id: LsiSymbolId
        get() = LsiSymbolId.fileScope(packageName, logicalPath)

    override val kind: LsiAnnotationScopeKind
        get() = LsiAnnotationScopeKind.FILE
}

internal data class FrozenLsiPackageAnnotationScope(
    override val packageName: String,
    override val annotations: List<LsiAnnotation>,
    override val location: LsiLocation?,
    override val origin: LsiOrigin,
) : LsiPackageAnnotationScope

internal data class FrozenLsiFileAnnotationScope(
    override val packageName: String,
    override val logicalPath: String,
    override val annotations: List<LsiAnnotation>,
    override val location: LsiLocation?,
    override val origin: LsiOrigin,
) : LsiFileAnnotationScope {
    init {
        require(logicalPath.isNotBlank()) { "LSI file annotation scope logical path cannot be blank" }
        require(
            logicalPath == LsiSource.of(logicalPath).path &&
                !logicalPath.hasWindowsDrivePrefix()
        ) {
            "LSI file annotation scope logical path must be normalized and relative: '$logicalPath'"
        }
    }
}

fun LsiPackageAnnotationScope(
    packageName: String,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
): LsiPackageAnnotationScope = FrozenLsiPackageAnnotationScope(
    packageName,
    annotations,
    location,
    origin,
)

fun LsiPackageAnnotationScope.copy(
    packageName: String = this.packageName,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
): LsiPackageAnnotationScope = LsiPackageAnnotationScope(packageName, annotations, location, origin)

fun LsiFileAnnotationScope(
    packageName: String,
    logicalPath: String,
    annotations: List<LsiAnnotation> = emptyList(),
    location: LsiLocation? = null,
    origin: LsiOrigin,
): LsiFileAnnotationScope = FrozenLsiFileAnnotationScope(
    packageName,
    logicalPath,
    annotations,
    location,
    origin,
)

fun LsiFileAnnotationScope.copy(
    packageName: String = this.packageName,
    logicalPath: String = this.logicalPath,
    annotations: List<LsiAnnotation> = this.annotations,
    location: LsiLocation? = this.location,
    origin: LsiOrigin = this.origin,
): LsiFileAnnotationScope = LsiFileAnnotationScope(
    packageName,
    logicalPath,
    annotations,
    location,
    origin,
)

private fun String.hasWindowsDrivePrefix(): Boolean {
    return length >= 3 && this[0].isLetter() && this[1] == ':' && this[2] == '/'
}
