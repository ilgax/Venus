plugins {
    kotlin("jvm") version "2.3.21" apply false
}

allprojects {
    group = "dev.ilgax.venus"
    version = "0.2.1"

    val localFile = rootProject.file("gradle-local.properties")
    if (localFile.exists()) {
        java.util.Properties().apply {
            localFile.inputStream().use { load(it) }
        }.forEach { (k, v) ->
            extra.set(k.toString(), v)
        }
    }
}

