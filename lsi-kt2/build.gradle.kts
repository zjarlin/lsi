plugins {
    id("site.addzero.gradle.plugin.intellij-core") version "+"
}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    implementation(libs.tool.str)

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
