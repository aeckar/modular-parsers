import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants.RESOURCE_LOADERS
import org.apache.velocity.runtime.resource.loader.FileResourceLoader
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// TODO LATER change names

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
        val commonMain by getting {
            kotlin.srcDirs("build/generated/sources/commonMain/kotlin")
        }

        val commonTest by getting {
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

    doLast {
        val engine = VelocityEngine().apply {
            setProperty(RESOURCE_LOADERS, "file")
            setProperty("resource.loader.file.class", FileResourceLoader::class.java.name)
            setProperty("resource.loader.file.path", "${projectDir}/src/commonMain/resources")
            setProperty("resource.loader.cache", true)
            init()
        }

        val typeSafePackage = "io/github/aeckar/parsing/typesafe"
        val typeSafeTemplatePath = "$typeSafePackage/TypeSafe.kt.vm"
        val identifiers = listOf("Junction", "Sequence")

        println("Generating type-safe declarations from resource $typeSafeTemplatePath")
        for (identifier in identifiers) {
            for (n in 2..(properties["generated.typesafe.count"] as String).toInt()) {
                val context = VelocityContext().apply {
                    put("identifier", identifier)
                    put("n", n)
                    put("ordinals", ordinals)
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