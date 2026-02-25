plugins {
    alias(libs.plugins.site.addzero.gradle.plugin.kotlin.convention)
}

dependencies {

    api(project(":checkouts:lsi:lsi-core"))
//    api(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2026.01.20")
}
