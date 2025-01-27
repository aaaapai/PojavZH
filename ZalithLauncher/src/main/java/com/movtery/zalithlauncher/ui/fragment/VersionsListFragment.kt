package com.movtery.zalithlauncher.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import android.widget.PopupWindow
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewbinding.ViewBinding
import com.angcyo.tablayout.DslTabLayout
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.FragmentVersionsListBinding
import com.movtery.zalithlauncher.databinding.ItemFavoriteFolderBinding
import com.movtery.zalithlauncher.databinding.ViewVersionFavoritesActionBinding
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.END
import com.movtery.zalithlauncher.event.single.RefreshVersionsEvent.MODE.START
import com.movtery.zalithlauncher.event.sticky.FileSelectorEvent
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathJsonObject
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathManager.Companion.save
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.feature.version.favorites.FavoritesVersionUtils
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.ui.dialog.EditTextDialog
import com.movtery.zalithlauncher.ui.dialog.FavoritesVersionDialog
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.layout.AnimRelativeLayout
import com.movtery.zalithlauncher.ui.subassembly.customprofilepath.ProfileItem
import com.movtery.zalithlauncher.ui.subassembly.customprofilepath.ProfilePathAdapter
import com.movtery.zalithlauncher.ui.subassembly.version.VersionAdapter
import com.movtery.zalithlauncher.utils.path.PathManager
import com.movtery.zalithlauncher.utils.StoragePermissionsUtils
import com.movtery.zalithlauncher.utils.ZHTools
import net.kdt.pojavlaunch.Tools
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.UUID

class VersionsListFragment : FragmentWithAnim(R.layout.fragment_versions_list) {
    companion object {
        const val TAG: String = "VersionsListFragment"
        private var LAST_REFRESH_TIME: Long = 0L

        /**
         * 检查上次刷新时间，避免频繁刷新
         * @return true: 不允许刷新
         */
        fun checkLastRefreshTime(): Boolean {
            val currentTime = ZHTools.getCurrentTimeMillis()
            if (currentTime - LAST_REFRESH_TIME < 100) return true
            LAST_REFRESH_TIME = currentTime
            return false
        }
    }

    private lateinit var binding: FragmentVersionsListBinding
    private val profilePathData: MutableList<ProfileItem> = ArrayList()
    private var versionsAdapter: VersionAdapter? = null
    private var profilePathAdapter: ProfilePathAdapter? = null
    private val mFavoritesFolderViewList: MutableList<View> = ArrayList()

    private val mFavoritesActionPopupWindow: PopupWindow = PopupWindow().apply {
        isFocusable = true
        isOutsideTouchable = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val selectorEvent = EventBus.getDefault().getStickyEvent(FileSelectorEvent::class.java)

        selectorEvent?.let { event ->
            event.path?.let { path ->
                if (path.isNotEmpty() && !isAddedPath(path)) {
                    EditTextDialog.Builder(requireContext())
                        .setTitle(R.string.profiles_path_create_new_title)
                        .setAsRequired()
                        .setConfirmListener { editBox, _ ->
                            profilePathData.add(ProfileItem(UUID.randomUUID().toString(), editBox.text.toString(), path))
                            save(this.profilePathData)
                            refresh()
                            true
                        }.showDialog()
                }
            }
            EventBus.getDefault().removeStickyEvent(event)
        }
        binding = FragmentVersionsListBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.apply {
            installNew.setOnClickListener {
                ZHTools.swapFragmentWithAnim(this@VersionsListFragment, VersionSelectorFragment::class.java, VersionSelectorFragment.TAG, null)
            }

            fun refreshFavoritesFolderTab(index: Int) {
                when(index) {
                    0 -> refreshVersions(true)
                    else -> refreshVersions(false, FavoritesVersionUtils.getAllFolders()[index - 1])
                }
            }

            favoritesFolderTab.observeIndexChange { _, toIndex, reselect, fromUser ->
                if (fromUser && !reselect) {
                    refreshFavoritesFolderTab(toIndex)
                }
            }

            favoritesActions.setOnClickListener { showFavoritesActionPopupWindow(it) }

            versionsAdapter = VersionAdapter(this@VersionsListFragment, object : VersionAdapter.OnVersionItemClickListener {
                override fun showFavoritesDialog(versionName: String) {
                    if (FavoritesVersionUtils.getAllFolders().isNotEmpty()) {
                        FavoritesVersionDialog(requireActivity(), versionName) {
                            refreshFavoritesFolderTab(favoritesFolderTab.currentItemIndex)
                        }.show()
                    } else Toast.makeText(requireActivity(), getString(R.string.version_manager_favorites_dialog_no_folders), Toast.LENGTH_SHORT).show()
                }

                override fun isVersionFavorited(versionName: String): Boolean {
                    //如果收藏栏选择的不是“全部”，那么当前版本一定会是被收藏的状态
                    if (favoritesFolderTab.currentItemIndex != 0) {
                        return true
                    }
                    return FavoritesVersionUtils.getFavoritesMap().values.any { it.contains(versionName) }
                }
            })

            versions.apply {
                layoutAnimation = LayoutAnimationController(
                    AnimationUtils.loadAnimation(view.context, R.anim.fade_downwards)
                )
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = versionsAdapter
            }

            profilePathAdapter = ProfilePathAdapter(this@VersionsListFragment, profilesPath)
            profilesPath.apply {
                layoutAnimation = LayoutAnimationController(
                    AnimationUtils.loadAnimation(view.context, R.anim.fade_downwards)
                )
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = profilePathAdapter
            }

            refreshButton.setOnClickListener {
                if (checkLastRefreshTime()) return@setOnClickListener
                refresh(true)
            }
            createPathButton.setOnClickListener {
                StoragePermissionsUtils.checkPermissions(requireActivity(), R.string.profiles_path_create_new) {
                    val bundle = Bundle()
                    bundle.putBoolean(FilesFragment.BUNDLE_SELECT_FOLDER_MODE, true)
                    bundle.putBoolean(FilesFragment.BUNDLE_SHOW_FILE, false)
                    bundle.putBoolean(FilesFragment.BUNDLE_REMOVE_LOCK_PATH, false)
                    ZHTools.swapFragmentWithAnim(this@VersionsListFragment, FilesFragment::class.java, FilesFragment.TAG, bundle)
                }
            }
            returnButton.setOnClickListener { ZHTools.onBackPressed(requireActivity()) }
        }

        refresh()
    }

    private fun refresh(refreshVersionInfo: Boolean = false) {
        VersionsManager.refresh(refreshVersionInfo)

        profilePathData.clear()
        profilePathData.add(ProfileItem("default", getString(R.string.profiles_path_default), PathManager.DIR_GAME_HOME))

        runCatching {
            val json: String
            if (PathManager.FILE_PROFILE_PATH.exists()) {
                json = Tools.read(PathManager.FILE_PROFILE_PATH)
                if (json.isEmpty()) return@runCatching
            } else return@runCatching

            val jsonObject = JsonParser.parseString(json).asJsonObject

            for (key in jsonObject.keySet()) {
                val profilePathId = Gson().fromJson(jsonObject[key], ProfilePathJsonObject::class.java)
                val item = ProfileItem(key, profilePathId.title, profilePathId.path)
                profilePathData.add(item)
            }
        }.getOrElse { e -> Logging.e("refresh profile data", Tools.printToString(e)) }

        profilePathAdapter?.updateData(this.profilePathData)
    }

    private fun isAddedPath(path: String): Boolean {
        for (mDatum in this.profilePathData) {
            if (mDatum.path == path) return true
        }
        return false
    }

    private fun refreshVersions(all: Boolean = true, favoritesFolder: String? = null) {
        versionsAdapter?.let {
            val versions = VersionsManager.getVersions()

            fun getVersions(): ArrayList<Version> {
                if (all) return versions
                else {
                    val folderName = favoritesFolder ?: ""
                    val folderVersions = FavoritesVersionUtils.getAllVersions(folderName) ?: emptySet()
                    return ArrayList<Version>().apply {
                        versions.forEach { version ->
                            if (folderVersions.contains(version.getVersionName())) {
                                add(version)
                            }
                        }
                    }
                }
            }

            it.refreshVersions(getVersions())
            binding.versions.scheduleLayoutAnimation()
        }
    }

    private fun refreshFavoritesFolderAndVersions() {
        binding.favoritesFolderTab.setCurrentItem(0)
        refreshVersions()

        mFavoritesFolderViewList.forEach { view ->
            binding.favoritesFolderTab.removeView(view)
        }
        mFavoritesFolderViewList.clear()

        fun createView(folderName: String): AnimRelativeLayout {
            return ItemFavoriteFolderBinding.inflate(layoutInflater).apply {
                name.text = folderName
                root.layoutParams = DslTabLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                //长按删除
                root.setOnLongClickListener {
                    showFavoritesDeletePopupWindow(root, folderName)
                    true
                }
            }.root
        }

        FavoritesVersionUtils.getAllFolders().forEach { folderName ->
            val view = createView(folderName)
            mFavoritesFolderViewList.add(view)
            binding.favoritesFolderTab.addView(view)
        }
    }

    private fun refreshActionPopupWindow(anchorView: View, binding: ViewBinding) {
        mFavoritesActionPopupWindow.apply {
            binding.root.measure(0, 0)
            this.contentView = binding.root
            this.width = binding.root.measuredWidth
            this.height = binding.root.measuredHeight
            showAsDropDown(anchorView)
        }
    }

    private fun showFavoritesActionPopupWindow(anchorView: View) {
        refreshActionPopupWindow(anchorView, ViewVersionFavoritesActionBinding.inflate(LayoutInflater.from(requireActivity())).apply {
            addView.setOnClickListener {
                EditTextDialog.Builder(requireActivity())
                    .setTitle(R.string.version_manager_favorites_write_folder_name)
                    .setAsRequired()
                    .setConfirmListener { editText, _ ->
                        FavoritesVersionUtils.addFolder(editText.text.toString())
                        refreshFavoritesFolderAndVersions()
                        true
                    }.showDialog()
                mFavoritesActionPopupWindow.dismiss()
            }
        })
    }

    private fun showFavoritesDeletePopupWindow(anchorView: View, folderName: String) {
        refreshActionPopupWindow(anchorView, ViewVersionFavoritesActionBinding.inflate(LayoutInflater.from(requireActivity())).apply {
            addIcon.setImageDrawable(
                ContextCompat.getDrawable(requireActivity(), R.drawable.ic_menu_delete_forever)
            )
            addView.setText(R.string.version_manager_favorites_remove_folder_title)
            addView.setOnClickListener {
                TipDialog.Builder(requireActivity())
                    .setTitle(R.string.version_manager_favorites_remove_folder_title)
                    .setMessage(R.string.version_manager_favorites_remove_folder_message)
                    .setWarning()
                    .setConfirmClickListener {
                        FavoritesVersionUtils.removeFolder(folderName)
                        refreshFavoritesFolderAndVersions()
                    }.showDialog()
                mFavoritesActionPopupWindow.dismiss()
            }
        })
    }

    @Subscribe
    fun event(event: RefreshVersionsEvent) {
        binding.apply {
            TaskExecutors.runInUIThread {
                when (event.mode) {
                    START -> {
                        versions.isEnabled = false
                        favoritesFolderTab.isEnabled = false
                    }
                    END -> {
                        refreshFavoritesFolderAndVersions()
                        favoritesFolderTab.isEnabled = true
                        versions.isEnabled = true
                    }
                }
                //无论刷新进度，都应该关闭所有的操作弹窗
                closeAllPopupWindow()
            }
        }
    }

    private fun closeAllPopupWindow() {
        versionsAdapter?.closePopupWindow()
        profilePathAdapter?.closePopupWindow()
        mFavoritesActionPopupWindow.dismiss()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        versionsAdapter?.let {
            //为了避免界面初始化的时候连续刷新两次（OnViewCreated最后会刷新一次）
            //这里需要确保VersionAdapter已经完成初始化，才会刷新
            VersionsManager.refresh()
        }
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    override fun onPause() {
        super.onPause()
        closeAllPopupWindow()
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        binding.apply {
            animPlayer.apply(AnimPlayer.Entry(versionsListLayout, Animations.BounceInUp))
                .apply(AnimPlayer.Entry(versionTopBar, Animations.BounceInDown))
                .apply(AnimPlayer.Entry(operateLayout, Animations.BounceInLeft))
        }
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        binding.apply {
            animPlayer.apply(AnimPlayer.Entry(versionsListLayout, Animations.FadeOutDown))
                .apply(AnimPlayer.Entry(versionTopBar, Animations.FadeOutUp))
                .apply(AnimPlayer.Entry(operateLayout, Animations.FadeOutRight))
        }
    }
}
