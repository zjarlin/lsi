# lsi-apt

Maven 坐标：`org.babyfish.jimmer:lsi-apt`。

`lsi-apt` 将当前 APT 轮次中的 javac 符号立即冻结为不可变 LSI。`AptLsiCompilerDriver` 负责多轮 workspace、诊断和 `GeneratedArtifact` 写出，`AptLsiProcessor` 提供可复用的通用 APT 生命周期入口；领域选项和输入文档通过 `CompilerWiring` 注入。

模块只依赖 `lsi-core`、JDK APT API 和 Kotlin metadata。它不包含 Jimmer 语义，也不依赖 JavaPoet、KotlinPoet 或 KSP。
