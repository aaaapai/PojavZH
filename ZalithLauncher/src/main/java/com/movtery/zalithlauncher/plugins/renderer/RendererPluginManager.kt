package com.movtery.zalithlauncher.plugins.renderer

import android.content.Context
import android.content.pm.ApplicationInfo
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.update.UpdateUtils
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.ZipUtils
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile

/**
 * FCL、ZalithLauncher 渲染器插件，同时支持使用本地渲染器插件
 * [FCL Renderer Plugin](https://github.com/FCL-Team/FCLRendererPlugin)
 */
object RendererPluginManager {
    private val rendererPluginList: MutableList<RendererPlugin> = mutableListOf()
    private val localRendererPluginList: MutableList<LocalRendererPlugin> = mutableListOf()

    @JvmStatic
    fun getRendererList() = ArrayList(rendererPluginList)

    @JvmStatic
    fun getAllLocalRendererList() = ArrayList(localRendererPluginList)

    @JvmStatic
    fun markLocalRendererDeleted(index: Int) {
        if (index in localRendererPluginList.indices) {
            localRendererPluginList[index].isDeleted = true
        }
    }

    @JvmStatic
    fun isAvailable(): Boolean {
        return rendererPluginList.isNotEmpty()
    }

    @JvmStatic
    val selectedRendererPlugin: RendererPlugin?
        get() {
            return getRendererList().find { it.id == Tools.LOCAL_RENDERER }
        }

    /**
     * 解析 ZalithLauncher、FCL 渲染器插件
     */
    internal fun parseApkPlugin(context: Context, info: ApplicationInfo) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            val metaData = info.metaData ?: return
            if (
                metaData.getBoolean("fclPlugin", false) ||
                metaData.getBoolean("zalithRendererPlugin", false)
            ) {
                val rendererString = metaData.getString("renderer") ?: return
                val des = metaData.getString("des") ?: return
                val pojavEnvString = metaData.getString("pojavEnv") ?: return
                val nativeLibraryDir = info.nativeLibraryDir
                val renderer = rendererString.split(":")
                val pojavEnvPair = pojavEnvString.split(":").run {
                    val envPairList = mutableListOf<Pair<String, String>>()
                    forEach { envString ->
                        if (envString.contains("=")) {
                            val stringList = envString.split("=")
                            envPairList.add(Pair(stringList[0], stringList[1]))
                        }
                    }
                    envPairList
                }
                val rendererId = pojavEnvPair.find { it.first == "POJAV_RENDERER" }?.second ?: renderer[0]
                if (!rendererPluginList.any { it.id == rendererId }) {
                    rendererPluginList.add(
                        RendererPlugin(
                            rendererId,
                            "$des (${
                                context.getString(
                                    R.string.setting_renderer_from_plugins,
                                    runCatching {
                                        context.packageManager.getApplicationLabel(info)
                                    }.getOrElse {
                                        context.getString(R.string.generic_unknown)
                                    }
                                )
                            })",
                            renderer[1],
                            renderer[2],
                            nativeLibraryDir,
                            pojavEnvPair
                        )
                    )
                }
            }
        }
    }

    /**
     * 从本地 `/files/renderer_plugins/` 目录下尝试解析渲染器插件
     * @return 是否是符合要求的插件
     *
     * 渲染器文件夹格式
     * renderer_plugins/
     * ----文件夹名称/
     * --------renderer_config.json (存放渲染器具体信息的配置文件)
     * --------libs/ (渲染器`.so`文件的存放目录)
     * ------------arm64-v8a/ (arm64架构)
     * ----------------渲染器库文件.so
     * ------------armeabi-v7a/ (arm32架构)
     * ----------------渲染器库文件.so
     * ------------x86/ (x86架构)
     * ----------------渲染器库文件.so
     * ------------x86_64/ (x86_64架构)
     * ----------------渲染器库文件.so
     */
    internal fun parseLocalPlugin(context: Context, directory: File): Boolean {
        val archModel: String = UpdateUtils.getArchModel(Architecture.getDeviceArchitecture()) ?: return false
        val libsDirectory: File = File(directory, "libs/$archModel").takeIf { it.exists() && it.isDirectory } ?: return false
        val rendererConfigFile: File = File(directory, "renderer_config.json").takeIf { it.exists() && it.isFile } ?: return false
        val rendererConfig: RendererConfig = runCatching {
            Tools.GLOBAL_GSON.fromJson(Tools.read(rendererConfigFile), RendererConfig::class.java)
        }.getOrElse { return false }
        rendererConfig.run {
            localRendererPluginList.add(
                LocalRendererPlugin(
                    rendererId,
                    rendererDisplayName,
                    directory
                )
            )
            if (!rendererPluginList.any { it.id == rendererId }) {
                rendererPluginList.add(
                    RendererPlugin(
                        rendererId,
                        "$rendererDisplayName (${
                            context.getString(
                                R.string.setting_renderer_from_plugins,
                                directory.name
                            )
                        })", glName, eglName,
                        libsDirectory.absolutePath,
                        env.toList()
                    )
                )
            }
        }
        return true
    }

    /**
     * 导入本地渲染器插件
     */
    fun importLocalRendererPlugin(pluginFile: File): Boolean {
        if (!pluginFile.exists() || !pluginFile.isFile) {
            Logging.i("importLocalRendererPlugin", "The compressed file does not exist or is not a valid file.")
            return false
        }

        return try {
            ZipFile(pluginFile).use { pluginZip ->
                val configEntry = pluginZip.entries().asSequence().find { it.name == "renderer_config.json" }
                    ?: throw IllegalArgumentException("The plugin package does not meet the requirements!")

                val rendererConfig = pluginZip.getInputStream(configEntry).use { inputStream ->
                    val configContent = InputStreamReader(inputStream).readText()
                    Tools.GLOBAL_GSON.fromJson(configContent, RendererConfig::class.java)
                }

                val rendererId = rendererConfig.rendererId
                val pluginFolder: File = File(
                    PathManager.DIR_INSTALLED_RENDERER_PLUGIN,
                    rendererId
                ).takeIf { !(it.exists() && it.isDirectory) && !rendererPluginList.any { plugin -> plugin.id == rendererId } }
                    ?: throw IllegalArgumentException("The renderer plugin $rendererId already exists!")

                ZipUtils.zipExtract(pluginZip, "", pluginFolder)
            }
            true
        } catch (e: Exception) {
            Logging.i("importLocalRendererPlugin", "Error: ${e.message}")
            false
        }
    }
}