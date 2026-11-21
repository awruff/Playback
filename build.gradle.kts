import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import net.ornithemc.ploceus.api.PloceusGradleExtensionApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.kikugie.loom-back-compat")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("ploceus") version "1.19.1" apply false
}

val isOrnithe = sc.current.version == "1.8.9"
val ploceus = if (isOrnithe) {
    pluginManager.apply("net.fabricmc.fabric-loom-remap")
    pluginManager.apply("ploceus")

    configurations.configureEach {
        exclude(group = "org.lwjgl.lwjgl")
    }

    extensions.getByType<PloceusGradleExtensionApi>().apply {
        setIntermediaryGeneration(2)
    }
} else {
    null
}

version = "${property("mod.version")}+${sc.current.project}"
base.archivesName = property("mod.id") as String

val requiredJava = if (isOrnithe || sc.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

val standalone = "org/polyfrost/keyframe/standalone"
val modernStandalone = listOf("compat/Compat", "export/AudioCapture", "playback/Keyframe", "export/PreviewFrame", "export/RunOnceTest").map { "$standalone/$it.kt" } + "$standalone/mixin/Mixin_*.java"
val sharedAddon = listOf("ui/UiLayout", "shadow/ShadowRecorder", "shadow/ShadowReplayZip", "shadow/ShadowReplayConfig", "shadow/ShadowReplayCore", "camera/CameraFrames", "ui/ScreenWire").flatMap { listOf("$it.java", "${it}Test.java") }
val ornitheKeeps = listOf("standalone/", "boot/", "ornithe/", "mixin/ornithe/")
sourceSets.configureEach {
    val excluded = buildList {
        if (isOrnithe) addAll(modernStandalone) else add("**/ornithe/**")
    }
    if (isOrnithe) {
        val keeps = ornitheKeeps
        val shared = sharedAddon
        java.exclude {
            val path = it.relativePath.pathString.removePrefix("org/polyfrost/keyframe/")
            !it.isDirectory && path != it.relativePath.pathString && keeps.none(path::startsWith) && path !in shared
        }
    }
    java.exclude(excluded)
    kotlin.exclude(excluded)
}

repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/releases") { name = "TerraformersMC" } }
        filter { includeGroup("com.terraformersmc") }
    }
    maven("https://repo.polyfrost.org/releases") { name = "Polyfrost" }
    maven("https://maven.cloverclient.com/releases") {
        content { includeGroup("pl.tomgirl") }
    }
    mavenLocal {
        content { includeModuleByRegex("org\\.polyfrost", "flashback_legacy.*") }
    }
    maven("https://redirector.kotlinlang.org/maven/compose-dev")
    google()
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    if (isOrnithe) {
        mappings(ploceus!!.featherMappings(property("deps.feather") as String))
        ploceus.dependOsl(property("deps.osl") as String)
        val legacy = "org.polyfrost:flashback_legacy:${property("deps.flashback_legacy")}"
        if (findProperty("standalone") == null) modImplementation(legacy) else modCompileOnly(legacy)
        compileOnly("org.polyfrost:flashback_legacy-patched-flashback:${property("deps.flashback_legacy")}")
        compileOnly("org.polyfrost:flashback_legacy-stubs:${property("deps.flashback_legacy")}")
        modLocalRuntime("pl.tomgirl:pylon:${property("deps.pylon")}")
    } else {
        loomx.applyMojangMappings()
        modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")
        val flashback = "maven.modrinth:flashback:${property("deps.flashback")}"
        if (findProperty("standalone") == null) modImplementation(flashback) else modCompileOnly(flashback)
    }

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_kotlin")}")
    modImplementation("org.polyfrost.oneconfig:${sc.current.project}-${if (isOrnithe) "ornithe" else "fabric"}:${property("deps.oneconfig")}")
    compileOnly("org.jspecify:jspecify:1.0.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

stonecutter {
    filters.exclude("java/org/polyfrost/keyframe/ornithe/**", "java/org/polyfrost/keyframe/mixin/ornithe/**")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
    if (!isOrnithe) {
        accessWidenerPath = sc.process(
            rootProject.file("src/main/resources/keyframe.accesswidener"),
            "build/processed.accesswidener"
        )
    }

    decompilerOptions.named("vineflower") {
        options.put("mark-corresponding-synthetics", "1")
    }

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file((if (isOrnithe) "run/ornithe" else "run") + if (findProperty("standalone") != null) "/standalone" else "")
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

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(requiredJava.majorVersion)
    }
}

val addonMixins = if (isOrnithe) {
    rootProject.fileTree("src/main/java/org/polyfrost/keyframe/mixin/ornithe").map { "ornithe.${it.nameWithoutExtension}" }.sorted()
} else buildList {
    addAll(listOf("MixinAbstractSelectionList", "MixinAbstractWidget", "MixinCamera", "MixinClientPacketListener", "MixinGameRenderer", "MixinGuiGraphicsExtractor", "MixinHud", "MixinInventoryScreen",
        "MixinMinecraft", "MixinPanorama", "MixinPauseScreen", "MixinPlayer", "MixinSoundManager"))
    addAll(listOf("AsyncReplaySaverAccessor", "MixinFlashback", "MixinRecorder", "MixinReplayGamePacketHandler", "MixinReplayPlayer", "MixinReplayServer").map { "flashback.$it" })
    if (sc.current.parsed < "1.21.6") addAll(listOf("MixinFont", "MixinHudRenderCallback", "MixinLayeredDraw"))
    if (sc.current.parsed >= "26.1") add("flashback.MixinSaveableFramebuffer")
    if (sc.current.parsed >= "26.2") add("MixinGui")
    if (sc.current.parsed >= "26.3") addAll(listOf("MixinLevelExtractor", "MixinGuiRenderState", "MixinDynamicTexture", "MixinLayerOp"))
    if (sc.current.parsed >= "1.21.6") add("MixinHudRootLayer")
    if (sc.current.parsed < "26.2") add("MixinGameRendererHud")
    if (sc.current.parsed >= "1.21.11") add("MixinRenderingTextCollector")
    if (sc.current.parsed >= "1.21.10" && sc.current.parsed < "26.1") add("flashback.MixinLatticeDropdownEntry")
    sort()
}

val skippedStandaloneMixins = buildList {
    if (sc.current.parsed >= "26.3") add("Mixin_KeyboardHandler_Core")
    else addAll(listOf("Mixin_SDLEventHandler_Core", "Mixin_SwingState_Puppet"))
    if (sc.current.parsed >= "26.1") add("Mixin_GameRenderer_Puppet")
    if (sc.current.parsed >= "26.2") add("Mixin_GlBuffer_Export")
    if (sc.current.parsed >= "1.21.10") add("Mixin_DebugScreenOverlay_Export")
    else addAll(listOf("Mixin_ClientAvatarState_Puppet", "Mixin_LocalPlayer_Spinning"))
    if (sc.current.parsed >= "1.21.8") add("Mixin_MobEffectInstance_Puppet")
    else addAll(listOf(
        "Mixin_GlBuffer_Export",
        "Mixin_Graphics_Blit",
        "Mixin_Graphics_Editor",
        "Mixin_GuiRenderer_Editor",
        "Mixin_LevelRenderer_Export",
        "Mixin_RenderSection_Export",
        "Mixin_SectionTask_Export",
    ))
}

tasks {
    processResources {
        val props = mapOf(
            "id" to project.property("mod.id").toString(),
            "name" to project.property("mod.name").toString(),
            "version" to project.version.toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "loader" to project.property("deps.fabric_loader").toString(),
            "java_version" to requiredJava.majorVersion,
            "flashback" to (project.findProperty("deps.flashback_min") ?: "0").toString(),
            "oneconfig" to project.property("deps.oneconfig").toString(),
            "java" to "JAVA_${requiredJava.majorVersion}",
            "mixin_classes" to addonMixins.joinToString(",\n    ") { "\"$it\"" },
        )
        inputs.properties(props)
        inputs.property("skippedStandaloneMixins", skippedStandaloneMixins)
        val ornithe = isOrnithe
        val skippedMixins = skippedStandaloneMixins

        filesMatching(listOf("fabric.mod.json", "*.mixins.json")) { expand(props) }
        filesMatching("keyframe.standalone.mixins.json") {
            filter { line ->
                val skipped = skippedMixins.any { "\"$it\"" in line }
                val wrongPlatform = if (ornithe) "\"Mixin_" in line else "\"ornithe.Mixin_" in line
                if (skipped || wrongPlatform) "" else line
            }
        }
        if (isOrnithe) exclude("keyframe.accesswidener")

        val out = destinationDir
        doLast {
            out.resolve("keyframe.standalone.mixins.json").takeIf { it.exists() }?.let {
                it.writeText(it.readText().replace(Regex(",(\\s*])"), "$1"))
            }
            val modJson = out.resolve("fabric.mod.json")
            @Suppress("UNCHECKED_CAST")
            val mod = JsonSlurper().parse(modJson) as MutableMap<String, Any?>
            if (ornithe) {
                (mod["depends"] as MutableMap<*, *>).remove("fabric-api")
                (mod["entrypoints"] as MutableMap<*, *>).remove("mixinsquared")
                mod.remove("accessWidener")
                for (key in listOf("suggests", "breaks")) {
                    @Suppress("UNCHECKED_CAST")
                    val deps = mod[key] as MutableMap<String, Any?>
                    deps["flashback_legacy"] = deps.remove("flashback")
                }
            }
            modJson.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(mod)))
        }
    }

    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
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

tasks.register<Exec>("runClientStandalone") {
    group = "fabric"
    description = "Runs the client without Flashback, in its own run directory"
    workingDir = rootDir
    commandLine(rootDir.resolve(if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "gradlew").path, ":${project.name}:runClient", "-Pstandalone")
}
