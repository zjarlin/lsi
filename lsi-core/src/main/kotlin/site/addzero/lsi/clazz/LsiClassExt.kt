package site.addzero.lsi.clazz

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace
import site.addzero.lsi.type.LsiDeclaredType

/** 按稳定符号查询冻结类声明。 */
fun LsiWorkspace.classDeclaration(id: LsiSymbolId): LsiClass? = this[id] as? LsiClass

/** 返回声明中所有直接父类型，排除非声明类型投影。 */
val LsiClass.directSuperTypes: List<LsiDeclaredType>
    get() = superTypes.filterIsInstance<LsiDeclaredType>()
