package com.movtery.zalithlauncher.feature.version.utils

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionInfo
import net.kdt.pojavlaunch.Tools
import java.io.File

class VersionInfoUtils {
    companion object {
        /**
         * 未知：未能从版本json中提取出已知ModLoader的信息
         */
        private val UNKNOWN = Pair("Unknown", "")

        private val LOADER_PAIR = mapOf(
            // "1.20.4-OptiFine_HD_U_I7_pre3"       -> Pair("OptiFine", "HD_U_I7_pre3")
            // "1.21.3-OptiFine_HD_U_J2_pre6"       -> Pair("OptiFine", "HD_U_J2_pre6")
            "-OptiFine_" to { id: String ->
                val matchResult = "-OptiFine_(.*)".toRegex().find(id)
                matchResult?.groups?.get(1)?.value?.let { Pair("OptiFine", it) } ?: UNKNOWN
            },
            // "1.20.2-forge-48.1.0"                -> Pair("Forge", "48.1.0")
            // "1.21.3-forge-53.0.23"               -> Pair("Forge", "53.0.23")
            "-forge-" to { id: String ->
                val matchResult = "-forge-(.*)".toRegex().find(id)
                matchResult?.groups?.get(1)?.value?.let { Pair("Forge", it) } ?: UNKNOWN
            },
            // "1.7.10-Forge10.13.4.1614-1.7.10"    -> Pair("Forge", "10.13.4.1614")
            "-Forge" to { id: String ->
                val matchResult = "-Forge([^-]*)-.*".toRegex().find(id)
                matchResult?.groups?.get(1)?.value?.let { Pair("Forge", it) } ?: UNKNOWN
            },
            // "neoforge-21.1.8"                    -> Pair("NeoForge", "21.1.8")
            // "neoforge-21.3.36-beta"              -> Pair("NeoForge", "21.3.36-beta")
            "neoforge-" to { id: String ->
                val version = id.removePrefix("neoforge-")
                Pair("NeoForge", version)
            },
            // "fabric-loader-0.15.7-1.20.4"        -> Pair("Fabric", "0.15.7")
            // "fabric-loader-0.16.9-1.21.3"        -> Pair("Fabric", "0.16.9")
            "fabric-loader-" to { id: String ->
                val matchResult = "fabric-loader-([^-]*)-.*".toRegex().find(id)
                matchResult?.groups?.get(1)?.value?.let { Pair("Fabric", it) } ?: UNKNOWN
            },
            // "quilt-loader-0.23.1-1.20.4"         -> Pair("Quilt", "0.23.1")
            // "quilt-loader-0.27.1-beta.1-1.21.3"  -> Pair("Quilt", "0.27.1-beta.1")
            "quilt-loader-" to { id: String ->
                val matchResult = "quilt-loader-(.*?)-(?=[^-]+$|$)".toRegex().find(id)
                matchResult?.groups?.get(1)?.value?.let { Pair("Quilt", it) } ?: UNKNOWN
            }
        )

        /**
         * 在版本的json文件中，找到版本信息，识别其是否有id这个键
         * @return 版本号、ModLoader信息
         */
        fun parseJson(jsonFile: File): VersionInfo? {
            return runCatching {
                val jsonObject = JsonParser.parseString(Tools.read(jsonFile)).asJsonObject
                if (jsonObject.has("id")) {
                    var id = jsonObject.get("id").asString

                    //由于已知的ModLoader都会把id更改为自己定义的版本字符串格式
                    //使用inheritsFrom来存放原版的id
                    //所以这里用检查inheritsFrom是否存在的方式来判断是否为ModLoader
                    val modloaderInfo = if (jsonObject.has("inheritsFrom")) {
                        //这里需要使用它们自定义的id进行解析
                        val info = parseLoader(id, jsonObject.takeIf { it.has("libraries") }?.getAsJsonArray("libraries"))
                        Logging.i("Parse version info", info.toString())
                        id = jsonObject.get("inheritsFrom").asString
                        arrayOf(info)
                    } else null

                    VersionInfo(
                        id,
                        modloaderInfo
                    )
                } else null
            }.getOrNull()
        }

        /**
         * 通过库中是否有optifine来判断当前版本是否为一个OptiFine版本
         */
        private fun JsonArray.takeIfOptiFine(): Pair<String, String>? {
            return firstOrNull { library ->
                runCatching {
                    library.asJsonObject.get("name").asString
                }.getOrNull()?.split(":")?.takeIf { it.size == 3 }?.let { libs ->
                    libs[0] == "optifine" && libs[1] == "OptiFine"
                } == true
            }?.let {
                val libs = it.asJsonObject.get("name").asString.split(":")
                Pair("OptiFine", libs[2])
            }
        }

        /**
         * 通过Id判断ModLoader信息：ModLoader名称、版本
         */
        private fun parseLoader(id: String, libraries: JsonArray?): VersionInfo.LoaderInfo {
            val pair = LOADER_PAIR.entries
                .firstOrNull { id.contains(it.key, true) }
                ?.value?.invoke(id)
                ?: libraries?.takeIfOptiFine() ?: UNKNOWN

            return VersionInfo.LoaderInfo(
                pair.first,
                pair.second
            )
        }
    }
}