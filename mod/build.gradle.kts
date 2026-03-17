plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
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

sourceSets {
    main {
        java {
            srcDir("src/main/java")
        }
    }
}

tasks.jar {
    archiveBaseName = "venus-mod"
}

tasks.remapJar {
    archiveBaseName = "venus-mod"
    finalizedBy("deploy")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-unchecked")
}

tasks.register<Copy>("deploy") {
    from(layout.buildDirectory.file("libs/venus-mod-${project.version}.jar"))
    into("C:\\Users\\ilgax\\AppData\\Roaming\\ModrinthApp\\profiles\\Venus\\mods")
    dependsOn(tasks.remapJar)
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "run"
    }
}