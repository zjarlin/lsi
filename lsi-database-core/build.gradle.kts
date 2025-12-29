plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
    id("koin-convention")
}

dependencies {
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2025.12.30")
    implementation("site.addzero:tool-database-model:2025.12.23")

}

description = "Lsi jimmer元数据到数据库ddl解析"
