plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

val libs = versionCatalogs.named("libs")
val lsiRootPath = project.path.substringBeforeLast(":")

dependencies {
    api(project("$lsiRootPath:lsi-core"))
    implementation(project(":lib:tool-kmp:tool-str"))
    implementation(libs.findLibrary("com-google-devtools-ksp-symbol-processing-api").get())

    compileOnly(libs.findLibrary("com-squareup-kotlinpoet").get())
    compileOnly(libs.findLibrary("com-squareup-kotlinpoet-ksp").get())
}

description = "LSI系统的KSP实现模块"
