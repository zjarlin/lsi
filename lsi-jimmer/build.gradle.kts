plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

val libs = versionCatalogs.named("libs")
val lsiRootPath = project.path.substringBeforeLast(":")

dependencies {
    api(project("$lsiRootPath:lsi-core"))
    implementation(project("$lsiRootPath:lsi-ksp"))
    compileOnly(libs.findLibrary("com-squareup-kotlinpoet").get())
}

description = "LSI 的 Jimmer 语义扩展层，提供 LsiClass/LsiField 的 ORM 语义扩展函数及 EntityMetadata 转换器"
