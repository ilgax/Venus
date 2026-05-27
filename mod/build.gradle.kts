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
    implementation(project(":common"))
    testImplementation(kotlin("test"))
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

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName = "venus-mod"
}

tasks.remapJar {
    archiveBaseName = "venus-mod"
    from(zipTree(project(":common").tasks.jar.get().archiveFile))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-unchecked")
}

val deployDir: String? = findProperty("venus.deploy.modDir") as? String
if (deployDir != null) {
    tasks.register<Copy>("deploy") {
        from(layout.buildDirectory.file("libs/venus-mod-${project.version}.jar"))
        into(deployDir)
        dependsOn(tasks.remapJar)
    }
    tasks.remapJar { finalizedBy("deploy") }
}

loom {
    runConfigs.all {
        ideConfigGenerated(true)
        runDir = "run"
    }
}