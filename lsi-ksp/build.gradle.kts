plugins {
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
}

dependencies {
    api(project(":checkouts:lsi:lsi-core"))
    implementation("site.addzero:tool-str:2025.12.22")
    // KSP API dependencies
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.4")

//    // 测试依赖
//    testImplementation(libs.junit.jupiter)
//    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
//    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
//    testImplementation("io.kotest:kotest-property:5.7.2")
//    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


description = "LSI系统的KSP实现模块"
