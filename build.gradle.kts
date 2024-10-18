@file:Suppress("UnstableApiUsage", "PropertyName")

import dev.deftu.gradle.utils.GameSide

plugins {
    java
    kotlin("jvm")
    id("dev.deftu.gradle.multiversion")
    id("dev.deftu.gradle.tools")
    id("dev.deftu.gradle.tools.resources")
    id("dev.deftu.gradle.tools.bloom")
    id("dev.deftu.gradle.tools.shadow")
    id("dev.deftu.gradle.tools.minecraft.loom")
}

// Sets up the variables for when we preprocess to other Minecraft versions.
preprocess {
    vars.put("MODERN", if (mcData.version.minor >= 16) 1 else 0)
}

toolkitLoomHelper {
    // Adds OneConfig to our project
    useOneConfig(mcData.version, mcData.loader, "commands", "config-impl", "events", "hud", "internal", "ui")
    useDevAuth()

    // Removes the server configs from IntelliJ IDEA, leaving only client runs.
    // If you're developing a server-side mod, you can remove this line.
    disableRunConfigs(GameSide.SERVER)

    // Sets up our Mixin refmap naming
    if (!mcData.isNeoForge) {
        useMixinRefMap(modData.id)
    }

    // Adds the tweak class if we are building legacy version of forge as per the documentation (https://docs.polyfrost.org)
    if (mcData.isLegacyForge) {
        useCoreMod("org.polyfrost.craftycrashes.plugin.LegacyCraftyCrashesLoadingPlugin")
        useTweaker("org.polyfrost.oneconfig.internal.legacy.OneConfigTweaker", GameSide.CLIENT)
        useForgeMixin(modData.id) // Configures the mixins if we are building for forge, useful for when we are dealing with cross-platform projects.
    }
}

// Configures the output directory for when building from the `src/resources` directory.
sourceSets {
    val dummy by creating
    main {
        dummy.compileClasspath += compileClasspath
        compileClasspath += dummy.output
        output.setResourcesDir(java.classesDirectory)
    }
}

// Adds the Polyfrost maven repository so that we can get the libraries necessary to develop the mod.
repositories {
    maven("https://repo.polyfrost.org/releases")
}

// Configures the libraries/dependencies for your mod.
dependencies {
    // If we are building for legacy forge, includes the launch wrapper with `shade` as we configured earlier.
    if (mcData.isLegacyForge) {
        compileOnly("org.spongepowered:mixin:0.7.11-SNAPSHOT")
    }
}
