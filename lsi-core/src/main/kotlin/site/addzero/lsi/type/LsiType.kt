package site.addzero.lsi.type

import site.addzero.lsi.anno.LsiAnnotation

/**
 * 语言无关的类型使用接口。
 *
 * 声明类型、泛型参数、原始类型、数组和函数类型都通过该接口进入共享编译流程，
 * 平台前端不得把 javac 或 KSP 类型对象泄漏给消费者。
 */
sealed interface LsiType {

    val nullability: LsiNullability

    val annotations: List<LsiAnnotation>
}
