import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

group = "site.addzero"
version = providers.gradleProperty("releaseVersion").get()
repositories { mavenCentral() }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.ENABLE)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
dependencies {
    if (project.name != "lsi-core") {
        api(project(":lsi-core"))
    }
    if (project.name != "lsi-jimmer") {
        implementation("site.addzero:tool-str-jvm:2026.08.26")
    }
    if (project.name == "lsi-ksp") {
        implementation("com.google.devtools.ksp:symbol-processing-api:2.3.9")
        compileOnly("com.squareup:kotlinpoet-ksp:2.2.0")
        testImplementation("dev.zacsweers.kctfork:ksp:0.7.1") {
            exclude(module = "symbol-processing-api")
        }
    }
    if (project.name == "lsi-jimmer") {
        implementation(project(":lsi-ksp"))
    }
    if (project.name in setOf("lsi-ksp", "lsi-jimmer")) {
        compileOnly("com.squareup:kotlinpoet-jvm:2.2.0")
    }
    testImplementation(kotlin("test-junit"))
}
tasks.test {
    useJUnit()
    maxHeapSize = "2g"
}
mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = true))
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set(project.name)
        description.set("Language-neutral source metadata: ${project.name}")
        url.set("https://github.com/zjarlin/lsi")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("zjarlin")
                name.set("zjarlin")
                email.set("zjarlin@outlook.com")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/zjarlin/lsi.git")
            url.set("https://github.com/zjarlin/lsi")
        }
    }
}
