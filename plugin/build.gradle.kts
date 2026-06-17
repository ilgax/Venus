plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.21"
    id("org.jlleitschuh.gradle.ktlint")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("org.jetbrains.kotlinx.kover")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-buffer:4.1.97.Final")
    implementation(project(":common"))
    implementation(project(":backend"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.10")
}

kotlin {
    jvmToolchain(21)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName = "venus-plugin"
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val deployDir: String? = findProperty("venus.deploy.pluginDir") as? String
if (deployDir != null) {
    tasks.register<Copy>("deploy") {
        from(layout.buildDirectory.file("libs/venus-plugin-${project.version}.jar"))
        into(deployDir)
        dependsOn(tasks.build)
        doFirst {
            delete(fileTree(deployDir) { include("venus-plugin-*.jar") })
        }
    }
    tasks.build { finalizedBy("deploy") }
}

tasks.withType<Test>().configureEach {
    systemProperty("net.bytebuddy.experimental", "true")
}
