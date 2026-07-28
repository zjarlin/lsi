plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

val libs = versionCatalogs.named("libs")

dependencies {
    implementation(project(":lib:tool-kmp:tool-str"))
}

description = "语言无关的不完备抽象层"
