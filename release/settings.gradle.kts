pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "lsi-release"
listOf("lsi-core", "lsi-ksp", "lsi-apt", "lsi-jimmer").forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("../$name")
    project(":$name").buildFileName = "../release/module.gradle.kts"
}
