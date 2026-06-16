plugins {
    kotlin("jvm") version "2.3.21" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

allprojects {
    group = "dev.ilgax.venus"
    version = "0.2.2"

    repositories {
        mavenCentral()
    }

    val localFile = rootProject.file("gradle-local.properties")
    if (localFile.exists()) {
        java.util.Properties().apply {
            localFile.inputStream().use { load(it) }
        }.forEach { (k, v) ->
            extra.set(k.toString(), v)
        }
    }
}

dependencies {
    kover(project(":common"))
    kover(project(":backend"))
    kover(project(":mod"))
    kover(project(":plugin"))
}
