
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

abstract class WriteHostedInventoryArtifact : DefaultTask() {
    @get:InputFiles
    abstract val artifacts: ConfigurableFileCollection

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val receipt: RegularFileProperty

    @get:Input
    abstract val baseCommit: Property<String>

    @TaskAction
    fun write() {
        val jar = artifacts.files.single { it.isFile && it.extension == "jar" }
        val destination = manifest.get().asFile
        destination.parentFile.mkdirs()
        val metadata = ZipFile(jar).use { zip ->
            val properties = Properties()
            zip.getInputStream(zip.getEntry("integration-profiles.properties")).use(properties::load)
            val info = zip.getInputStream(zip.getEntry("mcmod.info")).bufferedReader().use {
                JsonSlurper().parse(it) as Map<*, *>
            }
            val mods = info["modList"] as List<*>
            val mod = mods.filterIsInstance<Map<*, *>>().single { it["modid"] == "dynamicinventory" }
            mapOf("artifact" to jar.name, "profile" to properties.getProperty("default.profile"),
                "modVersion" to requireNotNull(mod["version"]), "baseCommit" to baseCommit.get(),
                "sha256" to MessageDigest.getInstance("SHA-256").digest(jar.readBytes()).joinToString("") { "%02x".format(it) })
        }
        receipt.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
        destination.writeText(jar.absolutePath + "\n")
        logger.lifecycle("Production JAR: {}", jar.absolutePath)
    }
}

// Record this build's production artifact even if build/libs contains other versions.
val inventoryProfile = providers.gradleProperty("inventoryProfile")
tasks.withType<WriteHostedInventoryArtifact>().configureEach {
    baseCommit.set(providers.exec {
        commandLine("git", "rev-parse", "--verify", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } })
}
val hostedArtifactManifest = inventoryProfile.map {
    if (it == "gtnh-2.9.1") "hosted-inventory-artifact-2.9.1.txt" else "hosted-inventory-artifact.txt"
}.orElse("hosted-inventory-artifact.txt")
tasks.register<WriteHostedInventoryArtifact>("writeHostedInventoryArtifact") {
    artifacts.from(tasks.named("reobfJar"))
    manifest.set(layout.buildDirectory.file(hostedArtifactManifest))
    receipt.set(layout.buildDirectory.file(hostedArtifactManifest.map { it.removeSuffix(".txt") + ".json" }))
}

if (inventoryProfile.orNull == "gtnh-2.9.1") {
    val sharedClientLibraries = layout.buildDirectory.dir("libs/gtnh-2.9.1")
    tasks.withType<Jar>().matching { it.name in setOf("jar", "reobfJar", "sourcesJar") }.configureEach {
        destinationDirectory.set(sharedClientLibraries)
    }

    tasks.register<WriteHostedInventoryArtifact>("writeSharedClientInventoryArtifact") {
        artifacts.from(tasks.named("reobfJar"))
        manifest.set(layout.buildDirectory.file("shared-client-inventory-artifact-2.9.1.txt"))
        receipt.set(layout.buildDirectory.file("shared-client-inventory-artifact-2.9.1.json"))
    }
}
