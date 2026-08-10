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
            "site.addzero.lsi.anno.LsiAnnotationValue",
            "site.addzero.lsi.anno.LsiSourceAnnotationArgument",
            "site.addzero.lsi.clazz.LsiClass",
            "site.addzero.lsi.clazz.LsiEnumEntry",
            "site.addzero.lsi.field.LsiField",
            "site.addzero.lsi.field.LsiProperty",
            "site.addzero.lsi.file.LsiFile",
            "site.addzero.lsi.method.LsiConstructor",
            "site.addzero.lsi.method.LsiMethod",
            "site.addzero.lsi.method.LsiParameter",
            "site.addzero.lsi.model.LsiAccessor",
            "site.addzero.lsi.model.LsiAnnotationMember",
            "site.addzero.lsi.model.LsiAnnotationScope",
            "site.addzero.lsi.model.LsiDeclaration",
            "site.addzero.lsi.model.LsiFileAnnotationScope",
            "site.addzero.lsi.model.LsiInitializerBlock",
            "site.addzero.lsi.model.LsiMember",
            "site.addzero.lsi.model.LsiOverride",
            "site.addzero.lsi.model.LsiPackageAnnotationScope",
            "site.addzero.lsi.type.LsiArrayType",
            "site.addzero.lsi.type.LsiDeclaredType",
            "site.addzero.lsi.type.LsiFunctionType",
            "site.addzero.lsi.type.LsiPrimitiveType",
            "site.addzero.lsi.type.LsiType",
            "site.addzero.lsi.type.LsiTypeArgument",
            "site.addzero.lsi.type.LsiTypeParameter",
            "site.addzero.lsi.type.LsiTypeParameterRef",
            "site.addzero.lsi.type.LsiUnresolvedType",
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
