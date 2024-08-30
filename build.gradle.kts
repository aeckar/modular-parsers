plugins {
    alias(libs.plugins.dokkatoo.html)
    id("root.publication")

    // Apply same plugin versions in all submodules
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
}

dependencies {
    dokkatoo(project(":core"))
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
    }
}