# lsi-poet

`lsi-poet` 只定义将 LSI 源码产物交给具体渲染后端的薄 SPI，不再拥有独立的文件、类型、字段、函数或注解模型。语言无关源码模型统一位于 `lsi-core` 的 `site.addzero.lsi.model` 与 `site.addzero.lsi.codegen`。

坐标：`org.babyfish.jimmer:lsi-poet`

Java 和 Kotlin 的源码落地分别由 `lsi-poet-javapoet` 与 `lsi-poet-kotlinpoet` 完成。中立 `LsiPoetRenderer` 接收 `LsiSourceArtifact` 并返回 `GeneratedArtifact`；JavaPoet/KotlinPoet 依赖只允许存在于对应适配器模块。

每个 `LsiSourceArtifact` 必须携带文件全部类型引用对应的 `LsiTypeName`。该模型显式保存包名和嵌套类型名，不按字符大小写猜测边界；已冻结声明由 `LsiWorkspace.toLsiTypeNames` 精确解析，尚未进入 workspace 的生成类型由调用方作为 `additional` 显式提供。
