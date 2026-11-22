
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
    }
}

// Record this build's production artifact even if build/libs contains other versions.
tasks.register<WriteHostedInventoryArtifact>("writeHostedInventoryArtifact") {
    artifacts.from(tasks.named("reobfJar"))
    manifest.set(layout.buildDirectory.file("hosted-inventory-artifact.txt"))
}
