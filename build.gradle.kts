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

dokkatoo {
    moduleName = "Modular Parsers"

    dokkatooSourceSets.configureEach {
        val x = this@configureEach
        x.apiVersion
    }
}

buildscript {
    dependencies {
        classpath("org.jetbrains.dokka:dokka-base:1.9.20")
    }
}