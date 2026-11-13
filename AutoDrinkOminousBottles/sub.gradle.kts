import gg.essential.gradle.multiversion.Platform

plugins {
    java
    id("gg.essential.loom")
    id("gg.essential.defaults.repo")
}

val platform = Platform.of(project)
// The toolkit's loom defaults trip over 26.x (unobfuscated) in this loom version, so 26.x is set up by hand.
if (!platform.isUnobfuscated) apply(plugin = "gg.essential.defaults.loom")
val era = property("adob.era").toString()
val hook = property("adob.hook").toString()
val res = property("adob.res").toString()
val modVersion = rootProject.property("mod_version").toString()
val range = property("adob.range").toString()

version = modVersion
base.archivesName.set("auto-drink-ominous-bottles-${property("adob.label")}")

sourceSets.main {
    java.setSrcDirs(listOf(
        rootProject.file("src/core/java"),
        rootProject.file("src/era-$era/java"),
        rootProject.file("src/hook-$hook/java"),
    ))
    resources.setSrcDirs(listOf(rootProject.file("src/res-$res/resources"), rootProject.file("src/common/resources")))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(platform.javaVersion.majorVersion.toInt())
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:-options")
}

repositories {
    maven("https://maven-central.storage-download.googleapis.com/maven2/")
    maven("https://maven.fabricmc.net/")
}

if (platform.isUnobfuscated && platform.isNeoForge) {
    repositories { maven("https://maven.neoforged.net/releases/") }
}

dependencies {
    if (platform.isUnobfuscated) {
        "minecraft"("com.mojang:minecraft:${platform.mcVersionStr}")
        when {
            platform.isFabric -> "implementation"(property("essential.defaults.loom.fabric-loader").toString())
            platform.isForge -> "forge"("net.minecraftforge:forge:${property("adob.forge")}")
            platform.isNeoForge -> "neoForge"("net.neoforged:neoforge:${property("adob.neoforge")}")
        }
    }
    if (platform.isFabric) {
        val fabricApi = property("adob.fabricApi").toString()
        if (platform.isUnobfuscated) {
            "implementation"("net.fabricmc.fabric-api:fabric-api:$fabricApi")
        } else {
            "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApi")
        }
    }
}

tasks.processResources {
    val props = mapOf("version" to modVersion, "range" to range, "mc" to platform.mcVersionStr)
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "mcmod.info")) {
        expand(props)
    }
}

tasks.register("printRepos") {
    doLast { repositories.forEach { r -> println("REPO ${r.name} ${(r as? MavenArtifactRepository)?.url}") } }
}

// The MIT license asks for its notice in every copy, so each jar carries LICENSE (shumsic's original notice).
tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}
