# 模块维护

依赖 `lsi-core`、KSP API、tool-str，KotlinPoet 仅在边界使用。入口 `KSClassDeclaration.toLsiClass(resolver)` / `KspLsiClass(resolver, declaration)` 产生 LSI 元数据。`KspLsiField.isComputed` 通过非抽象且无 backing field 判断，不按 getter 正文复杂度、属性名或 Jimmer 注解猜测。

只在有效的 KSP processing round 读取符号；跨轮次或 finish 使用完整快照。回归测试必须覆盖源码、已编译父接口、抽象属性和有 backing field 的自定义 getter；编译错误直接失败。

仓库根 README 为使用入口。在仓库根使用 JDK 17、Gradle 9.1 执行 `gradle -p release :lsi-ksp:test`；完整验证执行 `gradle -p release test publishToMavenLocal`。发布入口在 `release/`，宿主构建入口在本模块 `build.gradle.kts`，依赖调整需保持二者一致。
