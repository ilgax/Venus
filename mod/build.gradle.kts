plugins {
    kotlin("jvm")
    id("fabric-loom") version "1.15-SNAPSHOT"
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

val minecraftVersion = "1.21.11"
val fabricLoaderVersion = "0.18.4"
val fabricApiVersion = "0.141.3+1.21.11"
val fabricLanguageKotlinVersion = "1.13.0+kotlin.2.1.0"

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricLanguageKotlinVersion")
}

kotlin {
    jvmToolchain(25)
}

tasks.jar {
    archiveBaseName = "venus-mod"
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "run"
    }
}