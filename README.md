# LSI

语言无关的源码与符号元数据抽象层，发布坐标为 `site.addzero:lsi-core`、`lsi-ksp`、`lsi-apt`、`lsi-jimmer`。

`LsiField.isComputed` 表示没有存储字段、由具体 getter 实现的计算属性。KSP 同时检查 getter 是否抽象以及 `hasBackingField`：抽象接口属性、构造参数属性和使用 `field` 的 getter 均不是计算属性。该元数据不依赖 Jimmer 注解；简单表达式和复杂代码块使用相同规则，继承和依赖 JAR 中的属性也适用。

```kotlin
val persistentFields = lsiClass.fields.filterNot { it.isComputed }
```

Java 字段及未提供计算语义的适配器默认返回 `false`。跨 KSP 轮次保存元数据时，必须同步保存 `isComputed`，不能在 `finish` 中重新读取失效的符号。

仓库仍可作为宿主 Gradle 多模块工程的子模块使用。独立验证和发布入口为 `release/`，直接编译本仓库各模块的源文件，不依赖旧宿主中的副本。使用 Gradle 9.1 和 JDK 17：

```sh
gradle -p release test publishToMavenLocal
gradle -p release tasks --all
gradle -p release publishToMavenCentral
```

发布版本由 `release/gradle.properties` 的 `releaseVersion` 确定；已占用的日期必须顺延，不能覆盖。发布凭据和签名配置通过 Gradle 用户属性或环境提供，不写入仓库。
