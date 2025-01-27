package com.movtery.zalithlauncher.ui.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.ItemLocalRendererViewBinding
import com.movtery.zalithlauncher.plugins.renderer.LocalRendererPlugin
import com.movtery.zalithlauncher.plugins.renderer.RendererPluginManager
import org.apache.commons.io.FileUtils

class LocalRendererPluginDialog(
    private val context: Context
) : AbstractSelectDialog(context) {
    override fun initDialog(recyclerView: RecyclerView) {
        setTitleText(R.string.setting_renderer_local_manage)

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = LocalRendererPluginAdapter()
    }

    private class LocalRendererPluginAdapter : RecyclerView.Adapter<LocalRendererPluginAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemLocalRendererViewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(RendererPluginManager.getAllLocalRendererList()[position], position)
        }

        override fun getItemCount(): Int = RendererPluginManager.getAllLocalRendererList().size

        inner class ViewHolder(
            private val binding: ItemLocalRendererViewBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            @SuppressLint("NotifyDataSetChanged")
            fun bind(renderer: LocalRendererPlugin, position: Int) {
                binding.apply {
                    rendererName.text = renderer.rendererName
                    rendererId.text = renderer.rendererId

                    delete.setOnClickListener {
                        TipDialog.Builder(binding.root.context)
                            .setTitle(R.string.generic_warning)
                            .setMessage(R.string.setting_renderer_local_delete)
                            .setWarning()
                            .setConfirmClickListener {
                                FileUtils.deleteQuietly(renderer.folderPath)
                                RendererPluginManager.markLocalRendererDeleted(position)
                                notifyItemChanged(position)
                            }.showDialog()
                    }

                    if (renderer.isDeleted) {
                        delete.isEnabled = false
                        delete.alpha = 0.5f
                    }
                }
            }
        }
    }
}