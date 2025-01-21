package com.movtery.zalithlauncher.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.recyclerview.widget.LinearLayoutManager
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.DialogModDependenciesBinding
import com.movtery.zalithlauncher.feature.download.ModDependenciesAdapter
import com.movtery.zalithlauncher.feature.download.item.DependenciesInfoItem
import com.movtery.zalithlauncher.feature.download.item.InfoItem
import com.movtery.zalithlauncher.feature.log.Logging

class ModDependenciesDialog(
    context: Context,
    private val infoItem: InfoItem,
    private val dependenciesData: List<DependenciesInfoItem>,
    private val install: () -> Unit
) :
    FullScreenDialog(context) {
    private val binding = DialogModDependenciesBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(false)
        setContentView(binding.root)

        window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
            )
            setGravity(Gravity.CENTER)

            //隐藏状态栏
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        runCatching {
            init()
        }.getOrElse {
            dismiss()
            Logging.e("ModDependenciesDialog", "Initialization failed, dismiss attempted.", it)
        }
    }

    private fun init() {
        val data = dependenciesData.toMutableList().apply { this.sort() }

        binding.apply {
            titleView.text = context.getString(R.string.download_install_dependencies, infoItem.title)
            downloadButton.text = context.getString(R.string.download_install, infoItem.title)

            recyclerView.apply {
                layoutManager = LinearLayoutManager(context)
                layoutAnimation = LayoutAnimationController(AnimationUtils.loadAnimation(context, R.anim.fade_downwards))
                adapter = ModDependenciesAdapter(infoItem, data).apply {
                    setOnItemCLickListener { dismiss() }
                }
            }

            closeButton.setOnClickListener { dismiss() }
            downloadButton.setOnClickListener {
                install()
                dismiss()
            }
        }
    }
}
