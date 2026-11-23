
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

abstract class WriteHostedInventoryArtifact : DefaultTask() {
    @get:InputFiles
    abstract val artifacts: ConfigurableFileCollection

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun write() {
        val jar = artifacts.files.single { it.isFile && it.extension == "jar" }
        val destination = manifest.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(jar.absolutePath + "\n")
        logger.lifecycle("Production JAR: {}", jar.absolutePath)
    }
}

// Record this build's production artifact even if build/libs contains other versions.
val inventoryProfile = providers.gradleProperty("inventoryProfile")
val hostedArtifactManifest = inventoryProfile.map {
    if (it == "gtnh-2.9.1") "hosted-inventory-artifact-2.9.1.txt" else "hosted-inventory-artifact.txt"
}.orElse("hosted-inventory-artifact.txt")
tasks.register<WriteHostedInventoryArtifact>("writeHostedInventoryArtifact") {
    artifacts.from(tasks.named("reobfJar"))
    manifest.set(layout.buildDirectory.file(hostedArtifactManifest))
}

if (inventoryProfile.orNull == "gtnh-2.9.1") {
    val sharedClientLibraries = layout.buildDirectory.dir("libs/gtnh-2.9.1")
    tasks.withType<Jar>().matching { it.name in setOf("jar", "reobfJar", "sourcesJar") }.configureEach {
        destinationDirectory.set(sharedClientLibraries)
    }

    tasks.register<WriteHostedInventoryArtifact>("writeSharedClientInventoryArtifact") {
        artifacts.from(tasks.named("reobfJar"))
        manifest.set(layout.buildDirectory.file("shared-client-inventory-artifact-2.9.1.txt"))
    }
}
