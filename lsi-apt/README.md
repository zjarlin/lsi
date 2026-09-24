# lsi-apt

Maven 坐标：`site.addzero:lsi-apt`。依赖 lsi-core 和字符串工具，把 Java APT 类型、属性、方法及注解适配为 LSI。

```kotlin
val annotation: LsiAnnotation = AptLsiAnnotation(annotationMirror)
val nested = annotation.getAttribute("value") as List<*>
```

注解数组递归返回 List，包含全部元素且顺序稳定；嵌套注解为 LsiAnnotation，空数组为空 List。标量值继续沿用现有文本表示。适配器不负责创建编译器或关闭调用方处理环境，异常直接透出。读取应发生在 APT 处理环境有效期间。

验证：在仓库根用 JDK 17 执行 `gradle -p release :lsi-apt:test`；完整发布验证见根 README。
