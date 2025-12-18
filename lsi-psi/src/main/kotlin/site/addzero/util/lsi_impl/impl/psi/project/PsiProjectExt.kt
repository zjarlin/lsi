package site.addzero.util.lsi_impl.impl.psi.project

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import site.addzero.util.lsi_impl.impl.intellij.project.toEditor
import site.addzero.util.lsi_impl.impl.intellij.project.toVirtualFile
import site.addzero.util.lsi_impl.impl.psi.model.PsiCtx
import site.addzero.util.lsi_impl.impl.psi.psifile.toPsiClass

/**
 * @deprecated 此方法违反单一职责原则，职责过多，且未实现完整功能
 * 请使用 `project.lsiContext()` 替代，它提供了基于LSI抽象层的语言无关上下文
 *
 * @see site.addzero.util.lsi_impl.impl.intellij.context.context.lsiContext
 */
@Deprecated(
    message = "使用 project.lsiContext() 替代此方法。PsiCtx 直接暴露了 PSI 类型，违反了 LSI 抽象原则。",
    level = DeprecationLevel.WARNING
)
fun Project.allpsiCtx(): PsiCtx {
    // 返回空上下文，因为此方法已弃用
    return PsiCtx(
        editor = null,
        psiClass = null,
        psiFile = null,
        virtualFile = null,
        any = null
    )
}

/**
 * @deprecated 此方法违反单一职责原则，职责过多
 *
 * 问题：
 * 1. 混合了编辑器状态、文件系统、PSI元素等多个关注点
 * 2. 直接暴露PSI类型（PsiClass, PsiFile），违反LSI抽象层原则
 * 3. 返回的PsiCtx包含过多细节，难以维护
 *
 * 替代方案：
 * - 使用 `project.lsiContext()` 获取语言无关的类上下文
 * - 使用 `project.toEditor()` 获取编辑器
 * - 使用 `project.toVirtualFile()` 获取虚拟文件
 *
 * @see site.addzero.util.lsi_impl.impl.intellij.context.context.lsiContext
 * @see site.addzero.util.lsi_impl.impl.intellij.project.toEditor
 * @see site.addzero.util.lsi_impl.impl.intellij.project.toVirtualFile
 */
@Deprecated(
    message = "使用 project.lsiContext() 替代此方法。PsiCtx 直接暴露了 PSI 类型，违反了 LSI 抽象原则。",
    level = DeprecationLevel.WARNING
)
fun Project.psiCtx(): PsiCtx {
    val editor = this.toEditor()
    val virtualFile = toVirtualFile()
    val psiFile = virtualFile?.toPsiFile(this)
    val psiClass = psiFile?.toPsiClass()
    val any = if (psiFile is PsiJavaFile) {
        // 一个文件中可能会定义有多个Class，因此返回的是一个数组
        val classes: Array<PsiClass> = psiFile.getClasses()
        classes
    } else {
        null
    }
    return PsiCtx(
        editor = editor,
        psiClass = psiClass,
        psiFile = psiFile,
        virtualFile = virtualFile,
        any
    )

}



