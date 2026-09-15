plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3" /* [SC] DO NOT EDIT */

stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    dependencies["fapi"] = node.project.property("deps.fabric_api") as String

    replacements {
        string(current.parsed < "1.21.11") {
            replace("net.minecraft.Util", "net.minecraft.util.Util")
        }

        string(current.parsed < "26.3") {
            replace("com.mojang.renderpearl.api.buffers", "com.mojang.blaze3d.buffers")
            replace("com.mojang.renderpearl.api.textures", "com.mojang.blaze3d.textures")
            replace("com.mojang.renderpearl.api.GpuFormat", "com.mojang.blaze3d.GpuFormat")
        }
    }
}

stonecutter tasks {
    order("publishModrinth")
}