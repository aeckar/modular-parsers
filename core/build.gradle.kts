import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants.RESOURCE_LOADERS
import org.apache.velocity.runtime.resource.loader.FileResourceLoader
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val ordinals = listOf(
    "first",    "second",
    "third",    "fourth",
    "fifth",    "sixth",
    "seventh",  "eighth",
    "ninth",    "tenth",
    "eleventh", "twelfth"
)

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("module.publication")
}

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.apache.velocity:velocity-engine-core:2.3")
    }
}

kotlin {
    explicitApi()

    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()

    sourceSets {
        commonMain {
            kotlin.srcDirs("build/generated/sources/commonMain/kotlin")
            dependencies {
                implementation(libs.kotlinx.io)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.kotlin.logging)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    namespace = "io.github.aeckar.parsing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

tasks.register("generateTypeSafe") {
    group = "build"
    description = "Generates type-safe symbols, tokens, token extensions, and tuples"

    val resourcesPath = "${projectDir}/src/commonMain/resources"
    val typeSafePackage = "io/github/aeckar/parsing/typesafe"
    val typeSafeTemplatePath = "$typeSafePackage/TypeSafeDeclarations.kt.vm"

    inputs.file("$resourcesPath/$typeSafeTemplatePath")

    doLast {
        val engine = VelocityEngine().apply {
            setProperty(RESOURCE_LOADERS, "file,classpath")
            setProperty("resource.loader.file.class", FileResourceLoader::class.java.name)
            setProperty("resource.loader.file.path", resourcesPath)
            setProperty("resource.loader.cache", true)
            init()
        }
        val identifiers = listOf("Junction", "Sequence")

        println("Generating type-safe declarations from template $typeSafeTemplatePath")
        for (identifier in identifiers) {
            val typeSafeCount = (properties["generated.typesafe.count"] as String).toInt()
            for (n in 2..typeSafeCount) {
                val context = VelocityContext().apply {
                    put("identifier", identifier)
                    put("n", n)
                    put("ordinals", ordinals)
                    put("typeSafeCount", typeSafeCount)
                    put("capitalizeFirstChar") { input: String -> input.replaceFirstChar(Char::uppercaseChar) }
                }
                val outputFile = file("build/generated/sources/commonMain/kotlin/$typeSafePackage/$identifier$n.kt")
                outputFile.parentFile.mkdirs()
                outputFile.bufferedWriter().use { writer ->
                    engine.getTemplate(typeSafeTemplatePath).merge(context, writer)
                }
                println("File generated at ${outputFile.absolutePath}")
            }
        }
        println("All type-safe declarations generated")
    }
}