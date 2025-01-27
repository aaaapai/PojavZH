package com.movtery.zalithlauncher.feature.version

import com.movtery.zalithlauncher.feature.log.Logging
import net.kdt.pojavlaunch.Tools
import java.io.File
import java.io.FileWriter

class VersionInfo(
    val minecraftVersion: String,
    val loaderInfo: Array<LoaderInfo>?
) {
    /**
     * 拼接Minecraft的版本信息，包括ModLoader信息
     * @return 用", "分割的信息字符串
     */
    fun getInfoString(): String {
        val infoList = mutableListOf<String>().apply {
            add(minecraftVersion)
            loaderInfo?.forEach { info ->
                when {
                    info.name.isNotBlank() && info.version.isNotBlank() -> add("${info.name} - ${info.version}")
                    info.name.isNotBlank() -> add(info.name)
                    info.version.isNotBlank() -> add(info.version)
                }
            }
        }
        return infoList.joinToString(", ")
    }


    class LoaderInfo(
        val name: String,
        val version: String
    ) {
        override fun toString(): String {
            return "LoaderInfo{name='$name', version='$version'}"
        }
    }

    fun save(versionFolder: File) {
        runCatching {
            val zalithVersionPath = VersionsManager.getZalithVersionPath(versionFolder)
            val infoFile = File(zalithVersionPath, "VersionInfo.json")
            if (!zalithVersionPath.exists()) zalithVersionPath.mkdirs()

            FileWriter(infoFile, false).use {
                val json = Tools.GLOBAL_GSON.toJson(this)
                it.write(json)
            }
        }.getOrElse { e -> Logging.e("Save Version Info", Tools.printToString(e)) }
    }
}