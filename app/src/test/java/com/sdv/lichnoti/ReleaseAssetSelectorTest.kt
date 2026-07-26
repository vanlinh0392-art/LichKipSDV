package com.sdv.lichnoti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetSelectorTest {
    @Test
    fun select_prefersApkMatchingReleaseTag() {
        val assets = listOf(
            GithubReleaseAsset(
                "notes.txt",
                "https://github.com/vanlinh0392-art/LichKipSDV/releases/download/v4.61/notes.txt"
            ),
            GithubReleaseAsset(
                "other.apk",
                "https://github.com/vanlinh0392-art/LichKipSDV/releases/download/v4.61/other.apk"
            ),
            GithubReleaseAsset(
                "LichNoti_v4.61.apk",
                "https://github.com/vanlinh0392-art/LichKipSDV/releases/download/v4.61/LichNoti_v4.61.apk"
            )
        )

        val selected = ReleaseAssetSelector.select("v4.61", assets)

        assertEquals("LichNoti_v4.61.apk", selected?.name)
    }

    @Test
    fun select_rejectsNonApkAndUntrustedUrls() {
        val assets = listOf(
            GithubReleaseAsset(
                "LichNoti_v4.61.apk",
                "https://example.com/LichNoti_v4.61.apk"
            ),
            GithubReleaseAsset(
                "notes.txt",
                "https://github.com/vanlinh0392-art/LichKipSDV/releases/download/v4.61/notes.txt"
            )
        )

        assertNull(ReleaseAssetSelector.select("v4.61", assets))
    }

    @Test
    fun select_acceptsOnlySingleTrustedApkAsFallback() {
        val asset = GithubReleaseAsset(
            "internal-build.apk",
            "https://github.com/vanlinh0392-art/LichKipSDV/releases/download/v4.61/internal-build.apk"
        )

        assertEquals(asset, ReleaseAssetSelector.select("v4.61", listOf(asset)))
    }
}
