plugins {
    kotlin("jvm") version "2.3.10" apply false
}

allprojects {
    group = "dev.xcyn.venus"
    version = "0.2.0-SNAPSHOT"

    val localFile = rootProject.file("gradle-local.properties")
    if (localFile.exists()) {
        java.util.Properties().apply {
            localFile.inputStream().use { load(it) }
        }.forEach { k, v ->
            extra.set(k.toString(), v)
        }
    }
}

