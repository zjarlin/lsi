# lsi-ksp

Maven 坐标：`org.babyfish.jimmer:lsi-ksp`。

`lsi-ksp` 将当前 KSP 轮次中的有效符号立即冻结为不可变 LSI。`KspLsiCompilerDriver` 负责多轮 workspace、文件作用域 defer、诊断和 `GeneratedArtifact` 写出，`KspLsiProcessorProvider` 提供可复用的通用 KSP 生命周期入口；领域选项和输入文档通过 `CompilerWiring` 注入。

模块只依赖 `lsi-core` 与 KSP API。它不包含 Jimmer 语义，也不依赖 JavaPoet 或 KotlinPoet。
