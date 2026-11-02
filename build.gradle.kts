plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.project}"
base.archivesName = property("mod.id") as String

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    maven("https://repo.polyfrost.org/releases") { name = "Polyfrost" }
    google()
    maven("https://maven.terraformersmc.com/releases") { name = "TerraformersMC" }
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
    modImplementation("maven.modrinth:flashback:${property("deps.flashback")}")
    compileOnly("org.jspecify:jspecify:1.0.0")
    modImplementation("org.polyfrost.oneconfig:${sc.current.project}-fabric:${property("deps.oneconfig")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    accessWidenerPath = sc.process(
        rootProject.file("src/main/resources/playback.accesswidener"),
        "build/processed.accesswidener"
    )

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

val mixinClasses = buildList {
    addAll(listOf("MixinClientPacketListener", "MixinGameRenderer", "MixinGuiGraphicsExtractor", "MixinHud", "MixinInventoryScreen",
        "MixinMinecraft", "MixinPauseScreen", "MixinPlayer", "MixinSoundManager"))
    addAll(listOf("AsyncReplaySaverAccessor", "MixinFlashback", "MixinRecorder", "MixinReplayGamePacketHandler").map { "flashback.$it" })
    if (sc.current.parsed < "1.21.6") add("MixinFont")
    if (sc.current.parsed >= "26.1") add("MixinCamera")
    if (sc.current.parsed >= "26.2") add("MixinGui")
    if (sc.current.parsed >= "26.3") addAll(listOf("MixinLevelExtractor", "MixinGuiRenderState", "MixinDynamicTexture", "MixinLayerOp"))
    if (sc.current.parsed >= "1.21.11") add("MixinRenderingTextCollector")
    sort()
}

tasks {
    processResources {
        val props = mapOf(
            "id" to sc.properties["mod.id"],
            "name" to sc.properties["mod.name"],
            "version" to project.version.toString(),
            "minecraft" to sc.properties["mod.mc_compat"],
            "java_version" to requiredJava.majorVersion,
            "flashback" to sc.properties["deps.flashback_min"],
            "oneconfig" to sc.properties["deps.oneconfig"],
            "java" to "JAVA_${requiredJava.majorVersion}",
            "mixin_classes" to mixinClasses.joinToString(",\n    ") { "\"$it\"" },
        )
        inputs.properties(props)
        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
    }

    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from("../../LICENSE") { rename { "${it}_$name" } }
    }

    test {
        useJUnitPlatform()
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
