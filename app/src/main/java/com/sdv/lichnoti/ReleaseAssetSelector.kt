package com.sdv.lichnoti

data class GithubReleaseAsset(val name: String, val downloadUrl: String)

object ReleaseAssetSelector {
    fun select(tagName: String, assets: List<GithubReleaseAsset>): GithubReleaseAsset? {
        val apkAssets = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) &&
                it.downloadUrl.startsWith("https://github.com/vanlinh0392-art/LichKipSDV/")
        }
        if (apkAssets.isEmpty()) return null

        val version = tagName.removePrefix("v").removePrefix("V").trim()
        val preferredNames = setOf(
            "LichNoti_v$version.apk",
            "LichKipSDV_v$version.apk",
            "LichNoti_$tagName.apk"
        )
        return apkAssets.firstOrNull { asset ->
            preferredNames.any { it.equals(asset.name, ignoreCase = true) }
        } ?: apkAssets.singleOrNull()
    }
}
