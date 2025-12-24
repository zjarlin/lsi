plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2025.12.22")

}

description = "Lsi jimmer元数据到数据库ddl解析"
