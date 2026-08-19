package com.sdv.lichnoti

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Keeps the compact iOS visual ratio without letting SwitchCompat expand the track to
 * twice the thumb width. The view itself still has a 48dp minimum touch target via style.
 */
class IosSwitchMaterial @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.switchStyle
) : SwitchMaterial(context, attrs, defStyleAttr) {

    init {
        setEnforceSwitchWidth(false)
        setUseMaterialThemeColors(false)
        setThumbResource(R.drawable.ios_switch_thumb)
        setTrackResource(R.drawable.ios_switch_track)
        trackTintList = ContextCompat.getColorStateList(context, R.color.ios_switch_track_tint)
        splitTrack = false
        showText = false
        switchMinWidth = (46 * resources.displayMetrics.density).toInt()
    }
}
