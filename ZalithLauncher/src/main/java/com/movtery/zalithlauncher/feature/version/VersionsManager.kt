package com.movtery.zalithlauncher.feature.version

import android.content.Context
import com.movtery.zalithlauncher.InfoCenter
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.START
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.favorites.FavoritesVersionUtils
import com.movtery.zalithlauncher.feature.version.utils.VersionInfoUtils
import com.movtery.zalithlauncher.task.Task
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.EditTextDialog
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.file.FileTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kdt.pojavlaunch.Tools
import org.apache.commons.io.FileUtils
import org.greenrobot.eventbus.EventBus
import java.io.File

/**
 * 所有版本管理者
 * @see Version
 */
object VersionsManager {
    private val versions: MutableList<Version> = ArrayList()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val refreshMutex = Mutex()

    /**
     * @return 全部的版本数据
     */
    fun getVersions() = ArrayList(versions)

    /**
     * 检查版本是否已经存在
     */
    fun isVersionExists(versionName: String, checkJson: Boolean = false): Boolean {
        val folder = File(ProfilePathHome.getVersionsHome(), versionName)
        //保证版本文件夹存在的同时，也应保证其版本json文件存在
        return if (checkJson) File(folder, "${folder.name}.json").exists()
        else folder.exists()
    }

    /**
     * 异步刷新当前的版本列表，刷新完成后，将使用一个事件进行通知，不过这个事件并不会在UI线程执行
     * @see com.movtery.zalithlauncher.event.single.RefreshVersionsEvent
     */
    fun refresh(refreshVersionInfo: Boolean = false) {
        coroutineScope.launch {
            refreshMutex.withLock {
                EventBus.getDefault().post(RefreshVersionsEvent(START))

                try {
                    versions.clear()

                    val versionsHome = ProfilePathHome.getVersionsHome()
                    File(versionsHome).listFiles()?.forEach { versionFile ->
                        runCatching {
                            if (versionFile.exists() && versionFile.isDirectory) {
                                var isVersion = false

                                //通过判断是否存在版本的.json文件，来确定其是否为一个版本
                                val jsonFile = File(versionFile, "${versionFile.name}.json")
                                if (jsonFile.exists() && jsonFile.isFile) {
                                    isVersion = true
                                    val versionInfoFile = File(getZalithVersionPath(versionFile), "VersionInfo.json")
                                    if (refreshVersionInfo) FileUtils.deleteQuietly(versionInfoFile)
                                    if (!versionInfoFile.exists()) {
                                        VersionInfoUtils.parseJson(jsonFile)?.save(versionFile)
                                    }
                                }

                                val versionConfig = VersionConfig.parseConfig(versionFile)

                                versions.add(
                                    Version(
                                        versionsHome,
                                        versionFile.absolutePath,
                                        versionConfig,
                                        isVersion
                                    )
                                )
                            }
                        }
                    }
                    CurrentGameInfo.refreshCurrentInfo()
                } finally {
                    //使用事件通知版本已刷新
                    EventBus.getDefault().post(RefreshVersionsEvent(END))
                }
            }
        }
    }

    /**
     * @return 获取当前的版本
     */
    fun getCurrentVersion(): Version? {
        if (versions.isEmpty()) return null

        fun returnVersionByFirst(): Version? {
            versions.forEach { version ->
                if (version.isValid()) {
                    //确保版本有效
                    saveCurrentVersion(version.getVersionName())
                    return version
                }
            }
            //如果所有版本都无效，或者没有版本，那么久返回空
            return null
        }

        return runCatching {
            val versionString = CurrentGameInfo.getCurrentInfo().version
            getVersion(versionString) ?: run {
                return returnVersionByFirst()
            }
        }.getOrElse { e ->
            Logging.e("Get Current Version", Tools.printToString(e))
            returnVersionByFirst()
        }
    }

    /**
     * @return 通过版本名，判断其版本是否存在
     */
    fun checkVersionExistsByName(versionName: String?): Boolean = getVersion(versionName)?.let { true } ?: false

    /**
     * @return 获取 Zalith 启动器版本标识文件夹
     */
    fun getZalithVersionPath(version: Version) = File(version.getVersionPath(), InfoCenter.LAUNCHER_NAME)

    /**
     * @return 通过目录获取 Zalith 启动器版本标识文件夹
     */
    fun getZalithVersionPath(folder: File) = File(folder, InfoCenter.LAUNCHER_NAME)

    /**
     * @return 通过名称获取 Zalith 启动器版本标识文件夹
     */
    fun getZalithVersionPath(name: String) = File(getVersionPath(name), InfoCenter.LAUNCHER_NAME)

    /**
     * @return 获取当前版本设置的图标
     */
    fun getVersionIconFile(version: Version) = File(getZalithVersionPath(version), "VersionIcon.png")

    /**
     * @return 通过名称获取当前版本设置的图标
     */
    fun getVersionIconFile(name: String) = File(getZalithVersionPath(name), "VersionIcon.png")

    /**
     * @return 通过名称获取版本的文件夹路径
     */
    fun getVersionPath(name: String) = File(ProfilePathHome.getVersionsHome(), name)

    /**
     * 保存当前选择的版本
     */
    fun saveCurrentVersion(versionName: String) {
        runCatching {
            CurrentGameInfo.getCurrentInfo().apply {
                version = versionName
                saveCurrentInfo()
            }
        }.getOrElse { e -> Logging.e("Save Current Version", Tools.printToString(e)) }
    }

    /**
     * 打开重命名版本的弹窗，需要确保在UI线程运行
     * @param beforeRename 在重命名前一步的操作
     */
    fun openRenameDialog(context: Context, version: Version, beforeRename: (() -> Unit)? = null) {
        EditTextDialog.Builder(context)
            .setTitle(R.string.version_manager_rename)
            .setEditText(version.getVersionName())
            .setAsRequired()
            .setConfirmListener { editText, _ ->
                val string = editText.text.toString()

                if (string.contains("/")) {
                    editText.error = context.getString(R.string.generic_input_invalid_character, "/")
                    return@setConfirmListener false
                }

                //与原始名称一致
                if (string == version.getVersionName()) return@setConfirmListener true

                if (isVersionExists(string, true)) {
                    editText.error = context.getString(R.string.version_install_exists)
                    return@setConfirmListener false
                }

                version.getVersionInfo()?.let { info ->
                    //如果这个版本是有ModLoader加载器信息的，则不允许修改为与原版名称一致的名称，防止冲突
                    info.loaderInfo?.let { loaderInfo ->
                        if (loaderInfo.isNotEmpty() && string == info.minecraftVersion) {
                            editText.error = context.getString(R.string.version_install_cannot_use_mc_name)
                            return@setConfirmListener false
                        }
                    }
                }

                beforeRename?.invoke()
                renameVersion(version, string)

                true
            }.showDialog()
    }

    /**
     * 重命名当前版本，但并不会在这里对即将重命名的名称，进行非法性判断
     */
    private fun renameVersion(version: Version, name: String) {
        val currentVersionName = getCurrentVersion()?.getVersionName()
        //如果当前的版本是即将被重命名的版本，那么就把将要重命名的名字设置为当前版本
        if (version.getVersionName() == currentVersionName) saveCurrentVersion(name)

        //尝试刷新收藏夹内的版本名称
        FavoritesVersionUtils.renameVersion(version.getVersionName(), name)

        val versionFolder = version.getVersionPath()
        val renameFolder = File(ProfilePathHome.getVersionsHome(), name)

        //不管重命名之后的文件夹是什么，只要这个文件夹存在，那么就必须删除
        //否则将出现问题
        FileUtils.deleteQuietly(renameFolder)

        val originalName = versionFolder.name

        FileTools.renameFile(versionFolder, renameFolder)

        val versionJsonFile = File(renameFolder, "$originalName.json")
        val versionJarFile = File(renameFolder, "$originalName.jar")
        val renameJsonFile = File(renameFolder, "$name.json")
        val renameJarFile = File(renameFolder, "$name.jar")

        FileTools.renameFile(versionJsonFile, renameJsonFile)
        FileTools.renameFile(versionJarFile, renameJarFile)

        FileUtils.deleteQuietly(versionFolder)

        //重命名后，需要刷新列表
        refresh()
    }

    /**
     * 打开复制版本的名称输入框，将选中的版本复制为一个新的版本
     */
    fun openCopyDialog(context: Context, version: Version) {
        val dialog = ZHTools.createTaskRunningDialog(context)
        EditTextDialog.Builder(context)
            .setTitle(R.string.version_manager_copy)
            .setMessage(R.string.version_manager_copy_tip)
            .setCheckBoxText(R.string.version_manager_copy_all)
            .setShowCheckBox(true)
            .setEditText(version.getVersionName())
            .setAsRequired()
            .setConfirmListener { editText, checked ->
                val string = editText.text.toString()
                //与原始名称一致
                if (string == version.getVersionName()) return@setConfirmListener true

                if (isVersionExists(string, true)) {
                    editText.error = context.getString(R.string.version_install_exists)
                    return@setConfirmListener false
                }

                version.getVersionInfo()?.let { info ->
                    //如果这个版本是有ModLoader加载器信息的，则不允许修改为与原版名称一致的名称，防止冲突
                    info.loaderInfo?.let { loaderInfo ->
                        if (loaderInfo.isNotEmpty() && string == info.minecraftVersion) {
                            editText.error = context.getString(R.string.version_install_cannot_use_mc_name)
                            return@setConfirmListener false
                        }
                    }
                }

                Task.runTask {
                    copyVersion(version, string, checked)
                }.beforeStart(TaskExecutors.getAndroidUI()) {
                    dialog.show()
                }.onThrowable { e ->
                    Tools.showErrorRemote(e)
                }.finallyTask(TaskExecutors.getAndroidUI()) {
                    dialog.dismiss()
                    refresh()
                }.execute()
                true
            }.showDialog()
    }

    /**
     * 将选中的版本复制为一个新的版本
     * @param version 选中的版本
     * @param name 新的版本的名称
     * @param copyAllFile 是否复制全部文件
     */
    private fun copyVersion(version: Version, name: String, copyAllFile: Boolean) {
        val versionsFolder = version.getVersionsFolder()
        val newVersion = File(versionsFolder, name)

        val originalName = version.getVersionName()

        //新版本的json与jar文件
        val newJsonFile = File(newVersion, "$name.json")
        val newJarFile = File(newVersion, "$name.jar")

        val originalVersionFolder = version.getVersionPath()
        if (copyAllFile) {
            //启用复制所有文件时，直接将原文件夹整体复制到新版本
            FileUtils.copyDirectory(originalVersionFolder, newVersion)
            //重命名json、jar文件
            val jsonFile = File(newVersion, "$originalName.json")
            val jarFile = File(newVersion, "$originalName.jar")
            if (jsonFile.exists()) jsonFile.renameTo(newJsonFile)
            if (jarFile.exists()) jarFile.renameTo(newJarFile)
        } else {
            //不复制所有文件时，仅复制并重命名json、jar文件
            val originalJsonFile = File(originalVersionFolder, "$originalName.json")
            val originalJarFile = File(originalVersionFolder, "$originalName.jar")
            newVersion.mkdirs()
            // versions/1.21.3/1.21.3.json -> versions/name/name.json
            if (originalJsonFile.exists()) originalJsonFile.copyTo(newJsonFile)
            // versions/1.21.3/1.21.3.jar -> versions/name/name.jar
            if (originalJarFile.exists()) originalJarFile.copyTo(newJarFile)
        }

        //保存版本配置文件
        version.getVersionConfig().copy().let { config ->
            config.setVersionPath(newVersion)
            config.setIsolationType(VersionConfig.IsolationType.ENABLE)
            config.saveWithThrowable()
        }
    }

    private fun getVersion(name: String?): Version? {
        name?.let { versionName ->
            versions.forEach { version ->
                if (version.getVersionName() == versionName) {
                    return version.takeIf { it.isValid() }
                }
            }
        }
        return null
    }
}