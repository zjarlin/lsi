plugins {
   alias(libs.plugins.site.addzero.gradle.plugin.kotlin.convention)
}

dependencies {
    api(project(":checkouts:lsi:lsi-core"))
    api(project(":checkouts:lsi:lsi-ksp"))
    compileOnly(libs.kotlinpoet)
    compileOnly(libs.kotlinpoet.ksp)
}

description = "LSI 的 Jimmer 语义扩展层，提供 LsiClass/LsiField 的 ORM 语义扩展函数及 EntityMetadata 转换器"
