pluginManagement {
    repositories {
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases/")
        // Maven Central rate-limits this machine, so use Google's mirror of it first.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "adob"

// Every build target: <minecraft>-<loader>. Each has its own folder under versions/
// with a gradle.properties saying which code to use. They all share sub.gradle.kts.
val targets = (System.getenv("ADOB_TARGETS") ?: file("versions").listFiles()!!
    .filter { it.isDirectory && file("versions/${it.name}/gradle.properties").exists() }
    .joinToString(",") { it.name })
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

for (target in targets) {
    include(":$target")
    project(":$target").apply {
        projectDir = file("versions/$target")
        buildFileName = "../../sub.gradle.kts"
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
}
