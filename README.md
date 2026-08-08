# LSI

语言无关的编译器符号、类型、注解、诊断、生成制品与源码渲染抽象层。

## 模块

- `lsi-core`：不可变符号模型、类型系统、诊断与生成制品协议。
- `lsi-apt`：将当前 javac APT 轮次立即冻结为 LSI。
- `lsi-ksp`：将当前 KSP 轮次立即冻结为 LSI。
- `lsi-poet`：不依赖具体 Poet 实现的源码模型。
- `lsi-poet-javapoet`：JavaPoet 边界渲染器。
- `lsi-poet-kotlinpoet`：KotlinPoet 边界渲染器。
- `lsi-jimmer`：Jimmer 的 Immutable、DTO、Client、Error 等领域扩展。

## 集成

该仓库作为宿主 Gradle 多模块工程的 Git submodule 引入。宿主工程负责提供 Kotlin、依赖版本、发布约定和架构校验任务，并把上述目录映射为同名 Gradle project。

Jimmer 的参考接入路径为 `project/lib/lsi`。通用模块禁止依赖 Jimmer、APT/KSP 交叉平台 API 或具体 Poet 实现；所有 Jimmer 领域语义集中在 `lsi-jimmer`，JavaPoet/KotlinPoet 只存在于对应边界模块。
