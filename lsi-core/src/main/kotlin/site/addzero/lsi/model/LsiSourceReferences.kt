package site.addzero.lsi.model

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.anno.toSourceAnnotation
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.field.LsiProperty
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiTypeParameter

import site.addzero.lsi.core.LsiSymbolId

/** 收集单个源码注解中以结构化形式出现的全部稳定符号引用。 */
fun LsiAnnotation.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectSourceAnnotationDependencies(this@referencedSymbolIds)
    }
}

/** 收集渲染单个源码注解所需的直接类型身份。 */
val LsiAnnotation.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/**
 * 收集源码成员中以结构化形式出现的全部稳定符号引用。
 *
 * 纯文本和名称片段不承载符号身份，因此调用方必须用 [LsiCodePart.Type]
 * 表达会影响增量依赖的类型引用。
 */
fun LsiMember.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectSourceMemberDependencies(this@referencedSymbolIds)
    }
}

/**
 * 收集渲染源码成员所需的直接类型身份，不包含类型参数和成员身份。
 */
val LsiMember.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/** 收集独立代码块中以结构化形式出现的全部稳定符号引用。 */
fun LsiCodeBlock.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        collectSourceCodeDependencies(this@referencedSymbolIds)
    }
}

/** 收集渲染独立代码块所需的直接类型身份。 */
val LsiCodeBlock.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

/** 收集源码文件中以结构化形式出现的全部稳定符号引用。 */
fun LsiFile.referencedSymbolIds(): Set<LsiSymbolId> {
    return sortedSetOf<LsiSymbolId>().apply {
        annotations.forEach(::collectSourceAnnotationDependencies)
        members.forEach { member -> addAll(member.referencedSymbolIds()) }
    }
}

/** 收集渲染源码文件所需的直接类型身份，不包含类型参数和成员身份。 */
val LsiFile.referencedTypeIds: Set<LsiSymbolId>
    get() = referencedSymbolIds().filterTo(sortedSetOf(), LsiSymbolId::isTypeId)

private fun MutableSet<LsiSymbolId>.collectSourceMemberDependencies(member: LsiMember) {
    member.annotations.forEach(::collectSourceAnnotationDependencies)
    when (member) {
        is LsiConstructor -> {
            member.typeParameters.forEach(::collectSourceTypeParameterDependencies)
            member.parameters.forEach(::collectSourceParameterDependencies)
            member.thrownTypes.forEach(::collectTypeRefDependencies)
            collectSourceCodeDependencies(member.body)
            member.delegationCall?.arguments?.forEach(::collectSourceCodeDependencies)
        }
        is LsiField -> {
            collectTypeRefDependencies(member.type)
            member.initializer?.let(::collectSourceCodeDependencies)
        }
        is LsiMethod -> {
            member.typeParameters.forEach(::collectSourceTypeParameterDependencies)
            member.receiverType?.let(::collectTypeRefDependencies)
            member.parameters.forEach(::collectSourceParameterDependencies)
            if (member.renderReturnType) {
                collectTypeRefDependencies(member.returnType)
            }
            member.thrownTypes.forEach(::collectTypeRefDependencies)
            collectSourceCodeDependencies(member.body)
        }
        is LsiInitializerBlock -> collectSourceCodeDependencies(member.body)
        is LsiProperty -> {
            collectTypeRefDependencies(member.type)
            member.receiverType?.let(::collectTypeRefDependencies)
            member.initializer?.let(::collectSourceCodeDependencies)
            member.getter?.let(::collectSourceAccessorDependencies)
            member.setter?.let(::collectSourceAccessorDependencies)
        }
        is LsiClass -> {
            member.typeParameters.forEach(::collectSourceTypeParameterDependencies)
            member.superClass?.let(::collectTypeRefDependencies)
            member.superClassConstructorArguments.forEach(::collectSourceCodeDependencies)
            member.superInterfaces.forEach(::collectTypeRefDependencies)
            member.primaryConstructor?.let(::collectSourceMemberDependencies)
            member.enumEntries.forEach { constant ->
                constant.constructorArguments.forEach(::collectSourceCodeDependencies)
                constant.anonymousType?.let(::collectSourceMemberDependencies)
            }
            member.members.forEach(::collectSourceMemberDependencies)
        }
    }
}

private fun MutableSet<LsiSymbolId>.collectSourceTypeParameterDependencies(parameter: LsiTypeParameter) {
    add(parameter.id)
    parameter.upperBounds.forEach(::collectTypeRefDependencies)
}

private fun MutableSet<LsiSymbolId>.collectSourceParameterDependencies(parameter: LsiParameter) {
    parameter.annotations.forEach(::collectSourceAnnotationDependencies)
    collectTypeRefDependencies(parameter.type)
    parameter.defaultValue?.let(::collectSourceCodeDependencies)
}

private fun MutableSet<LsiSymbolId>.collectSourceAccessorDependencies(accessor: LsiAccessor) {
    accessor.annotations.forEach(::collectSourceAnnotationDependencies)
    accessor.parameterAnnotations.forEach(::collectSourceAnnotationDependencies)
    collectSourceCodeDependencies(accessor.body)
}

private fun MutableSet<LsiSymbolId>.collectSourceCodeDependencies(code: LsiCodeBlock) {
    code.parts.forEach { part ->
        when (part) {
            is LsiCodePart.BeginControlFlow -> collectSourceCodeDependencies(part.header)
            is LsiCodePart.BracedExpression -> {
                collectSourceCodeDependencies(part.prefix)
                collectSourceCodeDependencies(part.body)
                collectSourceCodeDependencies(part.suffix)
            }
            is LsiCodePart.NextControlFlow -> collectSourceCodeDependencies(part.header)
            is LsiCodePart.Return -> part.value?.let(::collectSourceCodeDependencies)
            is LsiCodePart.Statement -> collectSourceCodeDependencies(part.value)
            is LsiCodePart.Type -> collectTypeRefDependencies(part.value)
            is LsiCodePart.CharacterLiteral,
            LsiCodePart.EndControlFlow,
            LsiCodePart.Indent,
            is LsiCodePart.Literal,
            is LsiCodePart.Name,
            LsiCodePart.NewLine,
            is LsiCodePart.StringLiteral,
            is LsiCodePart.Text,
            is LsiCodePart.TopLevelMember,
            LsiCodePart.Unindent,
            -> Unit
        }
    }
}

private fun MutableSet<LsiSymbolId>.collectSourceAnnotationDependencies(annotation: LsiAnnotation) {
    add(annotation.type)
    annotation.toSourceAnnotation().sourceArguments.forEach { argument ->
        collectSourceAnnotationValueDependencies(argument.value)
    }
}

private fun MutableSet<LsiSymbolId>.collectSourceAnnotationValueDependencies(value: LsiAnnotationValue) {
    when (value) {
        is LsiAnnotationValue.ArrayValue -> value.elements.forEach(::collectSourceAnnotationValueDependencies)
        is LsiAnnotationValue.ClassValue -> collectTypeRefDependencies(value.type)
        is LsiAnnotationValue.EnumValue -> add(value.enumType)
        is LsiAnnotationValue.NestedAnnotationValue -> collectSourceAnnotationDependencies(value.annotation)
        is LsiAnnotationValue.BooleanValue,
        is LsiAnnotationValue.ByteValue,
        is LsiAnnotationValue.ShortValue,
        is LsiAnnotationValue.IntValue,
        is LsiAnnotationValue.LongValue,
        is LsiAnnotationValue.FloatValue,
        is LsiAnnotationValue.DoubleValue,
        is LsiAnnotationValue.CharValue,
        is LsiAnnotationValue.StringValue,
        -> Unit
    }
}
