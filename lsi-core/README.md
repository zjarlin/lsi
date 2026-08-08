# LSI Core

Maven 坐标：`org.babyfish.jimmer:lsi-core`。

`lsi-core` 提供语言无关符号、类型、注解、诊断、生成制品，以及多轮编译的 feature、session、输入文档和 wiring 协议。APT、KSP 等平台驱动只依赖这些不可变协议，领域编译器通过 `CompilerWiring` 注入前端选项和输入文档来源。

该模块不得依赖 APT、KSP、JavaPoet、KotlinPoet 或 Jimmer 领域语义。
