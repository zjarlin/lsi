plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("site.addzero.gradle.plugin.intellij-core") version "+"

}

dependencies {
    implementation(project(":checkouts:metaprogramming-lsi:lsi-core"))
    implementation(project(":checkouts:metaprogramming-lsi:lsi-intellij"))
    api(project(":checkouts:metaprogramming-lsi:lsi-kt2"))  // K2 Analysis API
    api(project(":checkouts:metaprogramming-lsi:lsi-psi"))
}

