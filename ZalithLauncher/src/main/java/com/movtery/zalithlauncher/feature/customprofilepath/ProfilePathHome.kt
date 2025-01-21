package com.movtery.zalithlauncher.feature.customprofilepath

class ProfilePathHome {
    companion object {
        @JvmStatic
        fun getGameHome(): String = "${ProfilePathManager.getCurrentPath()}/.minecraft"

        @JvmStatic
        fun getVersionsHome(): String = "${getGameHome()}/versions"

        @JvmStatic
        fun getLibrariesHome(): String = "${getGameHome()}/libraries"

        @JvmStatic
        fun getAssetsHome(): String = "${getGameHome()}/assets"

        @JvmStatic
        fun getResourcesHome(): String = "${getGameHome()}/resources"
    }
}
