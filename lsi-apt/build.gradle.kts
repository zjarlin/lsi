plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

val libs = versionCatalogs.named("libs")
val lsiRootPath = project.path.substringBeforeLast(":")

dependencies {
    api(project("$lsiRootPath:lsi-core"))
    implementation(project(":lib:tool-kmp:tool-str"))
}
