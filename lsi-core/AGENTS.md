# 模块维护

依赖 `site.addzero:tool-str-jvm`。对外 API 包括 `LsiClass`、`LsiField`、`LsiType` 和注解抽象；`LsiField.isComputed` 是语言中立的计算属性事实，默认 false。不要在此引入 KSP、APT 或 Jimmer 类型。消费者可用 `fields.filterNot { it.isComputed }` 筛选存储属性。

仓库根 README 为使用入口。在仓库根使用 JDK 17、Gradle 9.1 执行 `gradle -p release :lsi-core:test`；完整验证执行 `gradle -p release test publishToMavenLocal`。发布入口在 `release/`，宿主构建入口在本模块 `build.gradle.kts`，依赖调整需保持二者一致。
