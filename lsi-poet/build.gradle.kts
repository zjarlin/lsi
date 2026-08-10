import org.babyfish.jimmer.build.VerifyCompilerArchitecture

plugins {
    `kotlin-publish-convention`
}

dependencies {
    api(projects.lsiCore)

    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
}

val verifyLsiPoetArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 lsi-poet 只保留平台无关 renderer SPI"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    })
    expectedRelativePaths.set(
        setOf("src/main/kotlin/site/addzero/lsi/poet/LsiPoetRenderer.kt")
    )
    allowedImportPrefixes.set(
        setOf(
            "kotlin.",
            "site.addzero.lsi.codegen.",
        )
    )
    additionalForbiddenNamespaces.set(
        setOf(
            "com.google.devtools.ksp.",
            "com.squareup.javapoet.",
            "com.squareup.kotlinpoet.",
            "org.babyfish.jimmer.",
        )
    )
    captureDependencies(
        configurations = configurations,
        allowedDirectIds = setOf(
            "module:org.jetbrains.kotlin:kotlin-stdlib",
            "project:lsi-core",
        ),
        allowedResolvedProjectIds = setOf("project:lsi-core"),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_POET_MODULE_PREFIXES,
        allowedResolvedModuleIds = setOf("module:org.babyfish.jimmer:lsi-core"),
    )
}

tasks.named("check") {
    dependsOn(verifyLsiPoetArchitecture)
}

private val FORBIDDEN_LSI_POET_MODULE_PREFIXES = setOf(
    "module:com.google.devtools.ksp:",
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
