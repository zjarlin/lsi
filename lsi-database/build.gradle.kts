plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2025.12.09")

}

description = "Lsi数据库解析"
