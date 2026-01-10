plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("site.addzero.gradle.plugin.kotlin-convention") version "+"
    id("site.addzero.gradle.plugin.intellij-core")  version "2025.12.23"

}

dependencies {
    implementation("site.addzero:lsi-core:2026.01.11")
    implementation("site.addzero:lsi-intellij:2026.01.11")
    api("site.addzero:lsi-kt2:2026.01.11")  // K2 Analysis API
    api("site.addzero:lsi-psi:2026.01.11")
}

