package com.movtery.zalithlauncher.ui.subassembly.customprofilepath

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ItemProfilePathBinding
import com.movtery.zalithlauncher.databinding.ViewPathManagerBinding
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathManager.Companion.save
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathManager.Companion.setCurrentPathId
import com.movtery.zalithlauncher.setting.AllSettings.Companion.launcherProfile
import com.movtery.zalithlauncher.ui.dialog.EditTextDialog
import com.movtery.zalithlauncher.ui.dialog.TipDialog
import com.movtery.zalithlauncher.ui.fragment.FilesFragment
import com.movtery.zalithlauncher.ui.fragment.FragmentWithAnim
import com.movtery.zalithlauncher.ui.fragment.VersionsListFragment
import com.movtery.zalithlauncher.utils.StoragePermissionsUtils
import com.movtery.zalithlauncher.utils.ZHTools

class ProfilePathAdapter(
    private val fragment: FragmentWithAnim,
    private val view: RecyclerView
) :
    RecyclerView.Adapter<ProfilePathAdapter.ViewHolder>() {
    private val mData: MutableList<ProfileItem> = ArrayList()
    private val radioButtonList: MutableList<RadioButton> = mutableListOf()
    //如果没有存储权限，那么旧设置为默认路径
    private var currentId: String? = if (StoragePermissionsUtils.checkPermissions()) launcherProfile.getValue() else "default"
    private val managerPopupWindow: PopupWindow = PopupWindow().apply {
        isFocusable = true
        isOutsideTouchable = true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemProfilePathBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setView(mData[position], position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        radioButtonList.remove(holder.binding.radioButton)
    }

    override fun getItemCount(): Int = mData.size

    fun updateData(data: MutableList<ProfileItem>) {
        this.mData.clear()
        this.mData.addAll(data)
        radioButtonList.apply {
            forEach { radioButton -> radioButton.isChecked = false }
            clear()
        }
        refresh()
    }

    fun closePopupWindow() {
        managerPopupWindow.dismiss()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refresh() {
        save(this.mData)

        notifyDataSetChanged()
        view.scheduleLayoutAnimation()
    }

    private fun setPathId(id: String) {
        currentId = id
        setCurrentPathId(id)
        radioButtonList.forEach { radioButton -> radioButton.isChecked = radioButton.tag.toString() == id }
    }

    inner class ViewHolder(val binding: ItemProfilePathBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setView(profileItem: ProfileItem, position: Int) {
            binding.apply {
                radioButtonList.add(
                    radioButton.apply {
                        tag = profileItem.id
                        isChecked = currentId == profileItem.id
                    }
                )
                title.text = profileItem.title
                path.text = profileItem.path
                path.isSelected = true

                val onClickListener = View.OnClickListener {
                    if (VersionsListFragment.checkLastRefreshTime()) return@OnClickListener
                    if (currentId != profileItem.id) {
                        StoragePermissionsUtils.checkPermissions(fragment.requireActivity(), R.string.profiles_path_title) {
                            setPathId(profileItem.id)
                        }
                    }
                }
                root.setOnClickListener(onClickListener)
                radioButton.setOnClickListener(onClickListener)

                operate.setOnClickListener {
                    showPopupWindow(root, profileItem.id == "default", profileItem, position)
                }
            }
        }

        private fun showPopupWindow(
            anchorView: View,
            isDefault: Boolean,
            profileItem: ProfileItem,
            itemIndex: Int
        ) {
            val context = anchorView.context

            val viewBinding = ViewPathManagerBinding.inflate(LayoutInflater.from(context)).apply {
                val onClickListener = View.OnClickListener { v ->
                    when (v) {
                        gotoView -> {
                            val bundle = Bundle()
                            bundle.putString(FilesFragment.BUNDLE_LOCK_PATH, Environment.getExternalStorageDirectory().absolutePath)
                            bundle.putString(FilesFragment.BUNDLE_LIST_PATH, profileItem.path)
                            ZHTools.swapFragmentWithAnim(
                                fragment,
                                FilesFragment::class.java, FilesFragment.TAG, bundle
                            )
                        }
                        rename -> {
                            EditTextDialog.Builder(context)
                                .setTitle(R.string.generic_rename)
                                .setEditText(profileItem.title)
                                .setAsRequired()
                                .setConfirmListener { editBox, _ ->
                                    val string = editBox.text.toString()

                                    mData[itemIndex].title = string
                                    refresh()
                                    true
                                }.showDialog()
                        }
                        delete -> {
                            TipDialog.Builder(context)
                                .setTitle(context.getString(R.string.profiles_path_delete_title))
                                .setMessage(R.string.profiles_path_delete_message)
                                .setCancelable(false)
                                .setConfirmClickListener {
                                    if (currentId == profileItem.id) {
                                        //如果删除的是当前选中的路径，那么将自动选择为默认路径
                                        setPathId("default")
                                    }
                                    mData.removeAt(itemIndex)
                                    refresh()
                                }.showDialog()
                        }
                        else -> {}
                    }
                    managerPopupWindow.dismiss()
                }
                gotoView.setOnClickListener(onClickListener)
                rename.setOnClickListener(onClickListener)
                delete.setOnClickListener(onClickListener)
                if (isDefault) {
                    renameLayout.visibility = View.GONE
                    deleteLayout.visibility = View.GONE
                }
            }
            managerPopupWindow.apply {
                viewBinding.root.measure(0, 0)
                this.contentView = viewBinding.root
                this.width = viewBinding.root.measuredWidth
                this.height = viewBinding.root.measuredHeight
                showAsDropDown(anchorView, anchorView.measuredWidth, 0)
            }
        }
    }
}
