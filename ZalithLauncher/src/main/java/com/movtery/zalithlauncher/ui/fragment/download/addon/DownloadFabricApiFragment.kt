package com.movtery.zalithlauncher.ui.fragment.download.addon

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.version.install.Addon

class DownloadFabricApiFragment: DownloadFabricLikeApiModFragment(
    Addon.FABRIC_API,
    "P7dR8mSH",
    "https://modrinth.com/mod/fabric-api",
    "https://www.mcmod.cn/class/3124.html",
    R.drawable.ic_fabric
) {
    companion object {
        const val TAG: String = "DownloadFabricApiFragment"
    }
}