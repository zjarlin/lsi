plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(libs.tool.str)

}

