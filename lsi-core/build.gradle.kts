import org.babyfish.jimmer.build.VerifyCompilerArchitecture
import org.babyfish.jimmer.build.VerifyLsiPublicModel

plugins {
    `kotlin-publish-convention`
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
}

val verifyLsiArchitecture by tasks.registering(VerifyCompilerArchitecture::class) {
    group = "verification"
    description = "验证 LSI core 不依赖 Jimmer 语义、编译器平台与渲染器"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    })
    allowedImportPrefixes.set(
        setOf(
            "java.",
            "kotlin.",
            "site.addzero.lsi.",
        )
    )
    additionalForbiddenNamespaces.set(
        setOf(
            "org.babyfish.jimmer.",
            "site.addzero.lsi.apt.",
            "site.addzero.lsi.jimmer.",
            "site.addzero.lsi.ksp.",
            "site.addzero.lsi.poet.",
        )
    )
    captureDependencies(
        configurations = configurations,
        allowedDirectIds = setOf("module:org.jetbrains.kotlin:kotlin-stdlib"),
        allowedResolvedProjectIds = emptySet(),
        forbiddenResolvedModulePrefixes = FORBIDDEN_LSI_MODULE_PREFIXES,
    )
}

val verifyLsiPublicModel by tasks.registering(VerifyLsiPublicModel::class) {
    group = "verification"
    description = "验证 LSI 公共结构模型保持接口化且不恢复旧重复声明"

    baseDirectory.set(layout.projectDirectory)
    sourceFiles.from(fileTree("src/main") {
        include("**/*.kt")
    })
    requiredInterfaces.set(
        setOf(
            "site.addzero.lsi.anno.LsiAnnotation",
            "site.addzero.lsi.clazz.LsiClass",
            "site.addzero.lsi.clazz.LsiEnumEntry",
            "site.addzero.lsi.field.LsiField",
            "site.addzero.lsi.field.LsiProperty",
            "site.addzero.lsi.file.LsiFile",
            "site.addzero.lsi.method.LsiConstructor",
            "site.addzero.lsi.method.LsiMethod",
            "site.addzero.lsi.method.LsiParameter",
            "site.addzero.lsi.type.LsiType",
        )
    )
    forbiddenLegacyNames.set(
        setOf(
            "LsiFunction",
            "LsiTypeHierarchyEntry",
            "LsiTypeName",
        )
    )
}

tasks.named("check") {
    dependsOn(verifyLsiArchitecture, verifyLsiPublicModel)
}

private val FORBIDDEN_LSI_MODULE_PREFIXES = setOf(
    "module:com.google.devtools.ksp:",
    "module:com.squareup:javapoet",
    "module:com.squareup:kotlinpoet",
    "module:com.sun:tools",
    "module:jdk.tools:jdk.tools",
    "module:org.babyfish.jimmer:",
    "module:org.jetbrains.kotlin:kotlin-annotation-processing",
    "module:org.jetbrains.kotlin:kotlin-compiler",
)
