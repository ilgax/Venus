plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    implementation(project(":common"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.10")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    systemProperty("net.bytebuddy.experimental", "true")
}
