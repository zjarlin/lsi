# 模块维护

入口和坐标见 README。APT 类型只在本模块出现，共享元数据使用 `LsiAnnotation`。注解数组必须递归保存全部元素和顺序，空数组保持空集合，嵌套注解保持 `LsiAnnotation`；不得转成首项或注解文本。

使用 JDK 17 在仓库根执行 `gradle -p release test publishToMavenLocal`。APT 回归使用真实 Java 编译器；KSP 配套用例验证同等嵌套集合结构。消费者测试仍须覆盖真实 Jimmer 重复注解。编译器和文件管理器按 use 关闭，不持有全局处理环境。
