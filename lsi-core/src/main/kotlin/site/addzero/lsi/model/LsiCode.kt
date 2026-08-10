package site.addzero.lsi.model

import site.addzero.lsi.type.LsiType

/**
 * 构成代码块的语言无关占位片段。
 */
sealed interface LsiCodePart {
    data class Text(val value: String) : LsiCodePart

    data class Type(
        val value: LsiType,
        val referenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
    ) : LsiCodePart

    /**
     * 指向 Kotlin 包级声明，由 KotlinPoet 负责导入和关键字转义。
     */
    data class TopLevelMember(
        val packageName: String,
        val simpleName: String,
        val extension: Boolean,
    ) : LsiCodePart {
        init {
            require(packageName == packageName.trim()) {
                "LSI top-level member package name cannot have surrounding whitespace: '$packageName'"
            }
            require(packageName.isEmpty() || packageName.isQualifiedJvmName()) {
                "LSI top-level member package name must be a qualified JVM name: '$packageName'"
            }
            require(simpleName.isJvmIdentifier()) {
                "LSI top-level member simple name must be a JVM identifier: '$simpleName'"
            }
        }
    }

    data class Name(val value: String) : LsiCodePart {
        init {
            require(value.isNotBlank()) { "LSI name cannot be blank" }
        }
    }

    data class Literal(val value: String) : LsiCodePart {
        init {
            require(value.isNotBlank()) { "LSI literal cannot be blank" }
        }
    }

    data class StringLiteral(val value: String) : LsiCodePart

    data class CharacterLiteral(val value: Char) : LsiCodePart

    /**
     * 由具体 Poet 实现补齐语句终止符和换行。
     */
    data class Statement(val value: LsiCodeBlock) : LsiCodePart

    /**
     * 由具体 Poet 实现选择块状返回或表达式函数。
     */
    data class Return(val value: LsiCodeBlock?) : LsiCodePart

    /**
     * 表达带花括号主体、可选尾部调用以及语句完成方式的结构。
     */
    data class BracedExpression(
        val completion: LsiBracedExpressionCompletion,
        val prefix: LsiCodeBlock,
        val body: LsiCodeBlock,
        val suffix: LsiCodeBlock,
    ) : LsiCodePart

    /**
     * 开始一个由具体 Poet 实现负责排版的控制流。
     */
    data class BeginControlFlow(val header: LsiCodeBlock) : LsiCodePart

    /**
     * 切换到同一控制流的下一分支。
     */
    data class NextControlFlow(val header: LsiCodeBlock) : LsiCodePart

    data object EndControlFlow : LsiCodePart

    data object NewLine : LsiCodePart

    data object Indent : LsiCodePart

    data object Unindent : LsiCodePart
}

enum class LsiBracedExpressionCompletion {
    RETURN,
    STATEMENT,
}

/**
 * 控制代码块中的类型引用是否参与文件导入缩写。
 */
enum class LsiTypeReferenceStyle {
    IMPORTED,
    FULLY_QUALIFIED,
    /**
     * 省略当前包名但保留外部类型名，例如 `BookDraft.Producer`。
     */
    SAME_PACKAGE_OUTER_QUALIFIED,
}

data class LsiCodeBlock(
    val parts: List<LsiCodePart>,
    val indentation: LsiCodeBlockIndentation = LsiCodeBlockIndentation.PLATFORM_DEFAULT,
) {
    val isEmpty: Boolean
        get() = parts.isEmpty()

    init {
        var indentation = 0
        var controlFlowDepth = 0
        parts.forEach { part ->
            when (part) {
                is LsiCodePart.BeginControlFlow -> controlFlowDepth++
                LsiCodePart.EndControlFlow -> {
                    controlFlowDepth--
                    require(controlFlowDepth >= 0) {
                        "LSI code block cannot end a control flow before it begins"
                    }
                }
                LsiCodePart.Indent -> indentation++
                is LsiCodePart.NextControlFlow -> require(controlFlowDepth > 0) {
                    "LSI code block cannot continue a control flow before it begins"
                }
                LsiCodePart.Unindent -> {
                    indentation--
                    require(indentation >= 0) {
                        "LSI code block cannot unindent before an indent"
                    }
                }
                else -> Unit
            }
        }
        require(indentation == 0) {
            "LSI code block must close every indent"
        }
        require(controlFlowDepth == 0) {
            "LSI code block must close every control flow"
        }
    }

    companion object {
        val EMPTY: LsiCodeBlock = LsiCodeBlock(emptyList())

        fun build(block: LsiCodeBuilder.() -> Unit): LsiCodeBlock {
            return LsiCodeBuilder().apply(block).build()
        }
    }
}

/**
 * 控制外围声明是否可以为多行代码块追加续行缩进。
 */
enum class LsiCodeBlockIndentation {
    PLATFORM_DEFAULT,
    EXPLICIT,
}

class LsiCodeBuilder internal constructor() {
    private val parts = mutableListOf<LsiCodePart>()
    private var indentation = LsiCodeBlockIndentation.PLATFORM_DEFAULT

    /**
     * 表示代码块已经完整描述每一层缩进，平台不得再追加外围续行缩进。
     */
    fun preserveExplicitIndentation() {
        indentation = LsiCodeBlockIndentation.EXPLICIT
    }

    fun text(value: String) {
        if (value.isNotEmpty()) {
            parts += LsiCodePart.Text(value)
        }
    }

    fun type(
        value: LsiType,
        referenceStyle: LsiTypeReferenceStyle = LsiTypeReferenceStyle.IMPORTED,
    ) {
        parts += LsiCodePart.Type(value, referenceStyle)
    }

    fun topLevelMember(
        packageName: String,
        simpleName: String,
        extension: Boolean,
    ) {
        parts += LsiCodePart.TopLevelMember(packageName, simpleName, extension)
    }

    fun name(value: String) {
        parts += LsiCodePart.Name(value)
    }

    fun literal(value: String) {
        parts += LsiCodePart.Literal(value)
    }

    fun string(value: String) {
        parts += LsiCodePart.StringLiteral(value)
    }

    fun character(value: Char) {
        parts += LsiCodePart.CharacterLiteral(value)
    }

    fun line() {
        parts += LsiCodePart.NewLine
    }

    fun statement(block: LsiCodeBuilder.() -> Unit) {
        parts += LsiCodePart.Statement(LsiCodeBlock.build(block))
    }

    fun returnValue(block: LsiCodeBuilder.() -> Unit) {
        parts += LsiCodePart.Return(LsiCodeBlock.build(block))
    }

    fun returnVoid() {
        parts += LsiCodePart.Return(null)
    }

    fun returnBracedExpression(
        prefix: LsiCodeBuilder.() -> Unit,
        body: LsiCodeBuilder.() -> Unit,
        suffix: LsiCodeBuilder.() -> Unit = {},
    ) {
        bracedExpression(LsiBracedExpressionCompletion.RETURN, prefix, body, suffix)
    }

    fun statementBracedExpression(
        prefix: LsiCodeBuilder.() -> Unit,
        body: LsiCodeBuilder.() -> Unit,
        suffix: LsiCodeBuilder.() -> Unit = {},
    ) {
        bracedExpression(LsiBracedExpressionCompletion.STATEMENT, prefix, body, suffix)
    }

    private fun bracedExpression(
        completion: LsiBracedExpressionCompletion,
        prefix: LsiCodeBuilder.() -> Unit,
        body: LsiCodeBuilder.() -> Unit,
        suffix: LsiCodeBuilder.() -> Unit,
    ) {
        parts += LsiCodePart.BracedExpression(
            completion = completion,
            prefix = LsiCodeBlock.build(prefix),
            body = LsiCodeBlock.build(body),
            suffix = LsiCodeBlock.build(suffix),
        )
    }

    fun beginControlFlow(block: LsiCodeBuilder.() -> Unit) {
        parts += LsiCodePart.BeginControlFlow(LsiCodeBlock.build(block))
    }

    fun nextControlFlow(block: LsiCodeBuilder.() -> Unit) {
        parts += LsiCodePart.NextControlFlow(LsiCodeBlock.build(block))
    }

    fun endControlFlow() {
        parts += LsiCodePart.EndControlFlow
    }

    fun indent(block: LsiCodeBuilder.() -> Unit) {
        parts += LsiCodePart.Indent
        block()
        parts += LsiCodePart.Unindent
    }

    fun add(block: LsiCodeBlock) {
        if (block.indentation == LsiCodeBlockIndentation.EXPLICIT) {
            indentation = LsiCodeBlockIndentation.EXPLICIT
        }
        parts += block.parts
    }

    fun build(): LsiCodeBlock = LsiCodeBlock(parts.toList(), indentation)
}

private fun String.isQualifiedJvmName(): Boolean {
    return split('.').all(String::isJvmIdentifier)
}
