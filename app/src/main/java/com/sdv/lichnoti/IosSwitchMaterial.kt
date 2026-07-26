package com.sdv.lichnoti

import android.content.Context
import android.util.AttributeSet
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
    }
}
