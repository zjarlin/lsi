plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    api(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2025.12.09")
}
