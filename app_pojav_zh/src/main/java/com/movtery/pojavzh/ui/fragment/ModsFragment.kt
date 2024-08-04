package com.movtery.pojavzh.ui.fragment

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.daimajia.androidanimations.library.Techniques
import com.daimajia.androidanimations.library.YoYo.YoYoString
import com.movtery.pojavzh.ui.dialog.FilesDialog
import com.movtery.pojavzh.ui.dialog.FilesDialog.FilesButton
import com.movtery.pojavzh.ui.dialog.TipDialog
import com.movtery.pojavzh.ui.subassembly.filelist.FileIcon
import com.movtery.pojavzh.ui.subassembly.filelist.FileItemBean
import com.movtery.pojavzh.ui.subassembly.filelist.FileRecyclerView
import com.movtery.pojavzh.ui.subassembly.filelist.FileSelectedListener
import com.movtery.pojavzh.ui.subassembly.view.SearchView
import com.movtery.pojavzh.utils.ZHTools
import com.movtery.pojavzh.utils.anim.AnimUtils.setVisibilityAnim
import com.movtery.pojavzh.utils.anim.ViewAnimUtils.setViewAnim
import com.movtery.pojavzh.utils.anim.ViewAnimUtils.slideInAnim
import com.movtery.pojavzh.utils.file.FileTools.copyFileInBackground
import com.movtery.pojavzh.utils.file.FileTools.renameFile
import com.movtery.pojavzh.utils.file.OperationFile
import com.movtery.pojavzh.utils.file.PasteFile
import net.kdt.pojavlaunch.PojavApplication
import net.kdt.pojavlaunch.R
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension
import net.kdt.pojavlaunch.fragments.SearchModFragment
import java.io.File
import java.util.function.Consumer

class ModsFragment : FragmentWithAnim(R.layout.fragment_mods) {
    companion object {
        const val TAG: String = "ModsFragment"
        const val BUNDLE_ROOT_PATH: String = "root_path"
        const val JAR_FILE_SUFFIX: String = ".jar"
        const val DISABLE_JAR_FILE_SUFFIX: String = "$JAR_FILE_SUFFIX.disabled"
    }

    private var openDocumentLauncher: ActivityResultLauncher<Any>? = null
    private var mModsLayout: View? = null
    private var mOperateLayout: View? = null
    private var mSownloadOptiFine: View? = null
    private var mOperateView: View? = null
    private var mReturnButton: ImageButton? = null
    private var mAddModButton: ImageButton? = null
    private var mPasteButton: ImageButton? = null
    private var mDownloadButton: ImageButton? = null
    private var mSearchSummonButton: ImageButton? = null
    private var mRefreshButton: ImageButton? = null
    private var mNothingTip: TextView? = null
    private var mSearchView: SearchView? = null
    private var mMultiSelectCheck: CheckBox? = null
    private var mSelectAllCheck: CheckBox? = null
    private var mFileRecyclerView: FileRecyclerView? = null
    private var mRootPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDocumentLauncher = registerForActivityResult(OpenDocumentWithExtension("jar")) { result: Uri? ->
            result?.let{
                Toast.makeText(requireContext(), getString(R.string.tasks_ongoing), Toast.LENGTH_SHORT).show()

                PojavApplication.sExecutorService.execute {
                    copyFileInBackground(requireContext(), result, mRootPath)
                    Tools.runOnUiThread { Toast.makeText(requireContext(), getString(R.string.zh_profile_mods_added_mod), Toast.LENGTH_SHORT).show()
                        mFileRecyclerView!!.refreshPath()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindViews(view)
        parseBundle()

        mFileRecyclerView!!.setShowFiles(true)
        mFileRecyclerView!!.setShowFolders(false)
        mFileRecyclerView!!.lockAndListAt(mRootPath?.let { File(it) }, mRootPath?.let { File(it) })

        mFileRecyclerView!!.setFileSelectedListener(object : FileSelectedListener() {
            override fun onFileSelected(file: File?, path: String?) {
                showDialog(file)
            }

            override fun onItemLongClick(file: File?, path: String?) {
                if (file!!.isDirectory) {
                    showDialog(file)
                }
            }
        })
        mFileRecyclerView!!.setOnMultiSelectListener { itemBeans: List<FileItemBean> ->
            if (itemBeans.isNotEmpty()) {
                PojavApplication.sExecutorService.execute {}
                //取出全部文件
                val selectedFiles: MutableList<File> = ArrayList()
                itemBeans.forEach(Consumer { value: FileItemBean ->
                    val file = value.file
                    if (file != null) { selectedFiles.add(file) }
                })
                val filesButton = FilesButton()
                filesButton.setButtonVisibility(true, true, false, false, true, true)
                filesButton.setDialogText(
                    getString(R.string.zh_file_multi_select_mode_title),
                    getString(R.string.zh_file_multi_select_mode_message, itemBeans.size),
                    getString(R.string.zh_profile_mods_disable_or_enable)
                )
                Tools.runOnUiThread {
                    val filesDialog = FilesDialog(requireContext(), filesButton, {
                        Tools.runOnUiThread {
                            closeMultiSelect()
                            mFileRecyclerView!!.refreshPath()
                        }
                    }, selectedFiles)
                    filesDialog.setCopyButtonClick { mPasteButton!!.visibility = View.VISIBLE }
                    filesDialog.setMoreButtonClick {
                        OperationFile(requireContext(), {
                            Tools.runOnUiThread {
                                closeMultiSelect()
                                mFileRecyclerView!!.refreshPath()
                            }
                        }, { file: File? ->
                            file?.let{ if (file.exists()) {
                                val fileName = file.name
                                if (fileName.endsWith(JAR_FILE_SUFFIX)) {
                                    disableMod(file)
                                } else if (fileName.endsWith(DISABLE_JAR_FILE_SUFFIX)) {
                                    enableMod(file)
                                } }
                            }
                        }).operationFile(selectedFiles)
                    }
                    filesDialog.show()
                }
            }
        }
        mFileRecyclerView!!.setRefreshListener {
            val itemCount = mFileRecyclerView!!.itemCount
            val show = itemCount == 0
            setVisibilityAnim(mNothingTip!!, show)
        }
        val adapter = mFileRecyclerView!!.adapter
        mMultiSelectCheck!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            mSelectAllCheck!!.isChecked = false
            mSelectAllCheck!!.visibility = if (isChecked) View.VISIBLE else View.GONE
            adapter.setMultiSelectMode(isChecked)
        }
        mSelectAllCheck!!.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            adapter.selectAllFiles(isChecked)
        }

        mReturnButton!!.setOnClickListener {
            closeMultiSelect()
            ZHTools.onBackPressed(requireActivity())
        }
        mAddModButton!!.setOnClickListener {
            closeMultiSelect()
            val suffix = ".jar"
            Toast.makeText(
                requireActivity(),
                String.format(getString(R.string.zh_file_add_file_tip), suffix),
                Toast.LENGTH_SHORT
            ).show()
            openDocumentLauncher!!.launch(suffix)
        }
        mPasteButton!!.setOnClickListener {
            PasteFile.getInstance().pasteFiles(
                requireActivity(),
                mFileRecyclerView!!.fullPath,
                { file: File -> this.getFileSuffix(file) },
                {
                    Tools.runOnUiThread {
                        closeMultiSelect()
                        mPasteButton!!.visibility = View.GONE
                        mFileRecyclerView!!.refreshPath()
                    }
                })
        }
        mDownloadButton!!.setOnClickListener {
            closeMultiSelect()
            val bundle = Bundle()
            bundle.putBoolean(SearchModFragment.BUNDLE_SEARCH_MODPACK, false)
            bundle.putString(SearchModFragment.BUNDLE_MOD_PATH, mRootPath)
            ZHTools.swapFragmentWithAnim(
                this,
                SearchModFragment::class.java,
                SearchModFragment.TAG,
                bundle
            )
        }
        mSownloadOptiFine!!.setOnClickListener {
            TipDialog.Builder(requireContext())
                .setMessage(R.string.zh_profile_manager_download_optifine_message)
                .setConfirmClickListener {
                    val bundle = Bundle()
                    bundle.putBoolean(DownloadOptiFineFragment.BUNDLE_DOWNLOAD_MOD, true)
                    ZHTools.swapFragmentWithAnim(
                        this,
                        DownloadOptiFineFragment::class.java,
                        DownloadOptiFineFragment.TAG,
                        bundle
                    )
                }.buildDialog()
        }
        mSearchSummonButton!!.setOnClickListener {
            closeMultiSelect()
            mSearchView!!.setVisibility()
        }
        mRefreshButton!!.setOnClickListener {
            closeMultiSelect()
            mFileRecyclerView!!.refreshPath()
        }

        slideInAnim(this)
    }

    private fun closeMultiSelect() {
        //点击其它控件时关闭多选模式
        mMultiSelectCheck!!.isChecked = false
        mSelectAllCheck!!.visibility = View.GONE
    }

    private fun showDialog(file: File?) {
        val fileName = file!!.name

        val filesButton = FilesButton()
        filesButton.setButtonVisibility(true, true, !file.isDirectory, true, true,
            (fileName.endsWith(JAR_FILE_SUFFIX) || fileName.endsWith(DISABLE_JAR_FILE_SUFFIX)))
        filesButton.setMessageText(if (file.isDirectory) getString(R.string.zh_file_folder_message) else getString(R.string.zh_file_message))

        if (fileName.endsWith(JAR_FILE_SUFFIX)) filesButton.setMoreButtonText(getString(R.string.zh_profile_mods_disable))
        else if (fileName.endsWith(DISABLE_JAR_FILE_SUFFIX)) filesButton.setMoreButtonText(getString(R.string.zh_profile_mods_enable))

        val filesDialog = FilesDialog(
            requireContext(),
            filesButton,
            { Tools.runOnUiThread { mFileRecyclerView!!.refreshPath() } },
            file
        )

        filesDialog.setCopyButtonClick { mPasteButton!!.visibility = View.VISIBLE }

        //检测后缀名，以设置正确的按钮
        if (fileName.endsWith(JAR_FILE_SUFFIX)) {
            filesDialog.setFileSuffix(JAR_FILE_SUFFIX)
            filesDialog.setMoreButtonClick {
                disableMod(file)
                mFileRecyclerView!!.refreshPath()
                filesDialog.dismiss()
            }
        } else if (fileName.endsWith(DISABLE_JAR_FILE_SUFFIX)) {
            filesDialog.setFileSuffix(DISABLE_JAR_FILE_SUFFIX)
            filesDialog.setMoreButtonClick {
                enableMod(file)
                mFileRecyclerView!!.refreshPath()
                filesDialog.dismiss()
            }
        }

        filesDialog.show()
    }

    private fun disableMod(file: File?) {
        val fileName = file!!.name
        val fileParent = file.parent
        val newFile = File(fileParent, "$fileName.disabled")
        renameFile(file, newFile)
    }

    private fun enableMod(file: File?) {
        val fileName = file!!.name
        val fileParent = file.parent
        var newFileName = fileName.substring(0, fileName.lastIndexOf(DISABLE_JAR_FILE_SUFFIX))
        if (!fileName.endsWith(JAR_FILE_SUFFIX)) newFileName += JAR_FILE_SUFFIX //如果没有.jar结尾，那么默认加上.jar后缀


        val newFile = File(fileParent, newFileName)
        renameFile(file, newFile)
    }

    private fun getFileSuffix(file: File): String {
        val name = file.name
        if (name.endsWith(DISABLE_JAR_FILE_SUFFIX)) {
            return DISABLE_JAR_FILE_SUFFIX
        } else if (name.endsWith(JAR_FILE_SUFFIX)) {
            return JAR_FILE_SUFFIX
        } else {
            val dotIndex = file.name.lastIndexOf('.')
            return if (dotIndex == -1) "" else file.name.substring(dotIndex)
        }
    }

    private fun parseBundle() {
        val bundle = arguments ?: return
        mRootPath = bundle.getString(BUNDLE_ROOT_PATH, mRootPath)
    }

    private fun bindViews(view: View) {
        mModsLayout = view.findViewById(R.id.mods_layout)
        mOperateLayout = view.findViewById(R.id.operate_layout)

        mOperateView = view.findViewById(R.id.operate_view)

        mReturnButton = view.findViewById(R.id.zh_return_button)
        mAddModButton = view.findViewById(R.id.zh_add_file_button)
        mPasteButton = view.findViewById(R.id.zh_paste_button)
        mDownloadButton = view.findViewById(R.id.zh_create_folder_button)
        mSearchSummonButton = view.findViewById(R.id.zh_search_button)
        mRefreshButton = view.findViewById(R.id.zh_refresh_button)
        mNothingTip = view.findViewById(R.id.zh_mods_nothing)

        mAddModButton!!.setContentDescription(getString(R.string.zh_profile_mods_add_mod))
        mDownloadButton!!.setContentDescription(getString(R.string.zh_profile_mods_download_mod))
        mDownloadButton!!.setImageDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.ic_download
            )
        )

        mMultiSelectCheck = view.findViewById(R.id.zh_mods_multi_select_files)
        mSelectAllCheck = view.findViewById(R.id.zh_mods_select_all)
        mFileRecyclerView = view.findViewById(R.id.zh_mods)

        mSownloadOptiFine = view.findViewById(R.id.zh_mods_download_optifine)

        mSearchView = SearchView(view, view.findViewById(R.id.zh_search_view))
        mSearchView!!.setSearchListener(object : SearchView.SearchListener {
            override fun onSearch(string: String?, caseSensitive: Boolean): Int {
                return mFileRecyclerView!!.searchFiles(string, caseSensitive)
            }
        })
        mSearchView!!.setShowSearchResultsListener(object : SearchView.ShowSearchResultsListener {
            override fun onSearch(show: Boolean) {
                mFileRecyclerView!!.setShowSearchResultsOnly(show)
            }
        })
        mFileRecyclerView!!.setFileIcon(FileIcon.MOD)

        mPasteButton!!.setVisibility(if (PasteFile.getInstance().pasteType != null) View.VISIBLE else View.GONE)

        ZHTools.setTooltipText(mReturnButton, mReturnButton!!.contentDescription)
        ZHTools.setTooltipText(mAddModButton, mAddModButton!!.contentDescription)
        ZHTools.setTooltipText(mPasteButton, mPasteButton!!.contentDescription)
        ZHTools.setTooltipText(mDownloadButton, mDownloadButton!!.contentDescription)
        ZHTools.setTooltipText(mSearchSummonButton, mSearchSummonButton!!.contentDescription)
        ZHTools.setTooltipText(mRefreshButton, mRefreshButton!!.contentDescription)
    }

    override fun slideIn(): Array<YoYoString?> {
        val yoYos: MutableList<YoYoString?> = ArrayList()
        yoYos.add(setViewAnim(mModsLayout!!, Techniques.BounceInDown))
        yoYos.add(setViewAnim(mOperateLayout!!, Techniques.BounceInLeft))
        yoYos.add(setViewAnim(mOperateView!!, Techniques.FadeInLeft))
        val array = yoYos.toTypedArray()
        super.yoYos = array
        return array
    }

    override fun slideOut(): Array<YoYoString?> {
        val yoYos: MutableList<YoYoString?> = ArrayList()
        yoYos.add(setViewAnim(mModsLayout!!, Techniques.FadeOutUp))
        yoYos.add(setViewAnim(mOperateLayout!!, Techniques.FadeOutRight))
        val array = yoYos.toTypedArray()
        super.yoYos = array
        return array
    }
}

