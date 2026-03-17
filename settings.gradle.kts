pluginManagement {
    plugins {
        kotlin("plugin.serialization") version "2.1.0"
    }
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "venus"

include("plugin", "mod")