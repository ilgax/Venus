pluginManagement {
    plugins {
        kotlin("plugin.serialization") version "2.3.21"
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    }
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "venus"

include("common", "plugin", "mod")