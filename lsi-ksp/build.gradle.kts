plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(libs.tool.str)

    // KSP API dependencies
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.20-1.0.14")

    // 测试依赖
    testImplementation(libs.junit.jupiter)
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("io.kotest:kotest-property:5.7.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


description = "LSI系统的KSP实现模块"
