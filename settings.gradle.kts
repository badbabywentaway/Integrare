pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "Integrare"

// Runnable demo plugin exercising the library's own functionality end to end,
// against whichever custom item provider is installed. Not published - just
// for manual testing of the library itself.
include("demo-plugin")
