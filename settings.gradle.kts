pluginManagement {
    includeBuild("convention-plugins")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "modular-parsers"

include(":core")
include(":antlr4-interop")
include("primitives")
