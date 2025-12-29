import site.addzero.gradle.plugin.KotlinConventionPlugin

plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"

    id("site.addzero.gradle.plugin.intellij-core")  version "2025.12.23"
}

dependencies {
    implementation(project(":checkouts:lsi:lsi-core"))
    implementation(project(":checkouts:lsi:lsi-intellij"))
    implementation("site.addzero:tool-str:2025.12.30")

    // K2 Analysis API 通过 Kotlin 插件捆绑提供
//    intellijPlatform {
//        bundledPlugin("org.jetbrains.kotlin")
//    }
}

// 启用 K2 Analysis API 实验性功能
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaExperimentalApi",
            "-opt-in=org.jetbrains.kotlin.analysis.api.KaNonPublicApi"
        )
    }
}
