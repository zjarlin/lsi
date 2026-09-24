plugins {
    id("site.addzero.buildlogic.jvm.kotlin-convention")
}

val libs = versionCatalogs.named("libs")
val lsiRootPath = project.path.substringBeforeLast(":")

dependencies {
    testImplementation(kotlin("test-junit"))
    api(project("$lsiRootPath:lsi-core"))
    implementation(project(":lib:tool-kmp:tool-str"))
}

tasks.test { useJUnit() }
