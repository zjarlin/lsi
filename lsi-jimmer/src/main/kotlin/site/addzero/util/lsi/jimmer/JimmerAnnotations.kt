package site.addzero.util.lsi.jimmer

internal const val ENTITY = "org.babyfish.jimmer.sql.Entity"
internal const val MAPPED_SUPERCLASS = "org.babyfish.jimmer.sql.MappedSuperclass"
internal const val EMBEDDABLE = "org.babyfish.jimmer.sql.Embeddable"
internal const val IMMUTABLE = "org.babyfish.jimmer.Immutable"

internal const val ID = "org.babyfish.jimmer.sql.Id"
internal const val VERSION = "org.babyfish.jimmer.sql.Version"
internal const val LOGICAL_DELETED = "org.babyfish.jimmer.sql.LogicalDeleted"
internal const val KEY = "org.babyfish.jimmer.sql.Key"

internal const val MANY_TO_ONE = "org.babyfish.jimmer.sql.ManyToOne"
internal const val ONE_TO_ONE = "org.babyfish.jimmer.sql.OneToOne"
internal const val ONE_TO_MANY = "org.babyfish.jimmer.sql.OneToMany"
internal const val MANY_TO_MANY = "org.babyfish.jimmer.sql.ManyToMany"

internal const val ID_VIEW = "org.babyfish.jimmer.sql.IdView"
internal const val MANY_TO_MANY_VIEW = "org.babyfish.jimmer.sql.ManyToManyView"

internal const val FORMULA = "org.babyfish.jimmer.Formula"
internal const val TRANSIENT = "org.babyfish.jimmer.sql.Transient"

internal val ALL_JIMMER_ENTITY_ANNOTATIONS = setOf(ENTITY, MAPPED_SUPERCLASS, EMBEDDABLE, IMMUTABLE)
internal val ALL_JIMMER_ASSOCIATION_ANNOTATIONS = setOf(MANY_TO_ONE, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY)
